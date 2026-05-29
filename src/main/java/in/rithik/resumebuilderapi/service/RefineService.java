package in.rithik.resumebuilderapi.service;

import in.rithik.resumebuilderapi.document.Resume;
import in.rithik.resumebuilderapi.dto.RefineResponse;
import in.rithik.resumebuilderapi.dto.RefineSuggestion;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Strict, conservative ATS predictor.
 *
 * This is not a vendor formula. It is a deterministic rules engine that checks
 * every resume field the builder stores, then deducts points only for concrete
 * parser, structure, completeness, and content-quality risks it can explain.
 */
@Service
public class RefineService {

    private static final int MIN_SUMMARY_LEN = 80;
    private static final int MIN_EXPERIENCE_DESC_LEN = 45;
    private static final int MIN_PROJECT_DESC_LEN = 35;
    private static final int CURRENT_YEAR = Year.now().getValue();

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern URL = Pattern.compile("^(https?://)?[^\\s]+\\.[^\\s]{2,}.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HAS_METRIC = Pattern.compile(
        "(\\d+%|\\$\\s?\\d+|\\d+x|\\d{4,}|\\d+\\s*(users|clients|customers|requests|orders|revenue|sales|million|thousand|percent|hours|ms|seconds|repos|features|bugs|projects|team members|people))",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PASSIVE_VOICE = Pattern.compile(
        "\\b(was|were|been|is|are)\\s+(responsible for|managed by|handled by|tasked with|assigned to|involved in|utilized|leveraged)\\b",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FILLER_WORDS = Pattern.compile(
        "\\b(responsible for|helped with|assisted with|worked on|involved in|participated in|good at|team player|hard worker|fast learner|passionate about|results-driven|detail-oriented|dynamic|synergy|various|several|etc\\.)\\b",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ACTION_VERB = Pattern.compile(
        "\\b(achieved|automated|built|created|delivered|designed|developed|drove|implemented|improved|increased|launched|led|managed|migrated|optimized|owned|reduced|shipped|streamlined|trained|transformed)\\b",
        Pattern.CASE_INSENSITIVE
    );

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
        Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("apr", 4),
        Map.entry("may", 5), Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("aug", 8),
        Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dec", 12)
    );

    private static final Map<String, Integer> TEMPLATE_RISK = Map.ofEntries(
        Map.entry("template2", 6),
        Map.entry("template3", 3),
        Map.entry("premium1", 3),
        Map.entry("premium2", 2),
        Map.entry("premium3", 6),
        Map.entry("premium5", 3),
        Map.entry("premium6", 8),
        Map.entry("premium7", 6),
        Map.entry("premium8", 8),
        Map.entry("premium9", 2),
        Map.entry("premium10", 4)
    );

    private static final Map<String, String> TEMPLATE_NAMES = Map.ofEntries(
        Map.entry("template1", "Classic"),
        Map.entry("template2", "Side"),
        Map.entry("template3", "Banner"),
        Map.entry("premium1", "Timeline"),
        Map.entry("premium2", "Executive"),
        Map.entry("premium3", "Compact"),
        Map.entry("premium4", "Minimal"),
        Map.entry("premium5", "Accent"),
        Map.entry("premium6", "Split"),
        Map.entry("premium7", "Cards"),
        Map.entry("premium8", "Graph"),
        Map.entry("premium9", "Centered"),
        Map.entry("premium10", "Tech"),
        Map.entry("ats_classic", "Basic"),
        Map.entry("ats_entry", "Edge"),
        Map.entry("ats_senior", "Serif"),
        Map.entry("ats_lead", "Lead"),
        Map.entry("ats_intern", "Campus"),
        Map.entry("ats_experienced", "Prime")
    );

    public RefineResponse analyze(Resume resume) {
        List<RefineSuggestion> issues = new ArrayList<>();
        int deductions = 0;

        Resume.ProfileInfo profile = resume.getProfileInfo() != null ? resume.getProfileInfo() : new Resume.ProfileInfo();
        Resume.ContactInfo contact = resume.getContactInfo() != null ? resume.getContactInfo() : new Resume.ContactInfo();

        List<Resume.WorkExperience> experience = list(resume.getWorkExperience());
        List<Resume.Education> education = list(resume.getEducation());
        List<Resume.Skill> skills = list(resume.getSkills());
        List<Resume.Project> projects = list(resume.getProjects());
        List<Resume.Certification> certifications = list(resume.getCertifications());
        List<Resume.Language> languages = list(resume.getLanguages());
        List<String> interests = list(resume.getInterests());

        deductions += checkProfile(profile, issues);
        deductions += checkContact(contact, issues);
        deductions += checkExperience(experience, issues);
        deductions += checkEducation(education, issues);
        deductions += checkSkills(skills, issues);
        deductions += checkProjects(projects, experience, issues);
        deductions += checkCertifications(certifications, issues);
        deductions += checkLanguages(languages, issues);
        deductions += checkInterests(interests, issues);
        deductions += checkPresentation(resume, profile, issues);

        String category = AtsKeywords.detectCategory(profile.getDesignation());
        if (category != null) {
            deductions += checkKeywords(resume, category, issues);
        } else {
            int points = hasText(profile.getDesignation()) ? 2 : 0;
            if (points > 0) {
                issues.add(issue(
                    "role_category_unclear",
                    "Profile > Designation",
                    profile.getDesignation(),
                    "Use a standard target title such as Software Engineer, Product Manager, Designer, Data Analyst, or DevOps Engineer so keyword checks can be role-aware.",
                    "tip",
                    points
                ));
                deductions += points;
            }
        }

        int score = Math.max(0, Math.min(100, 100 - deductions));
        issues.sort(Comparator
            .comparingInt((RefineSuggestion i) -> severityRank(i.getSeverity()))
            .thenComparing((RefineSuggestion i) -> i.getPoints() == null ? 0 : -i.getPoints()));

        return RefineResponse.builder()
            .atsScore(score)
            .issues(issues)
            .overallFeedback(feedback(score, issues, category))
            .category(category)
            .build();
    }

    private int checkProfile(Resume.ProfileInfo profile, List<RefineSuggestion> issues) {
        int points = 0;

        points += requireText(issues, "missing_name", "Profile > Full name", profile.getFullName(), 6,
            "Add your full legal or professional name. ATS records need a clear candidate name.");

        points += requireText(issues, "missing_designation", "Profile > Designation", profile.getDesignation(), 6,
            "Add a target role title. This anchors the resume and enables role-specific keyword checks.");

        String summary = safe(profile.getSummary());
        if (!hasText(summary)) {
            points += add(issues, "missing_summary", "Profile > Summary", "", 12,
                "Add a 2-4 sentence summary with target role, years or scope, strongest skills, and measurable impact.", "error");
        } else {
            if (summary.length() < MIN_SUMMARY_LEN) {
                points += add(issues, "short_summary", "Profile > Summary", summary, 6,
                    "Expand the summary to at least 80 characters with role, strengths, and impact.", "warning");
            }
            if (!HAS_METRIC.matcher(summary).find()) {
                points += add(issues, "summary_no_metric", "Profile > Summary", trim(summary, 120), 3,
                    "Add one concrete signal, such as years of experience, users supported, revenue, performance, or team size.", "tip");
            }
            if (FILLER_WORDS.matcher(summary).find()) {
                String flagged = firstMatch(FILLER_WORDS, summary);
                points += add(issues, "summary_filler", "Profile > Summary", flagged, 3,
                    "Replace vague wording with a specific strength or outcome.", "warning");
            }
        }

        return points;
    }

    private int checkContact(Resume.ContactInfo contact, List<RefineSuggestion> issues) {
        int points = 0;

        String email = safe(contact.getEmail());
        if (!hasText(email)) {
            points += add(issues, "missing_email", "Contact > Email", "", 8,
                "Add a professional email address. ATS and recruiters need a direct contact field.", "error");
        } else if (!EMAIL.matcher(email).matches()) {
            points += add(issues, "invalid_email", "Contact > Email", email, 6,
                "Use a valid email format such as name@example.com.", "error");
        }

        String phone = safe(contact.getPhone());
        if (!hasText(phone)) {
            points += add(issues, "missing_phone", "Contact > Phone", "", 5,
                "Add a phone number with country code.", "warning");
        } else if (phone.replaceAll("\\D", "").length() < 8) {
            points += add(issues, "invalid_phone", "Contact > Phone", phone, 4,
                "Use a complete phone number. Include country code and enough digits.", "warning");
        }

        points += requireText(issues, "missing_location", "Contact > Location", contact.getLocation(), 3,
            "Add city and country or region. Many ATS filters use location.");

        points += checkOptionalUrl(contact.getLinkedIn(), "Contact > LinkedIn", "Use a full LinkedIn URL such as https://linkedin.com/in/username.", issues);
        points += checkOptionalUrl(contact.getGithub(), "Contact > GitHub", "Use a full GitHub URL such as https://github.com/username.", issues);
        points += checkOptionalUrl(contact.getWebsite(), "Contact > Website", "Use a complete portfolio URL, including a valid domain.", issues);

        return points;
    }

    private int checkExperience(List<Resume.WorkExperience> experience, List<RefineSuggestion> issues) {
        int points = 0;
        if (experience.isEmpty()) {
            return add(issues, "missing_experience", "Experience", "", 18,
                "Add at least one role, internship, freelance job, or substantial project-style experience.", "error");
        }

        for (int i = 0; i < experience.size(); i++) {
            Resume.WorkExperience job = experience.get(i);
            String label = "Experience " + (i + 1);

            points += requireText(issues, "missing_company", label + " > Company", job.getCompany(), 4,
                "Add the company or organization name.");
            points += requireText(issues, "missing_role", label + " > Role", job.getRole(), 4,
                "Add the role title exactly as you want recruiters and ATS to read it.");
            points += checkDateRange(job.getStartDate(), job.getEndDate(), label, issues, true);

            String description = safe(job.getDescription());
            if (!hasText(description)) {
                points += add(issues, "missing_experience_description", label + " > Description", "", 8,
                    "Add 2-4 bullets or sentences covering action, tools, and measurable impact.", "error");
            } else {
                points += checkContentQuality(description, label + " > Description", MIN_EXPERIENCE_DESC_LEN, true, issues);
            }
        }

        return points;
    }

    private int checkEducation(List<Resume.Education> education, List<RefineSuggestion> issues) {
        int points = 0;
        if (education.isEmpty()) {
            return add(issues, "missing_education", "Education", "", 6,
                "Add education, training, bootcamp, or equivalent credential. If not applicable, add your strongest formal training.", "warning");
        }

        for (int i = 0; i < education.size(); i++) {
            Resume.Education item = education.get(i);
            String label = "Education " + (i + 1);
            points += requireText(issues, "missing_degree", label + " > Degree", item.getDegree(), 4,
                "Add the degree, certificate, or course name.");
            points += requireText(issues, "missing_institution", label + " > Institution", item.getInstitution(), 4,
                "Add the school, university, or training provider.");
            points += checkDateRange(item.getStartDate(), item.getEndDate(), label, issues, false);
        }
        return points;
    }

    private int checkSkills(List<Resume.Skill> skills, List<RefineSuggestion> issues) {
        int points = 0;
        if (skills.isEmpty()) {
            return add(issues, "missing_skills", "Skills", "", 12,
                "Add 8-12 concrete skills. ATS keyword matching depends heavily on this section.", "error");
        }

        if (skills.size() < 5) {
            points += add(issues, "too_few_skills", "Skills", String.valueOf(skills.size()), 5,
                "Only " + skills.size() + " skills are listed. Add enough hard skills to match the target role.", "warning");
        }

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < skills.size(); i++) {
            Resume.Skill skill = skills.get(i);
            String label = "Skills " + (i + 1);
            String name = safe(skill.getName());
            if (!hasText(name)) {
                points += add(issues, "blank_skill", label + " > Name", "", 4,
                    "Remove the blank skill row or enter a specific skill name.", "error");
                continue;
            }
            String normalized = name.toLowerCase(Locale.ROOT);
            if (!seen.add(normalized)) {
                points += add(issues, "duplicate_skill", label + " > Name", name, 2,
                    "Remove duplicate skills. Keep one clear entry per skill.", "tip");
            }
            points += checkProgress(skill.getProgress(), label + " > Proficiency", issues);
        }
        return points;
    }

    private int checkProjects(List<Resume.Project> projects, List<Resume.WorkExperience> experience, List<RefineSuggestion> issues) {
        int points = 0;
        if (projects.isEmpty() && experience.size() <= 1) {
            return add(issues, "missing_projects", "Projects", "", 5,
                "Add one or two strong projects if your experience section is light. Include title, tools, and outcome.", "warning");
        }

        for (int i = 0; i < projects.size(); i++) {
            Resume.Project project = projects.get(i);
            String label = "Projects " + (i + 1);
            points += requireText(issues, "missing_project_title", label + " > Title", project.getTitle(), 4,
                "Add a concise project title.");
            String description = safe(project.getDescription());
            if (!hasText(description)) {
                points += add(issues, "missing_project_description", label + " > Description", "", 5,
                    "Add what the project does, what you used, and what improved.", "warning");
            } else {
                points += checkContentQuality(description, label + " > Description", MIN_PROJECT_DESC_LEN, false, issues);
            }
            points += checkOptionalUrl(project.getGithub(), label + " > GitHub URL", "Use a valid repository URL or leave the field empty.", issues);
            points += checkOptionalUrl(project.getLiveDemo(), label + " > Live demo URL", "Use a valid live demo URL or leave the field empty.", issues);
        }
        return points;
    }

    private int checkCertifications(List<Resume.Certification> certifications, List<RefineSuggestion> issues) {
        int points = 0;
        for (int i = 0; i < certifications.size(); i++) {
            Resume.Certification cert = certifications.get(i);
            String label = "Certifications " + (i + 1);
            points += requireText(issues, "missing_cert_title", label + " > Title", cert.getTitle(), 3,
                "Add the certification name or remove the blank certification row.");
            points += requireText(issues, "missing_cert_issuer", label + " > Issuer", cert.getIssuer(), 2,
                "Add the issuer so ATS and recruiters can verify the credential.");
            points += checkYear(cert.getYear(), label + " > Year", issues);
        }
        return points;
    }

    private int checkLanguages(List<Resume.Language> languages, List<RefineSuggestion> issues) {
        int points = 0;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < languages.size(); i++) {
            Resume.Language language = languages.get(i);
            String label = "Languages " + (i + 1);
            String name = safe(language.getName());
            if (!hasText(name)) {
                points += add(issues, "blank_language", label + " > Name", "", 2,
                    "Remove the blank language row or enter a language name.", "tip");
                continue;
            }
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                points += add(issues, "duplicate_language", label + " > Name", name, 1,
                    "Remove duplicate language entries.", "tip");
            }
            points += checkProgress(language.getProgress(), label + " > Proficiency", issues);
        }
        return points;
    }

    private int checkInterests(List<String> interests, List<RefineSuggestion> issues) {
        int points = 0;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < interests.size(); i++) {
            String interest = safe(interests.get(i));
            String label = "Interests " + (i + 1);
            if (!hasText(interest)) {
                points += add(issues, "blank_interest", label, "", 1,
                    "Remove blank interest rows.", "tip");
                continue;
            }
            if (!seen.add(interest.toLowerCase(Locale.ROOT))) {
                points += add(issues, "duplicate_interest", label, interest, 1,
                    "Remove duplicate interests.", "tip");
            }
        }
        if (interests.size() > 6) {
            points += add(issues, "too_many_interests", "Interests", String.valueOf(interests.size()), 1,
                "Keep interests short or remove them for ATS-first resumes. Use the space for experience, skills, or projects.", "tip");
        }
        return points;
    }

    private int checkPresentation(Resume resume, Resume.ProfileInfo profile, List<RefineSuggestion> issues) {
        int points = 0;
        String template = resume.getTemplate() != null ? resume.getTemplate().name() : "template1";
        Integer templateRisk = TEMPLATE_RISK.get(template);
        if (templateRisk != null) {
            points += add(issues, "template_parse_risk", "Customization > Template", templateName(template), templateRisk,
                "For ATS uploads, use Classic or Minimal. Keep visual templates for human-facing PDFs.", templateRisk >= 6 ? "warning" : "tip");
        }

        String photoUrl = safe(profile.getProfilePreviewUrl());
        Map<String, String> decoratives = resume.getDecoratives() != null ? resume.getDecoratives() : Collections.emptyMap();
        String photoShape = safe(decoratives.get("photoShape"));
        if (hasText(photoUrl) && !"none".equalsIgnoreCase(photoShape)) {
            points += add(issues, "profile_photo_parse_risk", "Profile > Photo", "Photo attached", 4,
                "Remove the photo for ATS-first resumes. Photos are often ignored and can reduce parser consistency.", "warning");
        }

        String headerStyle = safe(decoratives.get("headerStyle"));
        if ("full-bleed".equalsIgnoreCase(headerStyle) || "card".equalsIgnoreCase(headerStyle)) {
            points += add(issues, "decorative_header", "Customization > Header style", headerStyle, 3,
                "Use Minimal header style for ATS uploads. Decorative headers can change read order in some parsers.", "tip");
        }

        if ("true".equalsIgnoreCase(decoratives.get("sectionIcons"))) {
            points += add(issues, "section_icons", "Customization > Section icons", "Enabled", 3,
                "Disable section icons for ATS uploads. Icons can be parsed as stray characters.", "warning");
        }

        if ("true".equalsIgnoreCase(decoratives.get("sectionNumbers"))) {
            points += add(issues, "section_numbers", "Customization > Section numbers", "Enabled", 1,
                "Disable section numbers if the resume is being uploaded to an ATS. Plain section headings parse cleaner.", "tip");
        }

        String dividerStyle = safe(decoratives.get("dividerStyle"));
        if ("dots".equalsIgnoreCase(dividerStyle) || "gradient".equalsIgnoreCase(dividerStyle)) {
            points += add(issues, "decorative_divider", "Customization > Divider style", dividerStyle, 1,
                "Use a simple line divider or no divider for ATS uploads.", "tip");
        }

        String progressStyle = safe(decoratives.get("progressStyle"));
        if ("bar".equalsIgnoreCase(progressStyle) || "dots".equalsIgnoreCase(progressStyle)) {
            points += add(issues, "visual_progress", "Customization > Skill progress style", progressStyle, 1,
                "ATS reads skill names, not bars or dots. Keep the skill names textual and do not rely on visual proficiency.", "tip");
        }

        if (hasText(resume.getFontPairing()) && !"inter".equalsIgnoreCase(resume.getFontPairing())) {
            points += add(issues, "custom_font", "Customization > Font", resume.getFontPairing(), 1,
                "Use a common system-like font for ATS uploads. Keep custom fonts for human-facing versions.", "tip");
        }

        return points;
    }

    private int checkKeywords(Resume resume, String category, List<RefineSuggestion> issues) {
        List<String> keywords = AtsKeywords.CATEGORY_KEYWORDS.get(category);
        if (keywords == null || keywords.isEmpty()) return 0;

        String fullText = allText(resume).toLowerCase(Locale.ROOT);
        List<String> missing = new ArrayList<>();
        for (String keyword : keywords) {
            if (!fullText.contains(keyword.toLowerCase(Locale.ROOT))) {
                missing.add(keyword);
            }
        }

        if (missing.isEmpty()) return 0;

        double missRatio = (double) missing.size() / keywords.size();
        int points = Math.max(4, (int) Math.round(18 * missRatio));
        List<String> topMissing = missing.subList(0, Math.min(8, missing.size()));
        issues.add(issue(
            "keyword_gap",
            "Role keywords",
            "Missing: " + String.join(", ", topMissing),
            "For a " + category + " role, add only the missing keywords you genuinely have experience with: " + String.join(", ", topMissing) + ".",
            points >= 10 ? "warning" : "tip",
            points
        ));
        return points;
    }

    private int checkContentQuality(String text, String section, int minLength, boolean requireMetric, List<RefineSuggestion> issues) {
        int points = 0;
        String value = safe(text);

        if (value.length() < minLength) {
            points += add(issues, "short_description", section, value, 5,
                "Add more detail: action, tools, scope, and result.", "warning");
        }

        if (!ACTION_VERB.matcher(value).find()) {
            points += add(issues, "missing_action_verb", section, trim(value, 120), 3,
                "Start at least one sentence or bullet with a strong action verb such as Built, Led, Improved, Reduced, or Automated.", "tip");
        }

        if (requireMetric && !HAS_METRIC.matcher(value).find()) {
            points += add(issues, "missing_metric", section, trim(value, 120), 5,
                "Add a number or measurable result, for example: Reduced latency by 40% or Supported 10K users.", "warning");
        }

        if (PASSIVE_VOICE.matcher(value).find()) {
            String flagged = firstMatch(PASSIVE_VOICE, value);
            points += add(issues, "passive_voice", section, flagged, 4,
                "Rewrite this in active voice. Use a direct action verb and state your impact.", "warning");
        }

        if (FILLER_WORDS.matcher(value).find()) {
            String flagged = firstMatch(FILLER_WORDS, value);
            points += add(issues, "filler_word", section, flagged, 3,
                "Remove vague wording and replace it with a concrete action or result.", "warning");
        }

        return points;
    }

    private int checkDateRange(String start, String end, String section, List<RefineSuggestion> issues, boolean allowPresent) {
        int points = 0;
        if (!hasText(start)) {
            points += add(issues, "missing_start_date", section + " > Start date", "", 3,
                "Add a start month and year.", "warning");
        }
        if (!hasText(end)) {
            points += add(issues, "missing_end_date", section + " > End date", "", 3,
                allowPresent ? "Add an end month/year or mark it as Present." : "Add an end month and year.", "warning");
        }
        if (!hasText(start) || !hasText(end)) return points;
        if ("present".equalsIgnoreCase(end)) {
            if (!allowPresent) {
                points += add(issues, "invalid_end_date", section + " > End date", end, 2,
                    "Use a real end month and year for this section.", "warning");
            }
            return points;
        }

        Integer startValue = parseMonthYear(start);
        Integer endValue = parseMonthYear(end);
        if (startValue == null) {
            points += add(issues, "invalid_start_date", section + " > Start date", start, 2,
                "Use the editor date format: month plus four-digit year.", "warning");
        }
        if (endValue == null) {
            points += add(issues, "invalid_end_date", section + " > End date", end, 2,
                "Use the editor date format: month plus four-digit year.", "warning");
        }
        if (startValue != null && endValue != null && startValue > endValue) {
            points += add(issues, "date_order", section + " > Dates", start + " to " + end, 5,
                "Start date is after end date. Correct the timeline before applying.", "error");
        }
        return points;
    }

    private int checkYear(String year, String section, List<RefineSuggestion> issues) {
        if (!hasText(year)) {
            return add(issues, "missing_cert_year", section, "", 1,
                "Add the completion year or remove the year field if unknown.", "tip");
        }
        if (!year.matches("\\d{4}")) {
            return add(issues, "invalid_year", section, year, 2,
                "Use a four-digit year.", "warning");
        }
        int numericYear = Integer.parseInt(year);
        if (numericYear < 1950 || numericYear > CURRENT_YEAR + 10) {
            return add(issues, "year_out_of_range", section, year, 2,
                "Use a realistic year.", "warning");
        }
        return 0;
    }

    private int checkProgress(Integer progress, String section, List<RefineSuggestion> issues) {
        if (progress == null) {
            return add(issues, "missing_progress", section, "", 1,
                "Set a proficiency value or remove the visual proficiency control for ATS-first resumes.", "tip");
        }
        if (progress < 0 || progress > 100) {
            return add(issues, "invalid_progress", section, String.valueOf(progress), 1,
                "Keep proficiency between 0 and 100.", "tip");
        }
        return 0;
    }

    private int checkOptionalUrl(String url, String section, String fix, List<RefineSuggestion> issues) {
        if (!hasText(url)) return 0;
        if (!URL.matcher(url).matches()) {
            return add(issues, "invalid_url", section, url, 2, fix, "tip");
        }
        return 0;
    }

    private int requireText(List<RefineSuggestion> issues, String type, String section, String value, int points, String fix) {
        if (hasText(value)) return 0;
        return add(issues, type, section, "", points, fix, points >= 5 ? "error" : "warning");
    }

    private int add(List<RefineSuggestion> issues, String type, String section, String original, int points, String suggestion, String severity) {
        issues.add(issue(type, section, original, suggestion, severity, points));
        return points;
    }

    private RefineSuggestion issue(String type, String section, String original, String suggestion, String severity, int points) {
        return RefineSuggestion.builder()
            .type(type)
            .section(section)
            .original(original)
            .suggestion(suggestion)
            .severity(severity)
            .points(points)
            .build();
    }

    private String allText(Resume resume) {
        StringBuilder text = new StringBuilder();
        if (resume.getProfileInfo() != null) {
            text.append(safe(resume.getProfileInfo().getFullName())).append(' ');
            text.append(safe(resume.getProfileInfo().getDesignation())).append(' ');
            text.append(safe(resume.getProfileInfo().getSummary())).append(' ');
        }
        if (resume.getContactInfo() != null) {
            text.append(safe(resume.getContactInfo().getLocation())).append(' ');
            text.append(safe(resume.getContactInfo().getLinkedIn())).append(' ');
            text.append(safe(resume.getContactInfo().getGithub())).append(' ');
            text.append(safe(resume.getContactInfo().getWebsite())).append(' ');
        }
        list(resume.getWorkExperience()).forEach(item -> {
            text.append(safe(item.getCompany())).append(' ');
            text.append(safe(item.getRole())).append(' ');
            text.append(safe(item.getDescription())).append(' ');
        });
        list(resume.getEducation()).forEach(item -> {
            text.append(safe(item.getDegree())).append(' ');
            text.append(safe(item.getInstitution())).append(' ');
        });
        list(resume.getSkills()).forEach(item -> text.append(safe(item.getName())).append(' '));
        list(resume.getProjects()).forEach(item -> {
            text.append(safe(item.getTitle())).append(' ');
            text.append(safe(item.getDescription())).append(' ');
            text.append(safe(item.getGithub())).append(' ');
            text.append(safe(item.getLiveDemo())).append(' ');
        });
        list(resume.getCertifications()).forEach(item -> {
            text.append(safe(item.getTitle())).append(' ');
            text.append(safe(item.getIssuer())).append(' ');
            text.append(safe(item.getYear())).append(' ');
        });
        list(resume.getLanguages()).forEach(item -> text.append(safe(item.getName())).append(' '));
        list(resume.getInterests()).forEach(item -> text.append(safe(item)).append(' '));
        return text.toString();
    }

    private Integer parseMonthYear(String value) {
        String[] parts = safe(value).trim().split("\\s+");
        if (parts.length != 2) return null;
        Integer month = MONTHS.get(parts[0].substring(0, Math.min(3, parts[0].length())).toLowerCase(Locale.ROOT));
        if (month == null || !parts[1].matches("\\d{4}")) return null;
        int year = Integer.parseInt(parts[1]);
        if (year < 1950 || year > CURRENT_YEAR + 10) return null;
        return year * 12 + month;
    }

    private String firstMatch(Pattern pattern, String text) {
        var matcher = pattern.matcher(safe(text));
        return matcher.find() ? matcher.group() : "";
    }

    private String feedback(int score, List<RefineSuggestion> issues, String category) {
        long errors = issues.stream().filter(i -> "error".equals(i.getSeverity())).count();
        long warnings = issues.stream().filter(i -> "warning".equals(i.getSeverity())).count();
        String role = category != null ? " for a " + category + " role" : "";
        if (score >= 85) return "Strong ATS-ready structure" + role + ". Fix the remaining small parser risks before uploading.";
        if (score >= 70) return "Good foundation" + role + ". Address " + errors + " critical issue(s) and " + warnings + " warning(s) to reduce ATS risk.";
        if (score >= 50) return "Moderate ATS risk" + role + ". Focus first on missing required fields, measurable experience, and parser-friendly layout.";
        return "High ATS risk" + role + ". Fill missing sections, simplify formatting, and rewrite weak bullets before applying.";
    }

    private int severityRank(String severity) {
        if ("error".equals(severity)) return 0;
        if ("warning".equals(severity)) return 1;
        return 2;
    }

    private String templateName(String template) {
        return TEMPLATE_NAMES.getOrDefault(template, template);
    }

    private String trim(String value, int max) {
        String text = safe(value).trim();
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private <T> List<T> list(List<T> value) {
        return value == null ? Collections.emptyList() : value;
    }
}
