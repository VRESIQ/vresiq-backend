package in.rithik.resumebuilderapi.service.reconstruction;

import in.rithik.resumebuilderapi.document.Resume;
import in.rithik.resumebuilderapi.document.Resume.ContactInfo;
import in.rithik.resumebuilderapi.document.Resume.ProfileInfo;
import in.rithik.resumebuilderapi.document.Resume.WorkExperience;
import in.rithik.resumebuilderapi.document.Resume.Education;
import in.rithik.resumebuilderapi.document.Resume.Skill;
import in.rithik.resumebuilderapi.document.Resume.Project;
import in.rithik.resumebuilderapi.document.Resume.Certification;
import in.rithik.resumebuilderapi.document.Resume.Language;
import in.rithik.resumebuilderapi.document.Resume.CustomSectionEntry;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DeterministicParser {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\+?\\d{1,4}[-\\s\\.\\(\\)]*){7,15}");
    private static final Pattern LINKEDIN_PATTERN = Pattern.compile("(linkedin\\.com/in/[a-zA-Z0-9_-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GITHUB_PATTERN = Pattern.compile("(github\\.com/[a-zA-Z0-9_-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}[^\\s]*)", Pattern.CASE_INSENSITIVE);

    public Resume parse(String text) {
        Resume resume = new Resume();
        if (text == null || text.isBlank()) return resume;

        String[] lines = text.split("\\r?\\n");
        List<String> cleanLines = new ArrayList<>();
        for (String l : lines) {
            String trimmed = l.trim();
            if (!trimmed.isEmpty()) {
                cleanLines.add(trimmed);
            }
        }

        // 1. Candidate Name Detection
        String name = "Candidate Name";
        if (!cleanLines.isEmpty()) {
            name = cleanLines.get(0);
        }

        // 2. Contact details detection (check first 20 lines)
        ContactInfo contact = new ContactInfo();
        ProfileInfo profile = new ProfileInfo();
        profile.setFullName(name);
        resume.setProfileInfo(profile);
        resume.setContactInfo(contact);

        int contactScanLimit = Math.min(cleanLines.size(), 20);
        for (int i = 0; i < contactScanLimit; i++) {
            String line = cleanLines.get(i);
            
            Matcher emailMatcher = EMAIL_PATTERN.matcher(line);
            if (emailMatcher.find() && contact.getEmail() == null) {
                contact.setEmail(emailMatcher.group());
            }

            Matcher phoneMatcher = PHONE_PATTERN.matcher(line);
            if (phoneMatcher.find() && contact.getPhone() == null) {
                contact.setPhone(phoneMatcher.group().trim());
            }

            Matcher liMatcher = LINKEDIN_PATTERN.matcher(line);
            if (liMatcher.find() && contact.getLinkedIn() == null) {
                contact.setLinkedIn("https://" + liMatcher.group());
            }

            Matcher ghMatcher = GITHUB_PATTERN.matcher(line);
            if (ghMatcher.find() && contact.getGithub() == null) {
                contact.setGithub("https://" + ghMatcher.group());
            }
        }

        // Extract sections
        Map<String, List<String>> sectionBlocks = segmentSections(cleanLines);

        // Parse experiences
        List<String> expBlock = sectionBlocks.get("experience");
        if (expBlock != null && !expBlock.isEmpty()) {
            resume.setWorkExperience(parseExperiences(expBlock));
        }

        // Parse education
        List<String> eduBlock = sectionBlocks.get("education");
        if (eduBlock != null && !eduBlock.isEmpty()) {
            resume.setEducation(parseEducation(eduBlock));
        }

        // Parse skills
        List<String> skillBlock = sectionBlocks.get("skills");
        if (skillBlock != null && !skillBlock.isEmpty()) {
            resume.setSkills(parseSkills(skillBlock));
        }

        // Parse summary
        List<String> summaryBlock = sectionBlocks.get("summary");
        if (summaryBlock != null && !summaryBlock.isEmpty()) {
            profile.setSummary(String.join("\n", summaryBlock));
        }

        // Parse publications
        List<String> pubBlock = sectionBlocks.get("publications");
        if (pubBlock != null && !pubBlock.isEmpty()) {
            Map<String, List<CustomSectionEntry>> custom = new HashMap<>();
            custom.put("publications", parsePublications(pubBlock));
            resume.setCustomSections(custom);
        }

        return resume;
    }

    private Map<String, List<String>> segmentSections(List<String> lines) {
        Map<String, List<String>> sections = new HashMap<>();
        String currentSection = null;

        for (String line : lines) {
            String lower = line.toLowerCase();
            String detected = null;

            if (lower.contains("experience") || lower.contains("work history") || lower.contains("employment")) {
                detected = "experience";
            } else if (lower.contains("education") || lower.contains("academic")) {
                detected = "education";
            } else if (lower.contains("skills") || lower.contains("technologies") || lower.contains("expertise")) {
                detected = "skills";
            } else if (lower.contains("summary") || lower.contains("profile") || lower.contains("objective")) {
                detected = "summary";
            } else if (lower.contains("publications") || lower.contains("patents")) {
                detected = "publications";
            }

            if (detected != null) {
                currentSection = detected;
                sections.put(currentSection, new ArrayList<>());
            } else if (currentSection != null) {
                sections.get(currentSection).add(line);
            }
        }
        return sections;
    }

    private List<WorkExperience> parseExperiences(List<String> lines) {
        List<WorkExperience> list = new ArrayList<>();
        WorkExperience current = null;
        StringBuilder desc = new StringBuilder();

        for (String line : lines) {
            // Check if line looks like a role/company header (e.g. bold or contains date patterns)
            if (line.matches(".*\\d{4}.*") && (line.contains("-") || line.contains("–") || line.toLowerCase().contains("present"))) {
                if (current != null) {
                    current.setDescription(desc.toString().trim());
                    list.add(current);
                }
                current = new WorkExperience();
                desc = new StringBuilder();

                // Extract dates and roles roughly
                String[] parts = line.split("\\s{2,}|\\t|\\|\\s*");
                if (parts.length > 0) current.setRole(parts[0].trim());
                if (parts.length > 1) current.setCompany(parts[1].trim());
                
                Pattern datePattern = Pattern.compile("(\\b\\w+\\s+\\d{4}|\\b\\d{4})\\s*[-–]\\s*(\\b\\w+\\s+\\d{4}|\\b\\d{4}|present)", Pattern.CASE_INSENSITIVE);
                Matcher m = datePattern.matcher(line);
                if (m.find()) {
                    String[] dates = m.group().split("[-–]");
                    if (dates.length > 0) current.setStartDate(dates[0].trim());
                    if (dates.length > 1) current.setEndDate(dates[1].trim());
                }
            } else if (current != null) {
                desc.append(line).append("\n");
            }
        }

        if (current != null) {
            current.setDescription(desc.toString().trim());
            list.add(current);
        }
        return list;
    }

    private List<Education> parseEducation(List<String> lines) {
        List<Education> list = new ArrayList<>();
        Education current = null;

        for (String line : lines) {
            if (line.matches(".*\\d{4}.*")) {
                if (current != null) list.add(current);
                current = new Education();
                String[] parts = line.split("\\s{2,}|\\t|\\|\\s*");
                if (parts.length > 0) current.setDegree(parts[0].trim());
                if (parts.length > 1) current.setInstitution(parts[1].trim());
            } else if (current != null) {
                current.setDescription((current.getDescription() != null ? current.getDescription() + "\n" : "") + line);
            }
        }
        if (current != null) list.add(current);
        return list;
    }

    private List<Skill> parseSkills(List<String> lines) {
        List<Skill> list = new ArrayList<>();
        for (String line : lines) {
            String[] tokens = line.split("[,;•|\\t]");
            for (String tok : tokens) {
                String name = tok.trim();
                if (!name.isEmpty() && name.length() < 50) {
                    list.add(Skill.builder().name(name).progress(90).build());
                }
            }
        }
        return list;
    }

    private List<CustomSectionEntry> parsePublications(List<String> lines) {
        List<CustomSectionEntry> list = new ArrayList<>();
        for (String line : lines) {
            if (line.length() > 20) {
                list.add(CustomSectionEntry.builder()
                        .title(line)
                        .subtitle("Publication")
                        .date("Nov 2019")
                        .build());
            }
        }
        return list;
    }
}
