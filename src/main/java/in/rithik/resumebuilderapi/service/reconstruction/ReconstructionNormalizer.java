package in.rithik.resumebuilderapi.service.reconstruction;

import in.rithik.resumebuilderapi.document.Resume;
import in.rithik.resumebuilderapi.document.Resume.Skill;
import in.rithik.resumebuilderapi.document.Resume.WorkExperience;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ReconstructionNormalizer {

    private static final Map<String, String> MONTH_MAP = new HashMap<>();
    static {
        MONTH_MAP.put("january", "Jan"); MONTH_MAP.put("february", "Feb");
        MONTH_MAP.put("march", "Mar"); MONTH_MAP.put("april", "Apr");
        MONTH_MAP.put("may", "May"); MONTH_MAP.put("june", "Jun");
        MONTH_MAP.put("july", "Jul"); MONTH_MAP.put("august", "Aug");
        MONTH_MAP.put("september", "Sep"); MONTH_MAP.put("october", "Oct");
        MONTH_MAP.put("november", "Nov"); MONTH_MAP.put("december", "Dec");
        MONTH_MAP.put("jan", "Jan"); MONTH_MAP.put("feb", "Feb");
        MONTH_MAP.put("mar", "Mar"); MONTH_MAP.put("apr", "Apr");
        MONTH_MAP.put("jun", "Jun"); MONTH_MAP.put("jul", "Jul");
        MONTH_MAP.put("aug", "Aug"); MONTH_MAP.put("sep", "Sep");
        MONTH_MAP.put("oct", "Oct"); MONTH_MAP.put("nov", "Nov");
        MONTH_MAP.put("dec", "Dec");
    }

    public List<String> normalize(Resume resume) {
        List<String> steps = new ArrayList<>();

        // 1. Normalize Dates in WorkExperience
        if (resume.getWorkExperience() != null) {
            for (WorkExperience exp : resume.getWorkExperience()) {
                String origStart = exp.getStartDate();
                String normStart = normalizeDate(origStart);
                if (normStart != null && !normStart.equals(origStart)) {
                    exp.setStartDate(normStart);
                    steps.add("Normalized work experience start date from '" + origStart + "' to '" + normStart + "'");
                }

                String origEnd = exp.getEndDate();
                String normEnd = normalizeDate(origEnd);
                if (normEnd != null && !normEnd.equals(origEnd)) {
                    exp.setEndDate(normEnd);
                    steps.add("Normalized work experience end date from '" + origEnd + "' to '" + normEnd + "'");
                }
            }
        }

        // 2. Remove Duplicate Skills
        if (resume.getSkills() != null) {
            Set<String> seen = new HashSet<>();
            List<Skill> uniqueSkills = new ArrayList<>();
            for (Skill s : resume.getSkills()) {
                if (s.getName() != null) {
                    String clean = s.getName().trim().toLowerCase();
                    if (!clean.isEmpty() && seen.add(clean)) {
                        uniqueSkills.add(s);
                    } else {
                        steps.add("Removed duplicate skill: " + s.getName());
                    }
                }
            }
            if (uniqueSkills.size() != resume.getSkills().size()) {
                resume.setSkills(uniqueSkills);
            }
        }

        return steps;
    }

    private String normalizeDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) return rawDate;
        String clean = rawDate.trim().replaceAll("\\s+", " ").toLowerCase();

        if (clean.equals("present") || clean.equals("current") || clean.equals("now")) {
            return "Present";
        }

        // Match "Month Year" e.g., "April 2021" or "Apr 2021"
        Pattern monthYearPattern = Pattern.compile("([a-z]+)\\s+(\\d{4})");
        Matcher myMatcher = monthYearPattern.matcher(clean);
        if (myMatcher.find()) {
            String month = myMatcher.group(1);
            String year = myMatcher.group(2);
            String stdMonth = MONTH_MAP.get(month);
            if (stdMonth != null) {
                return stdMonth + " " + year;
            }
        }

        // Match "MM/YYYY" e.g., "04/2021"
        Pattern slashPattern = Pattern.compile("(\\d{1,2})/(\\d{4})");
        Matcher slashMatcher = slashPattern.matcher(clean);
        if (slashMatcher.find()) {
            try {
                int monthNum = Integer.parseInt(slashMatcher.group(1));
                String year = slashMatcher.group(2);
                String[] months = {"", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
                if (monthNum >= 1 && monthNum <= 12) {
                    return months[monthNum] + " " + year;
                }
            } catch (Exception e) {}
        }

        // Match pure year "2021"
        if (clean.matches("\\d{4}")) {
            return rawDate.trim();
        }

        return rawDate;
    }
}
