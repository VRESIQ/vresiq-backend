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
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative parser: source wording is retained and uncertain material is surfaced for review. */
@Component
public class DeterministicParser {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\w)(?:\\+?\\d[\\d\\s()./-]{6,}\\d)(?!\\w)");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>]+|(?:www\\.)?[a-zA-Z0-9.-]+\\.(?:com|org|net|ca|io|dev|edu|gov|co\\.[a-z]{2})/[^\\s<>]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_RANGE = Pattern.compile("(?i)((?:(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+)?(?:19|20)\\d{2}|present|current|now)\\s*(?:-|–|—|to)\\s*((?:(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+)?(?:19|20)\\d{2}|present|current|now)");
    private static final Pattern MONTH_YEAR_AT_END = Pattern.compile("(?i)(?:(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+)?(?:19|20)\\d{2}$");
    private static final Pattern YEAR_AT_END = Pattern.compile("(?i)(?:19|20)\\d{2}$");
    private static final Pattern EDUCATION_MONTH = Pattern.compile("(?i)\\b(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\b");
    private static final Pattern VRESIQ_WATERMARK = Pattern.compile("(?i)^made with vresiq$");

    /** Words that may appear lowercase inside a proper organization name. */
    private static final Set<String> ORGANIZATION_CONNECTORS = Set.of(
            "of", "and", "&", "the", "for", "at", "on", "in", "de", "du", "del", "la", "le", "und", "y", "e");

    /** Sentence-opening verbs that mark a bullet rather than an organization name. */
    private static final Set<String> ACTION_VERBS = Set.of(
            "led", "ran", "built", "made", "drove", "grew", "cut", "set", "won", "owned", "wrote", "oversaw",
            "hired", "taught", "brought", "kept", "held", "spent", "met");

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
        int watermarkCount = countWatermarkLines(text);
        List<String> cleanLines = nonEmptyLines(text);
        if (cleanLines.isEmpty()) {
            warnings.add("No extractable text was found. The document may be image-only or scanned; OCR is not configured.");
            return new ParseResult(resume, unclassified, warnings);
        }
        if (watermarkCount > 0) warnings.add("Excluded " + watermarkCount + " exact VResIQ footer watermark line(s) from resume content.");

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
            } else if (!isStandaloneLink(line)) {
                // Standalone links are captured in ContactInfo/Links; don't also misclassify them
                // as language, skill, publication, or experience text in the current section.
                sections.get(current).add(line);
            }
        }

        ProfileInfo profile = new ProfileInfo();
        String detectedName = detectName(preamble);
        if (detectedName != null) profile.setFullName(detectedName);
        List<String> preambleContent = new ArrayList<>(preamble);
        if (detectedName != null) preambleContent.remove(detectedName);
        String detectedDesignation = preambleContent.stream()
                .filter(line -> !EMAIL_PATTERN.matcher(line).find() && !PHONE_PATTERN.matcher(line).find() && !URL_PATTERN.matcher(line).find())
                .filter(line -> !line.contains(",") || line.length() < 40)
                .findFirst().orElse(null);
        if (detectedDesignation != null) {
            profile.setDesignation(detectedDesignation);
            preambleContent.remove(detectedDesignation);
        }
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
            String found = normalizeResumeUrl(trimUrl(url.group()));
            String canonical = canonicalizeUrl(found);
            if (!seenUrls.add(canonical)) duplicateUrls.add(found);
            else allUrls.add(found);
        }
        String publicationUrl = sections.containsKey("publications")
                ? allUrls.stream().filter(this::isPublicationUrl).findFirst().orElse(null) : null;
        for (String found : allUrls) {
            String normalized = found.toLowerCase(Locale.ROOT);
            if (contact.getLinkedIn() == null && normalized.contains("linkedin.com/")) contact.setLinkedIn(found);
            else if (contact.getGithub() == null && normalized.contains("github.com/")) contact.setGithub(found);
            String mapLocation = extractGoogleMapsLocation(found);
            if (contact.getLocation() == null && mapLocation != null) contact.setLocation(mapLocation);
        }
        Matcher preambleUrl = URL_PATTERN.matcher(String.join("\n", preamble));
        while (preambleUrl.find()) {
            String found = normalizeResumeUrl(trimUrl(preambleUrl.group()));
            if (contact.getWebsite() == null && !isContactSocialUrl(found) && extractGoogleMapsLocation(found) == null
                    && !sameUrl(found, publicationUrl)) {
                contact.setWebsite(found);
            }
        }
        resume.setContactInfo(contact);
        if (!duplicateUrls.isEmpty()) warnings.add("Repeated URLs were detected and consolidated using normalized destinations.");

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
        if (sections.containsKey("publications")) custom.put("publications", parsePublications(sections.get("publications"), publicationUrl));
        for (String key : List.of("volunteering", "awards")) {
            if (sections.containsKey(key)) custom.put(key, parseCustomEntries(key, sections.get(key)));
        }
        // Preserve links that aren't already represented as a structured contact field.
        List<CustomSectionEntry> otherLinks = new ArrayList<>();
        for (String link : allUrls) {
            String website = contact.getWebsite() == null ? "" : contact.getWebsite().toString().toLowerCase(Locale.ROOT);
            if (!isContactSocialUrl(link) && extractGoogleMapsLocation(link) == null
                    && !sameUrl(link, website) && !sameUrl(link, publicationUrl)) {
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
            String trimmed = repairKnownCollapsedBoundaries(line.trim());
            if (!trimmed.isEmpty() && !VRESIQ_WATERMARK.matcher(trimmed).matches()) lines.add(trimmed);
        }
        return lines;
    }

    private int countWatermarkLines(String text) {
        if (text == null || text.isBlank()) return 0;
        return (int) Arrays.stream(text.split("\\R"))
                .map(String::trim).filter(line -> VRESIQ_WATERMARK.matcher(line).matches()).count();
    }

    private String repairKnownCollapsedBoundaries(String line) {
        return line.replaceAll("(?i)\\bdatasets(?=including\\b)", "datasets ")
                .replaceAll("(?i)\\bclaims(?=to\\b)", "claims ");
    }

    private boolean isStandaloneLink(String line) {
        return URL_PATTERN.matcher(line).matches() || line.matches("(?i)^(?:mailto|tel):[^\\s]+$");
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
                if (current.getCompany() == null && description.length() == 0 && isStandaloneOrganizationLine(line)) {
                    // The layout puts the employer on the first line after the role/date header.
                    current.setCompany(line);
                } else {
                    if (current.getCompany() == null && description.length() == 0 && isAmbiguousOrganizationLine(line)) {
                        unclassified.add("Possible organization line needs review: " + line);
                    }
                    if (description.length() > 0) description.append('\n');
                    description.append(line);
                }
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

    /**
     * Accepts a standalone organization line only when both the layout (first line after the header)
     * and the shape (short proper-noun phrase, no dates, digits, bullets or sentence punctuation) agree.
     * Anything ambiguous is left in the description untouched instead of being guessed into the company field.
     */
    private boolean isStandaloneOrganizationLine(String raw) {
        String line = raw == null ? "" : raw.trim();
        if (line.length() < 3 || line.length() > 80) return false;
        if (!looksLikeProperNounPhrase(line, 8)) return false;
        if (line.contains(",") || line.matches(".*[.!?;:].*")) return false;
        return true;
    }

    /** Same shape as an organization, but not safe to map; surfaced as unclassified content for review. */
    private boolean isAmbiguousOrganizationLine(String raw) {
        String line = raw == null ? "" : raw.trim();
        if (line.length() < 3 || line.length() > 120) return false;
        if (!looksLikeProperNounPhrase(line, 12)) return false;
        return !isStandaloneOrganizationLine(line);
    }

    private boolean looksLikeProperNounPhrase(String line, int maxWords) {
        if (line.matches("^[\\u2022*\\-\u25aa\u00b7\u2013\u2014].*")) return false;
        if (line.matches(".*\\d.*")) return false;
        if (line.contains("@") || URL_PATTERN.matcher(line).find()) return false;
        if (DATE_RANGE.matcher(line).find()) return false;
        String[] words = line.split("\\s+");
        if (words.length < 2 || words.length > maxWords) return false;
        if (!Character.isUpperCase(words[0].charAt(0))) return false;
        if (startsWithActionVerb(words[0])) return false;
        long properWords = Arrays.stream(words)
                .filter(word -> Character.isUpperCase(word.charAt(0))
                        || ORGANIZATION_CONNECTORS.contains(word.toLowerCase(Locale.ROOT)))
                .count();
        return properWords * 5 >= words.length * 3;
    }

    private boolean startsWithActionVerb(String firstWord) {
        String word = firstWord.replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT);
        if (word.endsWith("ing")) return true;
        if (word.endsWith("ed") && word.length() > 4) return true;
        return ACTION_VERBS.contains(word);
    }

    private ParsedEducation parseEducation(List<String> lines) {
        List<Education> items = new ArrayList<>();
        List<String> unclassified = new ArrayList<>();
        List<String> pendingInstitution = new ArrayList<>();
        for (String line : lines) {
            Matcher year = Pattern.compile("\\b(?:19|20)\\d{2}\\b").matcher(line);
            List<String> yearsFound = new ArrayList<>();
            while (year.find()) yearsFound.add(year.group());
            if (!yearsFound.isEmpty()) {
                String header = line.replaceAll("\\b(?:19|20)\\d{2}\\b", "");
                header = EDUCATION_MONTH.matcher(header).replaceAll("").replaceAll("[-–—]+", " ").trim();
                String[] inlineParts = Arrays.stream(header.split("\\s*\\|\\s*|\\s{2,}"))
                        .map(String::trim).filter(part -> !part.isBlank()).toArray(String[]::new);
                Education current = new Education();
                if (!pendingInstitution.isEmpty()) {
                    current.setInstitution(String.join(" ", pendingInstitution).trim());
                    if (!header.isBlank()) current.setDegree(header);
                    pendingInstitution.clear();
                } else if (inlineParts.length > 1) {
                    current.setDegree(inlineParts[0]);
                    current.setInstitution(String.join(" | ", Arrays.copyOfRange(inlineParts, 1, inlineParts.length)));
                } else if (!header.isBlank()) {
                    current.setDegree(header);
                }

                Matcher dateRange = DATE_RANGE.matcher(line);
                if (dateRange.find()) {
                    current.setStartDate(dateRange.group(1).trim());
                    current.setEndDate(dateRange.group(2).trim());
                } else if (yearsFound.size() > 1) {
                    int firstYear = yearsFound.stream().mapToInt(Integer::parseInt).min().orElseThrow();
                    int lastYear = yearsFound.stream().mapToInt(Integer::parseInt).max().orElseThrow();
                    current.setStartDate(String.valueOf(firstYear));
                    current.setEndDate(String.valueOf(lastYear));
                } else {
                    current.setEndDate(yearsFound.get(0));
                }
                items.add(current);
            } else {
                pendingInstitution.add(line);
            }
        }
        if (!pendingInstitution.isEmpty()) unclassified.addAll(pendingInstitution);
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
        boolean hasWebsiteLabel = lines.stream().anyMatch(line -> line.matches("(?i)^website(?:\\s+.*)?$"));
        if (hasWebsiteLabel && !lines.isEmpty()) {
            Project project = Project.builder().title(removeBullet(lines.get(0))).build();
            List<String> descriptionLines = lines.subList(1, lines.size()).stream()
                    .map(this::removeBullet).filter(value -> !value.isBlank()).toList();
            if (!descriptionLines.isEmpty()) project.setDescription(String.join("\n", descriptionLines));
            result.add(project);
            return result;
        }

        Project current = null;
        StringBuilder description = new StringBuilder();
        for (String line : lines) {
            String cleaned = removeBullet(line);
            if (cleaned.isBlank()) continue;
            boolean explicitBullet = line.matches("^\\s*(?:[-*•▪◦]+)\\s+.*");
            if (current == null || !explicitBullet) {
                if (current != null) {
                    current.setDescription(cleanDescription(description.toString()));
                    result.add(current);
                }
                current = Project.builder().title(cleaned).build();
                description = new StringBuilder();
            } else {
                if (description.length() > 0) description.append('\n');
                description.append(cleaned);
            }
        }
        if (current != null) {
            current.setDescription(cleanDescription(description.toString()));
            result.add(current);
        }
        return result;
    }

    private List<Certification> parseCertifications(List<String> lines) {
        List<Certification> result = new ArrayList<>();
        for (String line : lines) {
            String cleaned = removeBullet(line);
            if (cleaned.isBlank()) continue;
            Matcher year = MONTH_YEAR_AT_END.matcher(cleaned);
            String yearValue = null;
            if (year.find()) {
                yearValue = year.group();
                cleaned = cleaned.substring(0, year.start()).replaceAll("[,|•\\s]+$", "").trim();
            }
            int lastComma = cleaned.lastIndexOf(',');
            String title = cleaned;
            String issuer = null;
            if (lastComma > 0) {
                title = cleaned.substring(0, lastComma).trim();
                issuer = cleaned.substring(lastComma + 1).trim();
            }
            result.add(Certification.builder().title(title).issuer(issuer).year(yearValue).build());
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

    private List<CustomSectionEntry> parsePublications(List<String> lines, String paperUrl) {
        List<String> content = lines.stream().map(this::removeBullet)
                .filter(line -> !line.isBlank() && !line.matches("(?i)^paper\\s+link(?:\\s+.*)?$"))
                .filter(line -> !isStandaloneLink(line)).toList();
        if (content.isEmpty()) return List.of();

        String title = content.get(0);
        String subtitle = null;
        String date = null;
        StringBuilder description = new StringBuilder();
        Matcher inlineDate = MONTH_YEAR_AT_END.matcher(title);
        if (inlineDate.find()) {
            date = inlineDate.group().trim();
            String beforeDate = title.substring(0, inlineDate.start()).replaceAll("[\\s,|•–—-]+$", "").trim();
            int separator = Math.max(beforeDate.lastIndexOf(','), Math.max(beforeDate.lastIndexOf('|'), beforeDate.lastIndexOf('•')));
            if (separator > 0) {
                subtitle = beforeDate.substring(separator + 1).trim();
                title = beforeDate.substring(0, separator).trim();
            } else {
                title = beforeDate;
            }
        }
        for (int i = 1; i < content.size(); i++) {
            String line = content.get(i);
            Matcher dateMatcher = MONTH_YEAR_AT_END.matcher(line);
            if (dateMatcher.find()) {
                date = dateMatcher.group().trim();
                subtitle = line.substring(0, dateMatcher.start()).replaceAll("[\\s,|•–—-]+$", "").trim();
            } else {
                if (description.length() > 0) description.append('\n');
                description.append(line);
            }
        }
        CustomSectionEntry entry = CustomSectionEntry.builder().title(title).subtitle(subtitle)
                .date(date).paperUrl(paperUrl).description(cleanDescription(description.toString())).build();
        return List.of(entry);
    }

    private String trimUrl(String raw) {
        return raw.replaceAll("[),.;!?]+$", "");
    }

    private String addScheme(String url) {
        return url.regionMatches(true, 0, "http://", 0, 7) || url.regionMatches(true, 0, "https://", 0, 8) ? url : "https://" + url;
    }

    private String normalizeResumeUrl(String raw) {
        String normalized = addScheme(raw);
        if (normalized.toLowerCase(Locale.ROOT).contains("linkedin.com/")) {
            normalized = normalized.replaceFirst("(?i)(/in/)(?:www\\.)?linkedin\\.com/in/", "$1");
        }
        return normalized;
    }

    private String canonicalizeUrl(String raw) {
        return addScheme(raw).replaceFirst("(?i)^https?://", "").replaceFirst("(?i)^www\\.", "")
                .replaceAll("/+$", "").toLowerCase(Locale.ROOT);
    }

    private boolean sameUrl(String left, String right) {
        return left != null && right != null && canonicalizeUrl(left).equals(canonicalizeUrl(right));
    }

    private boolean isContactSocialUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("linkedin.com/") || lower.contains("github.com/");
    }

    private boolean isPublicationUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("publications.") || lower.contains("doi.org/") || lower.contains("pubmed.")
                || lower.contains("arxiv.org/") || lower.contains("ncbi.nlm.nih.gov/")
                || lower.contains("journals.") || lower.contains("journal.");
    }

    private String extractGoogleMapsLocation(String url) {
        try {
            URI uri = URI.create(addScheme(url));
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            if (!(host.endsWith("google.com") && path.contains("/maps/"))) return null;
            String query = uri.getRawQuery();
            if (query == null) return null;
            for (String param : query.split("&")) {
                String[] parts = param.split("=", 2);
                if (parts.length == 2 && parts[0].equalsIgnoreCase("query")) {
                    String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8).trim();
                    return value.isBlank() ? null : value;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid URLs stay reviewable as source data and must not break reconstruction.
        }
        return null;
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
