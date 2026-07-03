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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * RefineService — backend ATS scoring adapter.
 *
 * ─── ARCHITECTURE ──────────────────────────────────────────────────────────
 * This service is ONE of TWO language-specific adapters for the single ATS
 * scoring algorithm. The other adapter is atsScorer.js (frontend).
 *
 *   atsRules.json        ← single source of truth (weights, keywords, risks)
 *       │
 *       ├── RefineService.java  (this file — backend Java adapter)
 *       └── atsScorer.js        (frontend JS adapter, reads same JSON)
 *
 * ─── CONSTANTS POLICY ──────────────────────────────────────────────────────
 * All numeric weights, keyword lists, and template risks are loaded at class
 * initialization from AtsRulesLoader (which reads atsRules.json).
 * This file contains ZERO hardcoded scoring constants.
 *
 * ─── REGEX PATTERNS ────────────────────────────────────────────────────────
 * Regex patterns are language-specific Java syntax and cannot be stored in
 * JSON. They implement the same semantic rules as the JavaScript equivalents
 * in atsScorer.js. Any semantic change must be applied to both adapters.
 *
 * ─── ALGORITHM ─────────────────────────────────────────────────────────────
 * score = max(0, min(100, 100 - total_deductions))
 * Issues are sorted: errors first, then warnings, then tips; ties broken by
 * descending deduction points. Both adapters use this identical sort.
 */
@Service
public class RefineService {

    // ─── Config loaded from atsRules.json (single source of truth) ───────────
    private static final AtsConfig CFG  = AtsRulesLoader.getConfig();
    private static final AtsConfig.Thresholds T   = CFG.getThresholds();
    private static final AtsConfig.ScoreBands SB  = CFG.getScoreBands();
    private static final int CURRENT_YEAR          = Year.now().getValue();

