package in.rithik.resumebuilderapi.service.reconstruction;

import in.rithik.resumebuilderapi.document.Resume;
import in.rithik.resumebuilderapi.document.Resume.Certification;
import in.rithik.resumebuilderapi.document.Resume.ContactInfo;
import in.rithik.resumebuilderapi.document.Resume.CustomSectionEntry;
import in.rithik.resumebuilderapi.document.Resume.Education;
import in.rithik.resumebuilderapi.document.Resume.Language;
import in.rithik.resumebuilderapi.document.Resume.ProfileInfo;
import in.rithik.resumebuilderapi.document.Resume.Project;
import in.rithik.resumebuilderapi.document.Resume.Skill;
import in.rithik.resumebuilderapi.document.Resume.WorkExperience;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative parser: source wording is retained and uncertain material is surfaced for review. */
@Component
public class DeterministicParser {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\w)(?:\\+?\\d[\\d\\s()./-]{6,}\\d)(?!\\w)");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>]+|(?:www\\.)?[a-zA-Z0-9.-]+\\.(?:com|org|net|ca|io|dev|edu|gov|co\\.[a-z]{2})/[^\\s<>]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_RANGE = Pattern.compile("(?i)((?:(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+)?(?:19|20)\\d{2}|present|current|now)\\s*(?:-|–|—|to)\\s*((?:(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+)?(?:19|20)\\d{2}|present|current|now)");

    private static final Map<String, String> SECTION_ALIASES = new LinkedHashMap<>();
    static {
        addAliases("summary", "summary", "professional summary", "profile", "professional profile", "objective", "career objective", "about me");
        addAliases("experience", "experience", "work experience", "professional experience", "employment", "employment history", "work history", "career history");
        addAliases("education", "education", "academic background", "academic history", "qualifications");
        addAliases("skills", "skills", "technical skills", "core skills", "core competencies", "technologies", "technology stack", "areas of expertise");
        addAliases("projects", "projects", "selected projects", "personal projects", "academic projects", "key projects");
        addAliases("certifications", "certifications", "certificates", "licenses and certifications", "licences and certifications", "licenses", "licences");
        addAliases("publications", "publications", "selected publications", "research publications", "papers", "patents");
        addAliases("languages", "languages", "language skills");
        addAliases("interests", "interests", "interests and activities", "activities", "hobbies");
        addAliases("volunteering", "volunteering", "volunteer experience", "community involvement");
        addAliases("awards", "awards", "honors and awards", "honours and awards", "honors", "honours");
    }

    private static void addAliases(String type, String... aliases) {
        Arrays.stream(aliases).forEach(alias -> SECTION_ALIASES.put(alias, type));
    }

    public Resume parse(String text) {
        return parseWithDiagnostics(text).getResume();
    }

    public ParseResult parseWithDiagnostics(String text) {
        Resume resume = new Resume();
        List<String> warnings = new ArrayList<>();
        List<String> unclassified = new ArrayList<>();
        List<String> cleanLines = nonEmptyLines(text);
        if (cleanLines.isEmpty()) {
            warnings.add("No extractable text was found. The document may be image-only or scanned; OCR is not configured.");
            return new ParseResult(resume, unclassified, warnings);
        }

        Map<String, List<String>> sections = new LinkedHashMap<>();
        List<String> preamble = new ArrayList<>();
        String current = null;
        Set<String> seenHeadings = new LinkedHashSet<>();
        for (String line : cleanLines) {
            String type = sectionType(line);
            if (type != null) {
                if (!seenHeadings.add(type)) warnings.add("Repeated '" + type + "' heading detected; its content was combined for review.");
                current = type;
                sections.computeIfAbsent(type, ignored -> new ArrayList<>());
            } else if (current == null) {
                preamble.add(line);
            } else if (!URL_PATTERN.matcher(line).matches()) {
                // Standalone links are captured in ContactInfo/Links; don't also misclassify them
                // as language, skill, publication, or experience text in the current section.
                sections.get(current).add(line);
            }
        }

        ProfileInfo profile = new ProfileInfo();
        String detectedName = detectName(preamble);
        if (detectedName != null) profile.setFullName(detectedName);
        if (sections.containsKey("summary")) profile.setSummary(String.join("\n", sections.get("summary")));
        resume.setProfileInfo(profile);

        ContactInfo contact = new ContactInfo();
        Set<String> allUrls = new LinkedHashSet<>();
        Set<String> duplicateUrls = new LinkedHashSet<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        String allText = String.join("\n", cleanLines);
        Matcher email = EMAIL_PATTERN.matcher(allText);
        if (email.find()) contact.setEmail(email.group());
        for (String line : cleanLines) {
            if (line.replaceAll("\\D", "").length() < 9) continue;
            Matcher phone = PHONE_PATTERN.matcher(line);
            if (phone.find()) {
                contact.setPhone(phone.group().trim());
                break;
            }
        }
        Matcher url = URL_PATTERN.matcher(allText);
        while (url.find()) {
            String found = trimUrl(url.group());
            String normalized = found.toLowerCase(Locale.ROOT);
            if (!seenUrls.add(normalized)) duplicateUrls.add(found);
            allUrls.add(found);
            if (contact.getLinkedIn() == null && normalized.contains("linkedin.com/")) contact.setLinkedIn(addScheme(found));
            else if (contact.getGithub() == null && normalized.contains("github.com/")) contact.setGithub(addScheme(found));
            else if (contact.getWebsite() == null && !normalized.contains("linkedin.com/") && !normalized.contains("github.com/")) contact.setWebsite(addScheme(found));
        }
        resume.setContactInfo(contact);
        if (!duplicateUrls.isEmpty()) warnings.add("Repeated URL(s) detected and flagged: " + String.join(", ", duplicateUrls));

        List<String> preambleContent = new ArrayList<>(preamble);
        if (detectedName != null) preambleContent.remove(detectedName);
        for (String line : preambleContent) {
            if (!EMAIL_PATTERN.matcher(line).find() && !PHONE_PATTERN.matcher(line).find() && !URL_PATTERN.matcher(line).find()) {
                unclassified.add("Unclassified header content: " + line);
            }
        }

        if (sections.containsKey("experience")) {
            ParsedExperiences parsed = parseExperiences(sections.get("experience"));
            resume.setWorkExperience(parsed.items);
            unclassified.addAll(parsed.unclassified);
        }
        if (sections.containsKey("education")) {
            ParsedEducation parsed = parseEducation(sections.get("education"));
            resume.setEducation(parsed.items);
            unclassified.addAll(parsed.unclassified);
        }
        if (sections.containsKey("skills")) {
            List<Skill> skills = parseSkills(sections.get("skills"));
            resume.setSkills(skills);
            reportDuplicateSkills(skills, warnings);
        }
        if (sections.containsKey("projects")) resume.setProjects(parseProjects(sections.get("projects")));
        if (sections.containsKey("certifications")) resume.setCertifications(parseCertifications(sections.get("certifications")));
        if (sections.containsKey("languages")) resume.setLanguages(parseLanguages(sections.get("languages")));
        if (sections.containsKey("interests")) resume.setInterests(new ArrayList<>(sections.get("interests")));

        Map<String, List<CustomSectionEntry>> custom = new LinkedHashMap<>();
        for (String key : List.of("publications", "volunteering", "awards")) {
            if (sections.containsKey(key)) custom.put(key, parseCustomEntries(key, sections.get(key)));
        }
        // Preserve links that aren't already represented as a structured contact field.
        List<CustomSectionEntry> otherLinks = new ArrayList<>();
        for (String link : allUrls) {
            String lower = link.toLowerCase(Locale.ROOT);
            String website = contact.getWebsite() == null ? "" : contact.getWebsite().toString().toLowerCase(Locale.ROOT);
            if (!lower.contains("linkedin.com/") && !lower.contains("github.com/") && !addScheme(link).equalsIgnoreCase(website)) {
                otherLinks.add(CustomSectionEntry.builder().title(link).url(addScheme(link)).build());
            }
        }
        if (!otherLinks.isEmpty()) custom.put("links", otherLinks);
        if (!custom.isEmpty()) resume.setCustomSections(custom);

        Set<String> recognized = new LinkedHashSet<>(SECTION_ALIASES.values());
        for (Map.Entry<String, List<String>> section : sections.entrySet()) {
            if (!recognized.contains(section.getKey())) {
                unclassified.add("Unclassified section [" + section.getKey() + "]: " + String.join(" | ", section.getValue()));
            }
        }

        return new ParseResult(resume, unclassified, warnings);
    }

    private List<String> nonEmptyLines(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) lines.add(trimmed);
        }
        return lines;
    }

    private String sectionType(String line) {
        String key = line.trim().replaceAll("^[#*\\s]+|[#*:\\s]+$", "").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return SECTION_ALIASES.get(key);
    }

    private String detectName(List<String> preamble) {
        for (String line : preamble) {
            String candidate = line.replaceAll("\\s+", " ").trim();
            if (candidate.length() < 3 || candidate.length() > 70) continue;
            if (EMAIL_PATTERN.matcher(candidate).find() || PHONE_PATTERN.matcher(candidate).find() || URL_PATTERN.matcher(candidate).find()) continue;
            if (candidate.matches("(?i)^(resume|curriculum vitae|cv|curriculum vitae resume)$")) continue;
            if (candidate.matches(".*\\d.*") || candidate.contains("@")) continue;
            if (candidate.matches("(?i).*(\\b(engineer|developer|manager|director|consultant|specialist|student|candidate|professional|analyst|designer|scientist|executive)\\b).*")) continue;
            return candidate;
        }
        return null;
    }

    private ParsedExperiences parseExperiences(List<String> lines) {
        List<WorkExperience> items = new ArrayList<>();
        List<String> unclassified = new ArrayList<>();
        WorkExperience current = null;
        StringBuilder description = new StringBuilder();
        for (String line : lines) {
            Matcher date = DATE_RANGE.matcher(line);
            if (date.find()) {
                if (current != null) {
                    current.setDescription(cleanDescription(description.toString()));
                    items.add(current);
                }
                String header = (line.substring(0, date.start()) + " " + line.substring(date.end())).trim().replaceAll("[|\\s]+$", "");
                String[] parts = header.split("\\s{2,}|\\s*\\|\\s*|\\s+at\\s+", 2);
                current = new WorkExperience();
                if (!header.isBlank()) {
                    current.setRole(parts[0].trim());
                    if (parts.length > 1) current.setCompany(parts[1].trim());
                } else {
                    unclassified.add("Date range without an adjacent role or organization: " + line);
                }
                current.setStartDate(date.group(1).trim());
                current.setEndDate(date.group(2).trim());
                description = new StringBuilder();
            } else if (current != null) {
                if (description.length() > 0) description.append('\n');
                description.append(line);
            } else {
                unclassified.add(line);
            }
        }
        if (current != null) {
            current.setDescription(cleanDescription(description.toString()));
            items.add(current);
        }
        return new ParsedExperiences(items, unclassified);
    }

    private ParsedEducation parseEducation(List<String> lines) {
        List<Education> items = new ArrayList<>();
        List<String> unclassified = new ArrayList<>();
        Education current = null;
        StringBuilder description = new StringBuilder();
        for (String line : lines) {
            Matcher year = Pattern.compile("\\b(?:19|20)\\d{2}\\b").matcher(line);
            List<String> yearsFound = new ArrayList<>();
            while (year.find()) yearsFound.add(year.group());
            if (!yearsFound.isEmpty()) {
                if (current != null) {
                    current.setDescription(cleanDescription(description.toString()));
                    items.add(current);
                }
                String header = line.replaceAll("\\b(?:19|20)\\d{2}\\b", "").replaceAll("[-–—|,]+", "|").trim();
                String[] parts = Arrays.stream(header.split("\\|")).map(String::trim).filter(part -> !part.isBlank()).toArray(String[]::new);
                current = new Education();
                if (!header.isBlank()) {
                    if (parts.length > 0) current.setDegree(parts[0]);
                    if (parts.length > 1) current.setInstitution(String.join(" | ", Arrays.copyOfRange(parts, 1, parts.length)));
                }
                if (yearsFound.size() > 1) {
                    current.setStartDate(yearsFound.get(0));
                    current.setEndDate(yearsFound.get(yearsFound.size() - 1));
                } else {
                    current.setEndDate(yearsFound.get(0));
                }
                description = new StringBuilder();
            } else if (current != null) {
                if (description.length() > 0) description.append('\n');
                description.append(line);
            } else unclassified.add(line);
        }
        if (current != null) {
            current.setDescription(cleanDescription(description.toString()));
            items.add(current);
        }
        return new ParsedEducation(items, unclassified);
    }

    private List<Skill> parseSkills(List<String> lines) {
        List<Skill> skills = new ArrayList<>();
        for (String line : lines) {
            for (String token : line.split("[,;•|\\t]")) {
                String name = token.trim();
                if (!name.isEmpty()) skills.add(Skill.builder().name(name).build());
            }
        }
        return skills;
    }

    private void reportDuplicateSkills(List<Skill> skills, List<String> warnings) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (Skill skill : skills) {
            if (!seen.add(skill.getName().toLowerCase(Locale.ROOT))) duplicates.add(skill.getName());
        }
        if (!duplicates.isEmpty()) warnings.add("Potential duplicate skill(s) retained for review: " + String.join(", ", duplicates));
    }

    private List<Project> parseProjects(List<String> lines) {
        List<Project> result = new ArrayList<>();
        for (String line : lines) {
            String cleaned = removeBullet(line);
            if (cleaned.isBlank()) continue;
            // Keep each extracted line separately until a human confirms project grouping.
            result.add(Project.builder().title(cleaned).build());
        }
        return result;
    }

    private List<Certification> parseCertifications(List<String> lines) {
        List<Certification> result = new ArrayList<>();
        for (String line : lines) {
            String cleaned = removeBullet(line);
            if (!cleaned.isBlank()) result.add(Certification.builder().title(cleaned).build());
        }
        return result;
    }

    private List<Language> parseLanguages(List<String> lines) {
        List<Language> result = new ArrayList<>();
        for (String line : lines) {
            String cleaned = removeBullet(line);
            if (!cleaned.isBlank()) result.add(Language.builder().name(cleaned).build());
        }
        return result;
    }

    private List<CustomSectionEntry> parseCustomEntries(String section, List<String> lines) {
        List<CustomSectionEntry> result = new ArrayList<>();
        for (String line : lines) {
            String cleaned = removeBullet(line);
            if (!cleaned.isBlank()) result.add(CustomSectionEntry.builder().title(cleaned).build());
        }
        return result;
    }

    private String trimUrl(String raw) {
        return raw.replaceAll("[),.;!?]+$", "");
    }

    private String addScheme(String url) {
        return url.regionMatches(true, 0, "http://", 0, 7) || url.regionMatches(true, 0, "https://", 0, 8) ? url : "https://" + url;
    }

    private String removeBullet(String line) { return line.replaceFirst("^\\s*(?:[-*•▪◦]+)\\s+", "").trim(); }
    private String cleanDescription(String text) { return text == null || text.isBlank() ? null : text.trim(); }

    private record ParsedExperiences(List<WorkExperience> items, List<String> unclassified) {}
    private record ParsedEducation(List<Education> items, List<String> unclassified) {}

    public static class ParseResult {
        private final Resume resume;
        private final List<String> unclassifiedContent;
        private final List<String> warnings;
        public ParseResult(Resume resume, List<String> unclassifiedContent, List<String> warnings) {
            this.resume = resume;
            this.unclassifiedContent = unclassifiedContent;
            this.warnings = warnings;
        }
        public Resume getResume() { return resume; }
        public List<String> getUnclassifiedContent() { return unclassifiedContent; }
        public List<String> getWarnings() { return warnings; }
    }
}