    // ─── Regex patterns — language-specific Java syntax ──────────────────────
    // Semantic equivalents defined in atsScorer.js
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern URL   = Pattern.compile("^(https?://)?[^\\s]+\\.[^\\s]{2,}.*$", Pattern.CASE_INSENSITIVE);
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
        Map.entry("jan", 1), Map.entry("feb", 2),  Map.entry("mar", 3),  Map.entry("apr", 4),
        Map.entry("may", 5), Map.entry("jun", 6),  Map.entry("jul", 7),  Map.entry("aug", 8),
        Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dec", 12)
    );

    // ─── Public entry point ───────────────────────────────────────────────────

    public RefineResponse analyze(Resume resume) {
        List<RefineSuggestion> issues = new ArrayList<>();
        int deductions = 0;

        Resume.ProfileInfo profile = resume.getProfileInfo() != null ? resume.getProfileInfo() : new Resume.ProfileInfo();
        Resume.ContactInfo contact = resume.getContactInfo() != null ? resume.getContactInfo() : new Resume.ContactInfo();

        List<Resume.WorkExperience> experience     = list(resume.getWorkExperience());
        List<Resume.Education>      education      = list(resume.getEducation());
        List<Resume.Skill>          skills         = list(resume.getSkills());
        List<Resume.Project>        projects       = list(resume.getProjects());
        List<Resume.Certification>  certifications = list(resume.getCertifications());
        List<Resume.Language>       languages      = list(resume.getLanguages());
        List<String>                interests      = list(resume.getInterests());

        String stage = detectCareerStage(resume);

        deductions += checkProfile(profile, issues, stage);
        deductions += checkContact(contact, issues);
        deductions += checkExperience(experience, issues, resume);
        deductions += checkEducation(education, issues);
        deductions += checkSkills(skills, issues);
        deductions += checkProjects(projects, experience, issues, stage);
        deductions += checkCertifications(certifications, issues);
        deductions += checkLanguages(languages, issues);
        deductions += checkInterests(interests, issues);
        deductions += checkPresentation(resume, profile, issues);

        String category = AtsKeywords.detectCategory(profile.getDesignation(), resume);
        boolean isMatchedRole = category != null && !"General Resume".equals(category);
        if (isMatchedRole) {
            deductions += checkKeywords(resume, category.toLowerCase(Locale.ROOT), issues, stage);
        }

        // Identical formula to atsScorer.js
        int score = Math.max(0, Math.min(100, 100 - deductions));

        // Identical sort to atsScorer.js (errors → warnings → tips, then by deduction desc)
        issues.sort(Comparator
            .comparingInt((RefineSuggestion i) -> severityRank(i.getSeverity()))
            .thenComparing((RefineSuggestion i) -> i.getPoints() == null ? 0 : -i.getPoints())
        );

        return RefineResponse.builder()
            .atsScore(score)
            .issues(issues)
            .overallFeedback(feedback(score, issues, category))
            .category(category)
            .build();
    }

    // ─── Section checkers ─────────────────────────────────────────────────────

    private int checkProfile(Resume.ProfileInfo profile, List<RefineSuggestion> issues, String stage) {
        int pts = 0;
        pts += requireText(issues, "missing_name",        "Profile > Full name",   profile.getFullName(),    CFG.d("missingName"),        "Add your professional or legal name at the top of your resume. ATS systems use this identifier to create your candidate profile.");
        pts += requireText(issues, "missing_designation", "Profile > Designation", profile.getDesignation(), CFG.d("missingDesignation"), "Add a target designation title. This anchors your resume in the database and enables role-based keywords matching.");

        String summary = safe(profile.getSummary());
        if (!hasText(summary)) {
            String missingDesc = "Add a 2-4 sentence summary with target role, strengths, and measurable impact.";
            if ("Student".equals(stage)) {
                missingDesc = "To stand out as a student, add academic projects, certifications, internships, hackathons, or measurable project outcomes to your summary.";
            } else if ("Fresher".equals(stage)) {
                missingDesc = "As a fresher, showcase your internships, core technical strengths, and final-year project impact in your summary.";
            } else {
                missingDesc = "For experienced roles, add quantified business impact, leadership scope, users supported, or performance metrics.";
            }
            pts += add(issues, "missing_summary", "Profile > Summary", "", CFG.d("missingSummary"), missingDesc, "error");
        } else {
            if (summary.length() < T.getMinSummaryLen()) {
                pts += add(issues, "short_summary", "Profile > Summary", summary, CFG.d("shortSummary"),
                    "Expand your summary to at least 80 characters. Describe your primary strengths, target role, and highest value achievement.", "warning");
            }
            if (!HAS_METRIC.matcher(summary).find()) {
                String suggestion = "Add one concrete metric, such as users supported, team size, budget managed, or project outcomes.";
                if ("Student".equals(stage)) {
                    suggestion = "To stand out as a student, add academic project outcomes, certifications, hackathon rankings, or GPA to your summary.";
                } else if ("Fresher".equals(stage)) {
                    suggestion = "As a fresher, showcase your internships, core technical strengths, or final-year project outcomes in your summary.";
                } else {
                    suggestion = "For experienced roles, add quantified business impact, users supported, revenue, or system performance metrics to your summary.";
                }
                pts += add(issues, "summary_no_metric", "Profile > Summary", trim(summary, 120), CFG.d("summaryNoMetric"), suggestion, "tip");
            }
            if (FILLER_WORDS.matcher(summary).find()) {
                pts += add(issues, "summary_filler", "Profile > Summary", firstMatch(FILLER_WORDS, summary), CFG.d("summaryFiller"),
                    "Replace vague buzzwords (like 'hard-working' or 'passionate') with concrete achievements or technical skills.", "warning");
            }
        }
        return pts;
    }

    private int checkContact(Resume.ContactInfo contact, List<RefineSuggestion> issues) {
        int pts = 0;
        String email = safe(contact.getEmail());
        if (!hasText(email)) {
            pts += add(issues, "missing_email", "Contact > Email", "", CFG.d("missingEmail"),
                "Add a professional email address. ATS and recruiters need a direct contact field.", "error");
        } else if (!EMAIL.matcher(email).matches()) {
            pts += add(issues, "invalid_email", "Contact > Email", email, CFG.d("invalidEmail"),
                "Use a valid email format such as name@example.com.", "error");
        }
        String phone = safe(contact.getPhone());
        if (!hasText(phone)) {
            pts += add(issues, "missing_phone", "Contact > Phone", "", CFG.d("missingPhone"),
                "Add a phone number with country code.", "warning");
        } else if (phone.replaceAll("\\D", "").length() < 8) {
            pts += add(issues, "invalid_phone", "Contact > Phone", phone, CFG.d("invalidPhone"),
                "Use a complete phone number. Include country code and enough digits.", "warning");
        }
        pts += requireText(issues, "missing_location", "Contact > Location", safe(contact.getLocation()), CFG.d("missingLocation"),
            "Add city and country or region. Many ATS filters use location.");
        pts += checkOptionalUrl(safe(contact.getLinkedIn()), "Contact > LinkedIn", "Use a full LinkedIn URL such as https://linkedin.com/in/username.", issues);
        pts += checkOptionalUrl(safe(contact.getGithub()),   "Contact > GitHub",   "Use a full GitHub URL such as https://github.com/username.", issues);
        pts += checkOptionalUrl(safe(contact.getWebsite()),  "Contact > Website",  "Use a complete portfolio URL, including a valid domain.", issues);
        return pts;
    }

    private int checkExperience(List<Resume.WorkExperience> experience, List<RefineSuggestion> issues, Resume resume) {
        if (experience.isEmpty()) {
            boolean hasInternships = resume.getCustomSections() != null && resume.getCustomSections().containsKey("internships") && !resume.getCustomSections().get("internships").isEmpty();
            boolean hasProjects = resume.getProjects() != null && !resume.getProjects().isEmpty();
            if (hasInternships || hasProjects) {
                return 0; // Bypass missing experience penalty if they have projects or internships
            }
            return add(issues, "missing_experience", "Experience", "", CFG.d("missingExperience"),
                "Add at least one professional role, internship, or technical project to demonstrate hands-on application of your skills.", "error");
        }
        int pts = 0;
        for (int i = 0; i < experience.size(); i++) {
            Resume.WorkExperience job = experience.get(i);
            String label = "Experience " + (i + 1);
            pts += requireText(issues, "missing_company", label + " > Company", job.getCompany(), CFG.d("missingCompany"),
                "Add the company or organization name.");
            pts += requireText(issues, "missing_role",    label + " > Role",    job.getRole(),    CFG.d("missingRole"),
                "Add the role title exactly as you want recruiters and ATS to read it.");
            pts += checkDateRange(job.getStartDate(), job.getEndDate(), label, issues, true);
            String description = safe(job.getDescription());
            if (!hasText(description)) {
                pts += add(issues, "missing_experience_description", label + " > Description", "", CFG.d("missingExperienceDescription"),
                    "Add 2-4 bullets or sentences covering action, tools, and measurable impact.", "error");
            } else {
                pts += checkContentQuality(description, label + " > Description", T.getMinExperienceDescLen(), true, issues);
            }
        }
        return pts;
    }

    private int checkEducation(List<Resume.Education> education, List<RefineSuggestion> issues) {
        if (education.isEmpty()) {
            return add(issues, "missing_education", "Education", "", CFG.d("missingEducation"),
                "Add education, training, bootcamp, or equivalent credential. If not applicable, add your strongest formal training.", "warning");
        }
        int pts = 0;
        for (int i = 0; i < education.size(); i++) {
            Resume.Education item = education.get(i);
            String label = "Education " + (i + 1);
            pts += requireText(issues, "missing_degree",      label + " > Degree",      item.getDegree(),      CFG.d("missingDegree"),      "Add the degree, certificate, or course name.");
            pts += requireText(issues, "missing_institution", label + " > Institution", item.getInstitution(), CFG.d("missingInstitution"), "Add the school, university, or training provider.");
            pts += checkDateRange(item.getStartDate(), item.getEndDate(), label, issues, false);
        }
        return pts;
    }

    private int checkSkills(List<Resume.Skill> skills, List<RefineSuggestion> issues) {
        if (skills.isEmpty()) {
            return add(issues, "missing_skills", "Skills", "", CFG.d("missingSkills"),
                "Add 8-12 concrete skills. ATS keyword matching depends heavily on this section.", "error");
        }
        int pts = 0;
        if (skills.size() < T.getTooFewSkillsCount()) {
            pts += add(issues, "too_few_skills", "Skills", String.valueOf(skills.size()), CFG.d("tooFewSkills"),
                "Only " + skills.size() + " skills are listed. Add enough hard skills to match the target role.", "warning");
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < skills.size(); i++) {
            Resume.Skill skill = skills.get(i);
            String label = "Skills " + (i + 1);
            String name  = safe(skill.getName());
            if (!hasText(name)) {
                pts += add(issues, "blank_skill", label + " > Name", "", CFG.d("blankSkill"),
                    "Remove the blank skill row or enter a specific skill name.", "error");
                continue;
            }
            String normalized = normalizeSkillName(name);
            if (!seen.add(normalized)) {
                pts += add(issues, "duplicate_skill", label + " > Name", name, CFG.d("duplicateSkill"),
                    "Remove duplicate skill listing: \"" + name + "\". Keep one clear normalized entry per skill to maintain a clean layout.", "tip");
            }
            pts += checkProgress(skill.getProgress(), label + " > Proficiency", issues);
        }
        return pts;
    }

    private String normalizeSkillName(String name) {
        String lower = name.trim().toLowerCase(Locale.ROOT);
        if ("js".equals(lower) || "javascript".equals(lower)) return "javascript";
        if ("ts".equals(lower) || "typescript".equals(lower)) return "typescript";
        if ("spring".equals(lower) || "spring boot".equals(lower) || "springboot".equals(lower)) return "spring boot";
        return lower;
    }

    private int checkProjects(List<Resume.Project> projects, List<Resume.WorkExperience> experience, List<RefineSuggestion> issues, String stage) {
        boolean isSeniorOrLeadOrManager = "Senior".equals(stage) || "Lead".equals(stage) || "Manager".equals(stage);
        if (isSeniorOrLeadOrManager && experience.size() >= 2) {
            return 0; // Never ask for projects if strong experience exists for seniors
        }
        if (projects.isEmpty() && experience.size() <= 1) {
            return add(issues, "missing_projects", "Projects", "", CFG.d("missingProjects"),
                "Add one or two strong projects to showcase hands-on work if your experience is light.", "warning");
        }
        int pts = 0;
        for (int i = 0; i < projects.size(); i++) {
            Resume.Project project = projects.get(i);
            String label = "Projects " + (i + 1);
            pts += requireText(issues, "missing_project_title", label + " > Title", project.getTitle(), CFG.d("missingProjectTitle"),
                "Add a concise project title.");
            String description = safe(project.getDescription());
            if (!hasText(description)) {
                pts += add(issues, "missing_project_description", label + " > Description", "", CFG.d("missingProjectDescription"),
                    "Add what the project does, what you used, and what improved.", "warning");
            } else {
                pts += checkContentQuality(description, label + " > Description", T.getMinProjectDescLen(), false, issues);
            }
            pts += checkOptionalUrl(project.getGithub(),  label + " > GitHub URL",   "Use a valid repository URL or leave the field empty.", issues);
            pts += checkOptionalUrl(project.getLiveDemo(), label + " > Live demo URL", "Use a valid live demo URL or leave the field empty.", issues);
        }
        return pts;
    }

    private int checkCertifications(List<Resume.Certification> certifications, List<RefineSuggestion> issues) {
        int pts = 0;
        for (int i = 0; i < certifications.size(); i++) {
            Resume.Certification cert = certifications.get(i);
            String label = "Certifications " + (i + 1);
            pts += requireText(issues, "missing_cert_title",  label + " > Title",  cert.getTitle(),  CFG.d("missingCertTitle"),  "Add the certification name or remove the blank certification row.");
            pts += requireText(issues, "missing_cert_issuer", label + " > Issuer", cert.getIssuer(), CFG.d("missingCertIssuer"), "Add the issuer so ATS and recruiters can verify the credential.");
            pts += checkYear(cert.getYear(), label + " > Year", issues);
        }
        return pts;
    }

    private int checkLanguages(List<Resume.Language> languages, List<RefineSuggestion> issues) {
        int pts = 0;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < languages.size(); i++) {
            Resume.Language language = languages.get(i);
            String label = "Languages " + (i + 1);
            String name  = safe(language.getName());
            if (!hasText(name)) {
                pts += add(issues, "blank_language", label + " > Name", "", CFG.d("blankLanguage"),
                    "Remove the blank language row or enter a language name.", "tip");
                continue;
            }
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                pts += add(issues, "duplicate_language", label + " > Name", name, CFG.d("duplicateLanguage"),
                    "Remove duplicate language entries.", "tip");
            }
            pts += checkProgress(language.getProgress(), label + " > Proficiency", issues);
        }
        return pts;
    }

    private int checkInterests(List<String> interests, List<RefineSuggestion> issues) {
        int pts = 0;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < interests.size(); i++) {
            String interest = safe(interests.get(i));
            String label    = "Interests " + (i + 1);
            if (!hasText(interest)) {
                pts += add(issues, "blank_interest", label, "", CFG.d("blankInterest"),
                    "Remove blank interest rows.", "tip");
                continue;
            }
            if (!seen.add(interest.toLowerCase(Locale.ROOT))) {
                pts += add(issues, "duplicate_interest", label, interest, CFG.d("duplicateInterest"),
                    "Remove duplicate interests.", "tip");
            }
        }
        if (interests.size() > T.getTooManyInterestsCount()) {
            pts += add(issues, "too_many_interests", "Interests", String.valueOf(interests.size()), CFG.d("tooManyInterests"),
                "Keep interests short or remove them for ATS-first resumes. Use the space for experience, skills, or projects.", "tip");
        }
        return pts;
    }

    private int checkPresentation(Resume resume, Resume.ProfileInfo profile, List<RefineSuggestion> issues) {
        int pts = 0;
        String template = resume.getTemplate() != null ? resume.getTemplate().name() : "template1";

        Map<String, Integer> templateRiskMap = CFG.getTemplateRisk();
        if (templateRiskMap != null) {
            Integer templateRisk = templateRiskMap.get(template);
            if (templateRisk != null) {
                String displayName = templateName(template);
                pts += add(issues, "template_parse_risk", "Customization > Template", displayName, templateRisk,
                    "For ATS uploads, use Classic or Minimal. Keep visual templates for human-facing PDFs.",
                    templateRisk >= 6 ? "warning" : "tip");
            }
        }

        String photoUrl   = safe(profile.getProfilePreviewUrl());
        Map<String, String> decoratives = resume.getDecoratives() != null ? resume.getDecoratives() : Collections.emptyMap();
        String photoShape = safe(decoratives.get("photoShape"));
        if (hasText(photoUrl) && !"none".equalsIgnoreCase(photoShape)) {
            pts += add(issues, "profile_photo_parse_risk", "Profile > Photo", "Photo attached", CFG.d("profilePhotoParseRisk"),
                "Remove the photo for ATS-first resumes. Photos are often ignored and can reduce parser consistency.", "warning");
        }
        String headerStyle = safe(decoratives.get("headerStyle"));
        if ("full-bleed".equalsIgnoreCase(headerStyle) || "card".equalsIgnoreCase(headerStyle)) {
            pts += add(issues, "decorative_header", "Customization > Header style", headerStyle, CFG.d("decorativeHeader"),
                "Use Minimal header style for ATS uploads. Decorative headers can change read order in some parsers.", "tip");
        }
        if ("true".equalsIgnoreCase(decoratives.get("sectionIcons"))) {
            pts += add(issues, "section_icons", "Customization > Section icons", "Enabled", CFG.d("sectionIcons"),
                "Disable section icons for ATS uploads. Icons can be parsed as stray characters.", "warning");
        }
        if ("true".equalsIgnoreCase(decoratives.get("sectionNumbers"))) {
            pts += add(issues, "section_numbers", "Customization > Section numbers", "Enabled", CFG.d("sectionNumbers"),
                "Disable section numbers if the resume is being uploaded to an ATS. Plain section headings parse cleaner.", "tip");
        }
        String dividerStyle = safe(decoratives.get("dividerStyle"));
        if ("dots".equalsIgnoreCase(dividerStyle) || "gradient".equalsIgnoreCase(dividerStyle)) {
            pts += add(issues, "decorative_divider", "Customization > Divider style", dividerStyle, CFG.d("decorativeDivider"),
                "Use a simple line divider or no divider for ATS uploads.", "tip");
        }
        String progressStyle = safe(decoratives.get("progressStyle"));
        if ("bar".equalsIgnoreCase(progressStyle) || "dots".equalsIgnoreCase(progressStyle)) {
            pts += add(issues, "visual_progress", "Customization > Skill progress style", progressStyle, CFG.d("visualProgress"),
                "ATS reads skill names, not bars or dots. Keep the skill names textual and do not rely on visual proficiency.", "tip");
        }
        if (hasText(resume.getFontPairing()) && !"inter".equalsIgnoreCase(resume.getFontPairing())) {
            pts += add(issues, "custom_font", "Customization > Font", resume.getFontPairing(), CFG.d("customFont"),
                "Use a common system-like font for ATS uploads. Keep custom fonts for human-facing versions.", "tip");
        }
        return pts;
    }

    private int checkKeywords(Resume resume, String category, List<RefineSuggestion> issues, String stage) {
        List<String> keywords = AtsKeywords.getCategoryKeywords().get(category);
        if (keywords == null || keywords.isEmpty()) return 0;
        String fullText = allText(resume).toLowerCase(Locale.ROOT);
        List<String> missing = new ArrayList<>();
        for (String keyword : keywords) {
            if (!fullText.contains(keyword.toLowerCase(Locale.ROOT))) missing.add(keyword);
        }
        if (missing.isEmpty()) return 0;

        missing = filterMissingKeywords(missing, fullText, category, stage, resume);
        if (missing.isEmpty()) return 0;

        double missRatio = (double) missing.size() / keywords.size();
        int pts = Math.max(CFG.d("keywordGapMin"), (int) Math.round(CFG.d("keywordGapMax") * missRatio));
        List<String> top = missing.subList(0, Math.min(T.getMaxKeywordDisplay(), missing.size()));
        
        String suggestion = "Including key technical terms for a " + category + " role helps parser matching. If you have experience with these adjacent concepts, consider adding them to your skills or project descriptions: " + String.join(", ", top) + ". Otherwise, focus on clarifying your core strengths.";

        issues.add(issue(
            "keyword_gap", "Role keywords",
            "Missing: " + String.join(", ", top),
            suggestion,
            pts >= 10 ? "warning" : "tip",
            pts
        ));
        return pts;
    }

    private int checkContentQuality(String text, String section, int minLength, boolean requireMetric, List<RefineSuggestion> issues) {
        int pts = 0;
        String value = safe(text);
        if (value.length() < minLength) {
            pts += add(issues, "short_description", section, value, CFG.d("shortDescription"),
                "Add more detail: action, tools, scope, and result.", "warning");
        }
        if (!ACTION_VERB.matcher(value).find()) {
            pts += add(issues, "missing_action_verb", section, trim(value, 120), CFG.d("missingActionVerb"),
                "Start at least one sentence or bullet with a strong action verb such as Built, Led, Improved, Reduced, or Automated.", "tip");
        }
        if (requireMetric && !HAS_METRIC.matcher(value).find()) {
            pts += add(issues, "missing_metric", section, trim(value, 120), CFG.d("missingMetric"),
                "Add a number or measurable result, for example: Reduced latency by 40% or Supported 10K users.", "warning");
        }
        if (PASSIVE_VOICE.matcher(value).find()) {
            pts += add(issues, "passive_voice", section, firstMatch(PASSIVE_VOICE, value), CFG.d("passiveVoice"),
                "Rewrite this in active voice. Use a direct action verb and state your impact.", "warning");
        }
        if (FILLER_WORDS.matcher(value).find()) {
            pts += add(issues, "filler_word", section, firstMatch(FILLER_WORDS, value), CFG.d("fillerWord"),
                "Remove vague wording and replace it with a concrete action or result.", "warning");
        }
        return pts;
    }

    private int checkDateRange(String start, String end, String section, List<RefineSuggestion> issues, boolean allowPresent) {
        int pts = 0;
        if (!hasText(start)) pts += add(issues, "missing_start_date", section + " > Start date", "", CFG.d("missingStartDate"), "Add a start month and year.", "warning");
        if (!hasText(end))   pts += add(issues, "missing_end_date",   section + " > End date",   "", CFG.d("missingEndDate"),   allowPresent ? "Add an end month/year or mark it as Present." : "Add an end month and year.", "warning");
        if (!hasText(start) || !hasText(end)) return pts;
        if ("present".equalsIgnoreCase(end)) {
            if (!allowPresent) pts += add(issues, "invalid_end_date", section + " > End date", end, CFG.d("invalidEndDate"), "Use a real end month and year for this section.", "warning");
            return pts;
        }
        Integer sv = parseMonthYear(start), ev = parseMonthYear(end);
        if (sv == null) pts += add(issues, "invalid_start_date", section + " > Start date", start, CFG.d("invalidStartDate"), "Use the editor date format: month plus four-digit year.", "warning");
        if (ev == null) pts += add(issues, "invalid_end_date",   section + " > End date",   end,   CFG.d("invalidEndDate"),   "Use the editor date format: month plus four-digit year.", "warning");
        if (sv != null && ev != null && sv > ev) pts += add(issues, "date_order", section + " > Dates", start + " to " + end, CFG.d("dateOrder"), "Start date is after end date. Correct the timeline before applying.", "error");
        return pts;
    }

    private int checkYear(String year, String section, List<RefineSuggestion> issues) {
        if (!hasText(year)) return add(issues, "missing_cert_year", section, "", CFG.d("missingCertYear"), "Add the completion year or remove the year field if unknown.", "tip");
        if (!year.matches("\\d{4}")) return add(issues, "invalid_year", section, year, CFG.d("invalidYear"), "Use a four-digit year.", "warning");
        int numericYear = Integer.parseInt(year);
        if (numericYear < 1950 || numericYear > CURRENT_YEAR + 10) return add(issues, "year_out_of_range", section, year, CFG.d("yearOutOfRange"), "Use a realistic year.", "warning");
        return 0;
    }

    private int checkProgress(Integer progress, String section, List<RefineSuggestion> issues) {
        if (progress == null)            return add(issues, "missing_progress", section, "",                    CFG.d("missingProgress"), "Set a proficiency value or remove the visual proficiency control for ATS-first resumes.", "tip");
        if (progress < 0 || progress > 100) return add(issues, "invalid_progress", section, String.valueOf(progress), CFG.d("invalidProgress"), "Keep proficiency between 0 and 100.", "tip");
        return 0;
    }

    private int checkOptionalUrl(String url, String section, String fix, List<RefineSuggestion> issues) {
        if (!hasText(url)) return 0;
        if (!URL.matcher(url).matches()) return add(issues, "invalid_url", section, url, CFG.d("invalidUrl"), fix, "tip");
        return 0;
    }

    private int requireText(List<RefineSuggestion> issues, String type, String section, String value, int points, String fix) {
        if (hasText(value)) return 0;
        return add(issues, type, section, "", points, fix, points >= 5 ? "error" : "warning");
    }

    private String formatRecruiterSuggestion(String type, String suggestion) {
        if ("keyword_gap".equals(type)) {
            return "Reason: Key technical role keywords are missing.\nWhy it matters: ATS algorithms utilize keyword frequencies to rank candidates.\nActionable improvement: " + suggestion + "\nRecruiter impact: Boosts search query match scores and technical indexing.\nExample: Include missing keywords in relevant project descriptions.";
        }
        if (suggestion != null && suggestion.contains("Reason:")) return suggestion;
        
        Map<String, RecruiterItem> formatted = new HashMap<>();
        formatted.put("missing_name", new RecruiterItem(
            "Full name is missing from the resume header.",
            "ATS parsers require a clear name identifier to create and index a candidate profile.",
            "Add your full legal or professional name at the very top of the page in a large, readable font.",
            "Allows recruiters to instantly find your candidate record and index your application.",
            "John Doe"
        ));
        formatted.put("missing_designation", new RecruiterItem(
            "Target role designation is missing.",
            "ATS systems and recruiters check this designation to map your profile to open roles.",
            "Add a target role title matching your career goal directly under your name.",
            "Aligns your resume with automated filters and recruiter role categories.",
            "Software Engineer"
        ));
        formatted.put("missing_summary", new RecruiterItem(
            "Professional summary is missing.",
            "A summary provides a high-level overview of your career trajectory and key value before recruiters dig into detail.",
            "Add a 2-4 sentence summary summarizing your target role, top strengths, and highest measurable impact.",
            "Reduces screening time and increases immediate engagement.",
            "Result-driven Software Engineer with 4 years of experience building high-scale Java APIs. Reduced latency by 20%."
        ));
        formatted.put("summary_no_metric", new RecruiterItem(
            "Your professional summary lacks quantifiable metrics or outcomes.",
            "Recruiters evaluate candidate strength using measurable achievements, not just static task lists.",
            "Add at least one numerical metric (e.g. team size, user count, optimization percentage, hackathon rank).",
            "Boosts credibility and demonstrates results-oriented work habits.",
            "Optimized SQL database indexing, reducing query runtimes by 30%."
        ));
        formatted.put("missing_email", new RecruiterItem(
            "Email address is missing.",
            "Email is the primary index contact key for automated system communications and scheduler integrations.",
            "Add a clean, professional email address to your header contact block.",
            "Enables instant automated interview invitations and follow-ups.",
            "alex.dev@gmail.com"
        ));
        formatted.put("missing_phone", new RecruiterItem(
            "Phone contact is missing.",
            "Recruiting teams use phone numbers for initial screeners and fast-track communications.",
            "Add your mobile phone number including country code to your header.",
            "Accelerates screening and scheduling touchpoints.",
            "+1 555-0100"
        ));
        formatted.put("missing_location", new RecruiterItem(
            "Candidate location is missing.",
            "ATS systems filter applications by region, tax jurisdiction, or relocation flags.",
            "Add your city and state/country to the contact header.",
            "Prevents automatic rejection by geographic routing rules.",
            "Boston, MA"
        ));
        formatted.put("missing_experience", new RecruiterItem(
            "No work experience or project experience is detected.",
            "ATS and recruiters look for proof of applied knowledge through jobs, projects, or internships.",
            "Add professional experience, custom internships, or capstone projects to show hands-on work.",
            "Fulfills the core requirement for technical validation.",
            "Add a section listing your capstone software project or technical internship."
        ));
        formatted.put("missing_skills", new RecruiterItem(
            "Skills section is missing or empty.",
            "Recruiters use skills sections to filter candidates on target keyword combinations.",
            "Add 8-12 core hard skills matching your target career role.",
            "Improves keyword relevance matching in search queries.",
            "Java, Spring Boot, React, SQL, Git, Docker"
        ));
        formatted.put("duplicate_skill", new RecruiterItem(
            "Duplicate or redundant skills detected.",
            "Listing the same skill multiple times (e.g. JS and JavaScript) looks disorganized and wastes space.",
            "Consolidate duplicate listings into one standard textual name.",
            "Shows professional attention to detail and saves valuable layout space.",
            "Consolidate 'JS' and 'JavaScript' into one entry: 'JavaScript'."
        ));

        RecruiterItem item = formatted.get(type);
        if (item != null) {
            return "Reason: " + item.reason + "\nWhy it matters: " + item.why + 
                   "\nActionable improvement: " + item.action + "\nRecruiter impact: " + item.impact + 
                   "\nExample: " + item.example;
        }

        return "Reason: " + suggestion + 
               "\nWhy it matters: Helps maintain layout quality, ATS readability, and recruiter compliance." + 
               "\nActionable improvement: Adjust this section to use clean text, valid URLs, or clear dates." + 
               "\nRecruiter impact: Reduces manual screening friction." + 
               "\nExample: Verify section completeness.";
    }

    private static class RecruiterItem {
        String reason;
        String why;
        String action;
        String impact;
        String example;

        RecruiterItem(String reason, String why, String action, String impact, String example) {
            this.reason = reason;
            this.why = why;
            this.action = action;
            this.impact = impact;
            this.example = example;
        }
    }

    private int add(List<RefineSuggestion> issues, String type, String section, String original, int points, String suggestion, String severity) {
        issues.add(issue(type, section, original, suggestion, severity, points));
        return points;
    }

    private RefineSuggestion issue(String type, String section, String original, String suggestion, String severity, int points) {
        String formatted = formatRecruiterSuggestion(type, suggestion);
        return RefineSuggestion.builder()
            .type(type).section(section).original(original)
            .suggestion(formatted).severity(severity).points(points)
            .build();
    }

    private String detectCareerStage(Resume resume) {
        String designation = safe(resume.getProfileInfo() != null ? resume.getProfileInfo().getDesignation() : "").toLowerCase(Locale.ROOT);
        String summary = safe(resume.getProfileInfo() != null ? resume.getProfileInfo().getSummary() : "").toLowerCase(Locale.ROOT);

        boolean isStudentTitle = designation.contains("student") || designation.contains("intern") || 
                                 designation.contains("undergrad") || designation.contains("candidate");
        
        boolean hasFutureEducation = false;
        if (resume.getEducation() != null) {
            for (Resume.Education edu : resume.getEducation()) {
                String end = safe(edu.getEndDate()).toLowerCase(Locale.ROOT);
                if (end.contains("present") || end.contains("expected")) {
                    hasFutureEducation = true;
                    break;
                }
                String[] parts = end.trim().split("\\s+");
                if (parts.length == 2 && parts[1].matches("\\d{4}")) {
                    int year = Integer.parseInt(parts[1]);
                    if (year >= CURRENT_YEAR) {
                        hasFutureEducation = true;
                        break;
                    }
                } else if (end.matches("\\d{4}")) {
                    int year = Integer.parseInt(end);
                    if (year >= CURRENT_YEAR) {
                        hasFutureEducation = true;
                        break;
                    }
                }
            }
        }

        if (isStudentTitle || hasFutureEducation) return "Student";
        if (designation.contains("fresher") || designation.contains("graduate")) return "Fresher";
        if (designation.contains("manager") || designation.contains("director") || designation.contains("vp") || 
            designation.contains("head") || designation.contains("pm")) return "Manager";
        if (designation.contains("lead") || designation.contains("coordinator")) return "Lead";
        if (designation.contains("senior") || designation.contains("sr.") || designation.contains("architect") || 
            designation.contains("principal")) return "Senior";
        if (designation.contains("junior") || designation.contains("jr.")) return "Junior";

        int expCount = resume.getWorkExperience() != null ? resume.getWorkExperience().size() : 0;
        int internshipCount = 0;
        if (resume.getCustomSections() != null && resume.getCustomSections().containsKey("internships")) {
            internshipCount = resume.getCustomSections().get("internships").size();
        }

        if (expCount == 0 && internshipCount == 0) return "Fresher";
        return "Mid-Level";
    }

    private String detectCareerPath(Resume resume, String designation, String fullText) {
        String lowerDes = designation.toLowerCase(Locale.ROOT);
        String lowerText = fullText.toLowerCase(Locale.ROOT);
        List<String> skills = new ArrayList<>();
        if (resume.getSkills() != null) {
            for (Resume.Skill s : resume.getSkills()) {
                skills.add(safe(s.getName()).toLowerCase(Locale.ROOT));
            }
        }

        Map<String, PathData> paths = new HashMap<>();
        paths.put("Backend Java", new PathData(
            List.of("java", "spring", "hibernate", "junit", "maven", "gradle", "jpa", "spring boot"),
            List.of("java", "spring boot", "spring", "hibernate", "jpa", "maven")
        ));
        paths.put("Frontend", new PathData(
            List.of("react", "typescript", "redux", "next.js", "nextjs", "vue", "angular", "css", "html", "figma", "ui/ux", "responsive design", "tailwind"),
            List.of("react", "typescript", "javascript", "vue", "angular", "css", "html", "tailwind", "next.js", "nextjs")
        ));
        paths.put("Machine Learning", new PathData(
            List.of("tensorflow", "pytorch", "scikit-learn", "pandas", "numpy", "deep learning", "keras", "machine learning", "nlp", "computer vision", "data science"),
            List.of("tensorflow", "pytorch", "scikit-learn", "pandas", "numpy", "machine learning")
        ));
        paths.put("DevOps", new PathData(
            List.of("docker", "kubernetes", "ci/cd", "terraform", "github actions", "jenkins", "aws", "azure", "gcp", "prometheus", "grafana", "ansible", "devops"),
            List.of("docker", "kubernetes", "terraform", "jenkins", "aws", "devops", "ci/cd")
        ));
        paths.put("QA", new PathData(
            List.of("selenium", "junit", "testng", "postman", "api testing", "cypress", "qa", "testing", "automation testing"),
            List.of("selenium", "junit", "testng", "postman", "cypress", "automation testing", "qa")
        ));
        paths.put("Data Analyst", new PathData(
            List.of("sql", "power bi", "excel", "tableau", "statistics", "pandas", "bi analyst", "etl", "data analyst", "data analytics"),
            List.of("sql", "power bi", "excel", "tableau", "data analyst", "pandas")
        ));

        String bestPath = "General Software Engineer";
        int maxScore = 0;

        for (Map.Entry<String, PathData> entry : paths.entrySet()) {
            int score = 0;
            PathData p = entry.getValue();
            for (String term : p.terms) {
                if (lowerDes.contains(term)) {
                    score += 12;
                }
            }
            for (String sk : skills) {
                if (p.skills.contains(sk)) {
                    score += 4;
                }
            }
            for (String term : p.terms) {
                if (lowerText.contains(term)) {
                    score += 1;
                }
            }
            if (score > maxScore) {
                maxScore = score;
                bestPath = entry.getKey();
            }
        }

        if (maxScore < 8) {
            return "General Software Engineer";
        }
        return bestPath;
    }

    private static class PathData {
        List<String> terms;
        List<String> skills;
        PathData(List<String> terms, List<String> skills) {
            this.terms = terms;
            this.skills = skills;
        }
    }

    private List<String> filterMissingKeywords(List<String> missing, String fullText, String category, String stage, Resume resume) {
        String lowerText = fullText.toLowerCase(Locale.ROOT);
        String designation = safe(resume.getProfileInfo() != null ? resume.getProfileInfo().getDesignation() : "");
        String careerPath = detectCareerPath(resume, designation, fullText);
        boolean isJuniorOrStudent = "Student".equals(stage) || "Fresher".equals(stage) || "Junior".equals(stage);
        
        List<String> filtered = new java.util.ArrayList<>();
        for (String kw : missing) {
            String kwLower = kw.toLowerCase(Locale.ROOT);
            
            // 1. General logical adjacency rules (apply to all paths)
            if ("spring boot".equals(kwLower) && !lowerText.contains("java")) {
                continue;
            }
            if ("typescript".equals(kwLower) && !lowerText.contains("react") && !lowerText.contains("javascript")) {
                continue;
            }
            if (List.of("pandas", "numpy", "scikit-learn", "tensorflow", "pytorch").contains(kwLower) &&
                !lowerText.contains("python") && !lowerText.contains("machine learning") && !lowerText.contains("ml")) {
                continue;
            }
            if ("kubernetes".equals(kwLower) && !lowerText.contains("docker")) {
                continue;
            }
            if ("microservices".equals(kwLower) && !lowerText.contains("rest api") && !lowerText.contains("backend") && !lowerText.contains("spring") && !lowerText.contains("node")) {
                continue;
            }

            // 2. Career Path filtering for "software engineer"
            if ("software engineer".equals(category.toLowerCase(Locale.ROOT))) {
                if ("Backend Java".equals(careerPath)) {
                    List<String> forbidden = List.of("react", "typescript", "vue", "angular", "figma", "sass", "less", "html", "css", "adobe xd");
                    if (forbidden.contains(kwLower) && !lowerText.contains("javascript") && !lowerText.contains("react")) {
                        continue;
                    }
                } else if ("Frontend".equals(careerPath)) {
                    List<String> forbidden = List.of("spring boot", "hibernate", "kubernetes", "docker", "microservices", "c++", "c#", "java");
                    if (forbidden.contains(kwLower) && !lowerText.contains("node") && !lowerText.contains("python") && !lowerText.contains("java")) {
                        continue;
                    }
                } else if ("Machine Learning".equals(careerPath)) {
                    List<String> forbidden = List.of("react", "typescript", "javascript", "docker", "kubernetes", "spring boot", "hibernate", "angular", "vue", "html", "css");
                    if (forbidden.contains(kwLower) && !lowerText.contains("mlops") && !lowerText.contains("docker")) {
                        continue;
                    }
                } else if ("DevOps".equals(careerPath)) {
                    List<String> forbidden = List.of("react", "typescript", "javascript", "spring boot", "hibernate", "angular", "vue", "html", "css");
                    if (forbidden.contains(kwLower)) {
                        continue;
                    }
                } else if ("QA".equals(careerPath)) {
                    List<String> forbidden = List.of("react", "typescript", "docker", "kubernetes", "spring boot", "hibernate", "microservices", "aws", "azure", "gcp");
                    if (forbidden.contains(kwLower)) {
                        continue;
                    }
                } else if ("Data Analyst".equals(careerPath)) {
                    List<String> forbidden = List.of("react", "typescript", "docker", "kubernetes", "spring boot", "hibernate", "microservices", "aws", "gcp", "azure");
                    if (forbidden.contains(kwLower)) {
                        continue;
                    }
                } else if ("General Software Engineer".equals(careerPath)) {
                    List<String> specialized = List.of("spring boot", "hibernate", "react", "typescript", "kubernetes", "tensorflow", "pytorch", "scikit-learn", "vue", "angular", "pandas", "numpy");
                    if (specialized.contains(kwLower)) {
                        continue;
                    }
                }
            }

            // 3. Junior/Student generic infrastructure filtering
            if (isJuniorOrStudent) {
                if (List.of("kubernetes", "docker", "microservices", "ci/cd", "aws", "system design").contains(kwLower)) {
                    List<String> infra = List.of("linux", "git", "rest api", "sql", "cloud", "backend");
                    boolean hasInfra = false;
                    for (String term : infra) {
                        if (lowerText.contains(term)) {
                            hasInfra = true;
                            break;
                        }
                    }
                    if (!hasInfra) {
                        continue;
                    }
                }
            }

            filtered.add(kw);
        }
        return filtered;
    }

    // ─── Full text collector for keyword matching ─────────────────────────────

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

    // ─── Utilities ────────────────────────────────────────────────────────────

    private Integer parseMonthYear(String value) {
        String str = safe(value).trim().toLowerCase(Locale.ROOT);
        str = str.replaceAll("(?i)^expected\\s+", "");
        String[] parts = str.split("\\s+");
        if (parts.length == 1 && parts[0].matches("\\d{4}")) {
            int year = Integer.parseInt(parts[0]);
            if (year < 1950 || year > CURRENT_YEAR + 10) return null;
            return year * 12 + 1; // default to Jan
        }
        if (parts.length == 2) {
            String mStr = parts[0].length() > 3 ? parts[0].substring(0, 3) : parts[0];
            Integer month = MONTHS.get(mStr);
            if (month == null || !parts[1].matches("\\d{4}")) return null;
            int year = Integer.parseInt(parts[1]);
            if (year < 1950 || year > CURRENT_YEAR + 10) return null;
            return year * 12 + month;
        }
        return null;
    }

    private String firstMatch(Pattern pattern, String text) {
        var matcher = pattern.matcher(safe(text));
        return matcher.find() ? matcher.group() : "";
    }

    private String feedback(int score, List<RefineSuggestion> issues, String category) {
        long errors   = issues.stream().filter(i -> "error".equals(i.getSeverity())).count();
        long warnings = issues.stream().filter(i -> "warning".equals(i.getSeverity())).count();
        String role   = category != null ? " for a " + category + " role" : "";
        if (score >= SB.getStrong())   return "Strong ATS-ready structure"  + role + ". Fix the remaining small parser risks before uploading.";
        if (score >= SB.getGood())     return "Good foundation"             + role + ". Address " + errors + " critical issue(s) and " + warnings + " warning(s) to reduce ATS risk.";
        if (score >= SB.getModerate()) return "Moderate ATS risk"           + role + ". Focus first on missing required fields, measurable experience, and parser-friendly layout.";
        return "High ATS risk" + role + ". Fill missing sections, simplify formatting, and rewrite weak bullets before applying.";
    }

    private int severityRank(String severity) {
        if ("error".equals(severity))   return 0;
        if ("warning".equals(severity)) return 1;
        return 2;
    }

    private String templateName(String template) {
        Map<String, String> names = CFG.getTemplateNames();
        return (names != null) ? names.getOrDefault(template, template) : template;
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

    private String safe(Object value) {
        if (value == null) return "";
        if (value instanceof String)       return (String) value;
        if (value instanceof java.util.Map) {
            Object val = ((java.util.Map<?, ?>) value).get("value");
            return val != null ? val.toString() : "";
        }
        return value.toString();
    }

    private <T> List<T> list(List<T> value) {
        return value == null ? Collections.emptyList() : value;
    }
}
