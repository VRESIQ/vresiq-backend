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
import java.util.regex.Matcher;

@Service
public class RefineService {

    private static final AtsConfig CFG  = AtsRulesLoader.getConfig();
    private static final AtsConfig.Thresholds T   = CFG.getThresholds();
    private static final AtsConfig.ScoreBands SB  = CFG.getScoreBands();
    private static final int CURRENT_YEAR          = Year.now().getValue();

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

    public static class JobIntel {
        public int index;
        public String company;
        public String role;
        public String startDate;
        public String endDate;
        public String description;
    }

    public static class EduIntel {
        public int index;
        public String degree;
        public String institution;
        public String startDate;
        public String endDate;
    }

    public static class SkillIntel {
        public String name;
        public Integer progress;
    }

    public static class ProjIntel {
        public int index;
        public String title;
        public String description;
        public String github;
        public String liveDemo;
    }

    public static class CertIntel {
        public int index;
        public String title;
        public String issuer;
        public String year;
    }

    public static class LangIntel {
        public String name;
        public Integer progress;
    }

    public static class ResumeIntelligence {
        public String stage;
        public double yearsOfExperience;
        public String targetRole;
        public String specialization;
        public boolean hasInternship;
        public boolean hasFreelancing;
        public boolean hasOpenSource;
        public boolean hasResearch;
        public boolean hasHackathons;
        public boolean hasLeadership;
        public boolean hasCertifications;
        public String projectMaturity;
        public String deploymentMaturity;
        public int achievementQuality;
        public int writingQuality;
        public int technicalDepth;
        public int atsCompatibility;
        
        public String fullName;
        public String designation;
        public String summaryText;
        public String email;
        public String phone;
        public String location;
        public String linkedIn;
        public String github;
        public String website;
        
        public List<JobIntel> jobs = new ArrayList<>();
        public List<EduIntel> educationList = new ArrayList<>();
        public List<SkillIntel> skillsList = new ArrayList<>();
        public List<ProjIntel> projectsList = new ArrayList<>();
        public List<CertIntel> certificationsList = new ArrayList<>();
        public List<LangIntel> languages = new ArrayList<>();
        public List<String> interests = new ArrayList<>();
        public String template;
        public String photoUrl;
        public Map<String, String> decoratives = new HashMap<>();
        public String fontPairing;
        public Resume rawResume;
    }

    public RefineResponse analyze(Resume resume) {
        ResumeIntelligence intel = analyzeResumeIntelligence(resume);
        List<RefineSuggestion> rawIssues = new ArrayList<>();

        checkProfile(intel, rawIssues);
        checkContact(intel, rawIssues);
        checkExperience(intel, rawIssues);
        checkEducation(intel, rawIssues);
        checkSkills(intel, rawIssues);
        checkProjects(intel, rawIssues);
        checkCertifications(intel, rawIssues);
        checkLanguages(intel, rawIssues);
        checkInterests(intel, rawIssues);
        checkPresentation(intel, rawIssues);

        boolean isMatchedRole = intel.specialization != null && !"General Resume".equals(intel.specialization);
        if (isMatchedRole) {
            checkKeywords(intel, intel.specialization.toLowerCase(Locale.ROOT), rawIssues);
        }

        List<RefineSuggestion> issues = optimizeRecommendations(rawIssues, intel);

        int deductions = 0;
        for (RefineSuggestion iss : issues) {
            deductions += iss.getPoints();
        }
        int score = Math.max(0, Math.min(100, 100 - deductions));

        String feedbackStr = feedback(score, issues, intel.specialization);

        List<String> strengths = new ArrayList<>();
        if (intel.yearsOfExperience >= 5) {
            strengths.add("Good technical progression & career stability");
        }
        if (intel.achievementQuality > 75) {
            strengths.add("Strong measurable achievements with business metrics");
        }
        if (intel.technicalDepth > 75) {
            strengths.add("Excellent project complexity & technical depth");
        }
        if (!"General Software Engineer".equals(intel.specialization) && !"General Resume".equals(intel.specialization)) {
            strengths.add("Clear specialization alignment: " + intel.specialization);
        }
        if (intel.atsCompatibility == 100) {
            strengths.add("Well-structured layout with optimal parser safety");
        }
        if ("High".equals(intel.projectMaturity)) {
            strengths.add("Excellent project complexity & technical depth");
        } else if ("Medium".equals(intel.projectMaturity)) {
            strengths.add("Solid project evidence across multiple builds");
        }
        if (intel.hasLeadership) {
            strengths.add("Leadership or coordination evidence is present");
        }
        if (intel.hasOpenSource) {
            strengths.add("Open-source or GitHub evidence is present");
        }
        if (strengths.size() < 3) {
            strengths.add("Well-structured ATS-ready formatting foundations");
            strengths.add("Clear visual categorization sections");
            strengths.add("Contact details are complete and readable");
        }

        return RefineResponse.builder()
            .atsScore(score).issues(issues)
            .overallFeedback(feedbackStr).category(intel.specialization)
            .strengths(strengths)
            .build();
    }

    public ResumeIntelligence analyzeResumeIntelligence(Resume resume) {
        String text = allText(resume).toLowerCase(Locale.ROOT);
        Resume.ProfileInfo profile = resume.getProfileInfo() != null ? resume.getProfileInfo() : new Resume.ProfileInfo();
        Resume.ContactInfo contact = resume.getContactInfo() != null ? resume.getContactInfo() : new Resume.ContactInfo();
        String designation = safe(profile.getDesignation());
        String summary = safe(profile.getSummary());
        
        ResumeIntelligence intel = new ResumeIntelligence();
        intel.stage = detectCareerStage(resume);
        intel.specialization = detectCareerPath(resume, designation, text);

        intel.hasInternship = (resume.getCustomSections() != null && resume.getCustomSections().containsKey("internships")) ||
            designation.toLowerCase(Locale.ROOT).contains("intern") ||
            list(resume.getWorkExperience()).stream().anyMatch(w -> safe(w.getRole()).toLowerCase(Locale.ROOT).contains("intern") || safe(w.getCompany()).toLowerCase(Locale.ROOT).contains("intern"));

        intel.hasFreelancing = text.contains("freelance") || text.contains("contractor") || text.contains("consultant");
        intel.hasOpenSource = text.contains("open source") || text.contains("contribution") || list(resume.getProjects()).stream().anyMatch(p -> hasText(p.getGithub()));
        intel.hasResearch = text.contains("research assistant") || text.contains("publication") || text.contains("research paper") || text.contains("scientific");
        intel.hasHackathons = text.contains("hackathon") || text.contains("competition") || text.contains("codefest");
        intel.hasLeadership = text.contains("lead ") || text.contains("manage") || text.contains("direct") || text.contains("mentor") || text.contains("orchestrated") || text.contains("supervised");
        intel.hasCertifications = !list(resume.getCertifications()).isEmpty();

        int totalMonths = 0;
        for (Resume.WorkExperience w : list(resume.getWorkExperience())) {
            Integer startVal = parseMonthYear(w.getStartDate());
            Integer endVal = w.getEndDate() != null && w.getEndDate().toLowerCase(Locale.ROOT).contains("present") ? CURRENT_YEAR * 12 + java.time.LocalDate.now().getMonthValue() : parseMonthYear(w.getEndDate());
            if (startVal != null && endVal != null && endVal >= startVal) {
                totalMonths += (endVal - startVal + 1);
            }
        }
        intel.yearsOfExperience = Math.round((totalMonths / 12.0) * 10) / 10.0;

        intel.projectMaturity = "Low";
        intel.deploymentMaturity = "None";
        if (list(resume.getProjects()).size() >= 2) intel.projectMaturity = "Medium";
        if (text.contains("architecture") || text.contains("system design") || text.contains("microservices")) intel.projectMaturity = "High";
        if (text.contains("aws") || text.contains("docker") || text.contains("kubernetes") || text.contains("deploy")) intel.deploymentMaturity = "Cloud";

        int metricsCount = 0;
        Matcher m = HAS_METRIC.matcher(text);
        while (m.find()) metricsCount++;

        intel.achievementQuality = Math.min(100, Math.max(40, 40 + metricsCount * 15));
        intel.writingQuality = 100;
        int fillerCount = 0;
        Matcher fm = FILLER_WORDS.matcher(text);
        while (fm.find()) fillerCount++;
        if (fillerCount > 0) intel.writingQuality = Math.max(40, 100 - fillerCount * 8);

        intel.technicalDepth = Math.min(100, Math.max(30, 30 + list(resume.getSkills()).size() * 5));
        intel.atsCompatibility = 100;

        intel.fullName = safe(profile.getFullName());
        intel.designation = designation;
        intel.summaryText = summary;
        intel.email = safe(contact.getEmail());
        intel.phone = safe(contact.getPhone());
        intel.location = safe(contact.getLocation());
        intel.linkedIn = safe(contact.getLinkedIn());
        intel.github = safe(contact.getGithub());
        intel.website = safe(contact.getWebsite());

        List<Resume.WorkExperience> jobsRaw = list(resume.getWorkExperience());
        for (int i = 0; i < jobsRaw.size(); i++) {
            Resume.WorkExperience w = jobsRaw.get(i);
            JobIntel j = new JobIntel();
            j.index = i + 1;
            j.company = safe(w.getCompany());
            j.role = safe(w.getRole());
            j.startDate = safe(w.getStartDate());
            j.endDate = safe(w.getEndDate());
            j.description = safe(w.getDescription());
            intel.jobs.add(j);
        }

        List<Resume.Education> eduRaw = list(resume.getEducation());
        for (int i = 0; i < eduRaw.size(); i++) {
            Resume.Education e = eduRaw.get(i);
            EduIntel ed = new EduIntel();
            ed.index = i + 1;
            ed.degree = safe(e.getDegree());
            ed.institution = safe(e.getInstitution());
            ed.startDate = safe(e.getStartDate());
            ed.endDate = safe(e.getEndDate());
            intel.educationList.add(ed);
        }

        for (Resume.Skill s : list(resume.getSkills())) {
            SkillIntel sk = new SkillIntel();
            sk.name = safe(s.getName());
            sk.progress = s.getProgress();
            intel.skillsList.add(sk);
        }

        List<Resume.Project> projRaw = list(resume.getProjects());
        for (int i = 0; i < projRaw.size(); i++) {
            Resume.Project p = projRaw.get(i);
            ProjIntel pr = new ProjIntel();
            pr.index = i + 1;
            pr.title = safe(p.getTitle());
            pr.description = safe(p.getDescription());
            pr.github = safe(p.getGithub());
            pr.liveDemo = safe(p.getLiveDemo());
            intel.projectsList.add(pr);
        }

        List<Resume.Certification> certRaw = list(resume.getCertifications());
        for (int i = 0; i < certRaw.size(); i++) {
            Resume.Certification c = certRaw.get(i);
            CertIntel ce = new CertIntel();
            ce.index = i + 1;
            ce.title = safe(c.getTitle());
            ce.issuer = safe(c.getIssuer());
            ce.year = safe(c.getYear());
            intel.certificationsList.add(ce);
        }

        for (Resume.Language l : list(resume.getLanguages())) {
            LangIntel la = new LangIntel();
            la.name = safe(l.getName());
            la.progress = l.getProgress();
            intel.languages.add(la);
        }

        for (String in : list(resume.getInterests())) {
            intel.interests.add(safe(in));
        }

        intel.template = resume.getTemplate() != null ? resume.getTemplate().name() : "template1";
        intel.photoUrl = safe(profile.getProfilePreviewUrl());
        if (resume.getDecoratives() != null) {
            for (Map.Entry<String, String> entry : resume.getDecoratives().entrySet()) {
                intel.decoratives.put(entry.getKey(), safe(entry.getValue()));
            }
        }
        intel.fontPairing = resume.getFontPairing();
        intel.rawResume = resume;
        return intel;
    }

    private int checkProfile(ResumeIntelligence intel, List<RefineSuggestion> issues) {
        int pts = 0;
        if (!hasText(intel.fullName)) {
            pts += issue(issues, "missing_name", "Profile > Full name", "", CFG.d("missingName"), "Add your professional or legal name at the top of your resume. ATS systems use this identifier to create your candidate profile.", "error", 100);
        }
        if (!hasText(intel.designation)) {
            pts += issue(issues, "missing_designation", "Profile > Designation", "", CFG.d("missingDesignation"), "Add a target designation title. This anchors your resume in the database and enables role-based keywords matching.", "error", 95);
        }

        String summary = intel.summaryText;
        if (!hasText(summary)) {
            String missingDesc = "Add a 2-4 sentence summary with target role, strengths, and measurable impact.";
            if ("Student".equals(intel.stage)) {
                missingDesc = "To stand out as a student, add academic projects, certifications, internships, hackathons, or measurable project outcomes to your summary.";
            } else if ("Fresher".equals(intel.stage)) {
                missingDesc = "As a fresher, showcase your internships, core technical strengths, and final-year project impact in your summary.";
            }
            pts += issue(issues, "missing_summary", "Profile > Summary", "", CFG.d("missingSummary"), missingDesc, "error", 90);
        } else {
            if (summary.length() < T.getMinSummaryLen()) {
                pts += issue(issues, "short_summary", "Profile > Summary", summary, CFG.d("shortSummary"), "Expand your summary to at least 80 characters. Describe your primary strengths, target role, and highest value achievement.", "warning", 80);
            }
            if (!HAS_METRIC.matcher(summary).find()) {
                String suggestion = "Add one concrete metric, such as users supported, team size, budget managed, or project outcomes.";
                if ("Student".equals(intel.stage)) {
                    suggestion = "To stand out as a student, add academic project outcomes, certifications, hackathon rankings, or GPA to your summary.";
                } else if ("Fresher".equals(intel.stage)) {
                    suggestion = "As a fresher, showcase your internships, core technical strengths, or final-year project outcomes in your summary.";
                }
                int conf = ("Student".equals(intel.stage) || "Fresher".equals(intel.stage)) ? 75 : 95;
                pts += issue(issues, "summary_no_metric", "Profile > Summary", trim(summary, 120), CFG.d("summaryNoMetric"), suggestion, "tip", conf);
            }
            if (FILLER_WORDS.matcher(summary).find()) {
                pts += issue(issues, "summary_filler", "Profile > Summary", firstMatch(FILLER_WORDS, summary), CFG.d("summaryFiller"), "Replace vague buzzwords (like 'hard-working' or 'passionate') with concrete achievements or technical skills.", "warning", 85);
            }
        }
        return pts;
    }

    private int checkContact(ResumeIntelligence intel, List<RefineSuggestion> issues) {
        int pts = 0;
        if (!hasText(intel.email)) {
            pts += issue(issues, "missing_email", "Contact > Email", "", CFG.d("missingEmail"), "Add a professional email address. ATS and recruiters need a direct contact field.", "error", 100);
        } else if (!EMAIL.matcher(intel.email).matches()) {
            pts += issue(issues, "invalid_email", "Contact > Email", intel.email, CFG.d("invalidEmail"), "Use a valid email format such as name@example.com.", "error", 100);
        }
        if (!hasText(intel.phone)) {
            pts += issue(issues, "missing_phone", "Contact > Phone", "", CFG.d("missingPhone"), "Add a phone number with country code.", "warning", 95);
        } else if (intel.phone.replaceAll("\\D", "").length() < 8) {
            pts += issue(issues, "invalid_phone", "Contact > Phone", intel.phone, CFG.d("invalidPhone"), "Use a complete phone number. Include country code and enough digits.", "warning", 95);
        }
        if (!hasText(intel.location)) {
            pts += issue(issues, "missing_location", "Contact > Location", "", CFG.d("missingLocation"), "Add city and country or region. Many ATS filters use location.", "warning", 85);
        }
        pts += optUrl(intel.linkedIn, "Contact > LinkedIn", "Use a full LinkedIn URL such as https://linkedin.com/in/username.", issues);
        pts += optUrl(intel.github, "Contact > GitHub", "Use a full GitHub URL such as https://github.com/username.", issues);
        pts += optUrl(intel.website, "Contact > Website", "Use a complete portfolio URL, including a valid domain.", issues);
        return pts;
    }

    private int checkExperience(ResumeIntelligence intel, List<RefineSuggestion> issues) {
        boolean bypass = intel.hasInternship || intel.hasFreelancing || intel.hasOpenSource || intel.hasResearch || intel.hasHackathons;
        if (intel.jobs.isEmpty()) {
            if (bypass) return 0;
            return issue(issues, "missing_experience", "Experience", "", CFG.d("missingExperience"), "Add at least one professional role, internship, or technical project to demonstrate hands-on application of your skills.", "error", 95);
        }
        int pts = 0;
        for (JobIntel job : intel.jobs) {
            String lbl = "Experience " + job.index;
            if (!hasText(job.company)) pts += issue(issues, "missing_company", lbl + " > Company", "", CFG.d("missingCompany"), "Add the company or organization name.", "error", 90);
            if (!hasText(job.role)) pts += issue(issues, "missing_role", lbl + " > Role", "", CFG.d("missingRole"), "Add the role title exactly as you want recruiters and ATS to read it.", "error", 90);
            pts += checkDateRange(job.startDate, job.endDate, lbl, issues, true);
            if (!hasText(job.description)) {
                pts += issue(issues, "missing_experience_description", lbl + " > Description", "", CFG.d("missingExperienceDescription"), "Add 2-4 bullets or sentences covering action, tools, and measurable impact.", "error", 95);
            } else {
                pts += checkQuality(job.description, lbl + " > Description", T.getMinExperienceDescLen(), true, issues, intel.stage);
            }
        }
        return pts;
    }

    private int checkEducation(ResumeIntelligence intel, List<RefineSuggestion> issues) {
        if (intel.educationList.isEmpty()) {
            return issue(issues, "missing_education", "Education", "", CFG.d("missingEducation"), "Add education, training, bootcamp, or equivalent credential. If not applicable, add your strongest formal training.", "warning", 90);
        }
        int pts = 0;
        for (EduIntel edu : intel.educationList) {
            String lbl = "Education " + edu.index;
            if (!hasText(edu.degree)) pts += issue(issues, "missing_degree", lbl + " > Degree", "", CFG.d("missingDegree"), "Add the degree, certificate, or course name.", "error", 90);
            if (!hasText(edu.institution)) pts += issue(issues, "missing_institution", lbl + " > Institution", "", CFG.d("missingInstitution"), "Add the school, university, or training provider.", "error", 90);
            pts += checkDateRange(edu.startDate, edu.endDate, lbl, issues, false);
        }
        return pts;
    }

    private int checkSkills(ResumeIntelligence intel, List<RefineSuggestion> issues) {
        if (intel.skillsList.isEmpty()) return issue(issues, "missing_skills", "Skills", "", CFG.d("missingSkills"), "Add 8-12 concrete skills. ATS keyword matching depends heavily on this section.", "error", 100);
        int pts = 0;
        if (intel.skillsList.size() < T.getTooFewSkillsCount()) {
            pts += issue(issues, "too_few_skills", "Skills", String.valueOf(intel.skillsList.size()), CFG.d("tooFewSkills"), "Only " + intel.skillsList.size() + " skills are listed. Add enough hard skills to match the target role.", "warning", 85);
        }
        Set<String> seen = new HashSet<>();
        for (int idx = 0; idx < intel.skillsList.size(); idx++) {
            SkillIntel sk = intel.skillsList.get(idx);
            String lbl = "Skills " + (idx + 1);
            if (!hasText(sk.name)) { pts += issue(issues, "blank_skill", lbl + " > Name", "", CFG.d("blankSkill"), "Remove the blank skill row or enter a specific skill name.", "error", 95); continue; }
            String norm = normalizeSkillName(sk.name);
            if (seen.contains(norm)) {
                pts += issue(issues, "duplicate_skill", lbl + " > Name", sk.name, CFG.d("duplicateSkill"), "Remove duplicate skill listing: \"" + sk.name + "\". Keep one clear normalized entry per skill to maintain a clean layout.", "tip", 95);
            }
            seen.add(norm);
            pts += checkProgress(sk.progress, lbl + " > Proficiency", issues);
        }
        return pts;
    }

    private int checkProjects(ResumeIntelligence intel, List<RefineSuggestion> issues) {
        boolean isSeniorOrLeadOrManager = "Senior".equals(intel.stage) || "Lead".equals(intel.stage) || "Manager".equals(intel.stage);
        if (isSeniorOrLeadOrManager && intel.jobs.size() >= 2) {
            return 0;
        }
        if (intel.projectsList.isEmpty() && intel.jobs.size() <= 1) {
            return issue(issues, "missing_projects", "Projects", "", CFG.d("missingProjects"), "Add one or two strong projects to showcase hands-on work if your experience is light.", "warning", 90);
        }
        int pts = 0;
        for (ProjIntel proj : intel.projectsList) {
            String lbl = "Projects " + proj.index;
            if (!hasText(proj.title)) pts += issue(issues, "missing_project_title", lbl + " > Title", "", CFG.d("missingProjectTitle"), "Add a concise project title.", "error", 90);
            if (!hasText(proj.description)) {
                pts += issue(issues, "missing_project_description", lbl + " > Description", "", CFG.d("missingProjectDescription"), "Add what the project does, what you used, and what improved.", "warning", 90);
            } else {
                pts += checkQuality(proj.description, lbl + " > Description", T.getMinProjectDescLen(), false, issues, intel.stage);
                
                String lowerDesc = proj.description.toLowerCase(Locale.ROOT);
                if (!lowerDesc.contains("architecture") && !lowerDesc.contains("design") && !lowerDesc.contains("built") && !lowerDesc.contains("implement")) {
                    pts += issue(issues, "project_architecture_weak", lbl + " > Description", trim(proj.description, 120), 0,
                      "Project details lack architectural or implementation depth. Clarify the problem statement and design choices.", "tip", 80);
                }
                if (!lowerDesc.contains("deploy") && !lowerDesc.contains("aws") && !lowerDesc.contains("docker") && !lowerDesc.contains("kubernetes") && !lowerDesc.contains("cloud") && !lowerDesc.contains("vercel") && !lowerDesc.contains("github actions")) {
                    pts += issue(issues, "project_deployment_missing", lbl + " > Description", trim(proj.description, 120), 0,
                      "Missing details about deployment or cloud environment. Document how the application is built, deployed, or hosted.", "tip", 80);
                }
            }
            pts += optUrl(proj.github,  lbl + " > GitHub URL",   "Use a valid repository URL or leave the field empty.", issues);
            pts += optUrl(proj.liveDemo,lbl + " > Live demo URL", "Use a valid live demo URL or leave the field empty.", issues);
        }
        return pts;
    }

    private int checkCertifications(ResumeIntelligence intel, List<RefineSuggestion> issues) {
        int pts = 0;
        for (CertIntel c : intel.certificationsList) {
            String lbl = "Certifications " + c.index;
            if (!hasText(c.title)) pts += issue(issues, "missing_cert_title",  lbl + " > Title",  "",  CFG.d("missingCertTitle"),  "Add the certification name or remove the blank certification row.", "error", 90);
            if (!hasText(c.issuer)) pts += issue(issues, "missing_cert_issuer", lbl + " > Issuer", "", CFG.d("missingCertIssuer"), "Add the issuer so ATS and recruiters can verify the credential.", "error", 90);
            pts += checkYear(c.year, lbl + " > Year", issues);
        }
        return pts;
    }

    private int checkLanguages(ResumeIntelligence intel, List<RefineSuggestion> issues) {
        int pts = 0;
        Set<String> seen = new HashSet<>();
        for (int idx = 0; idx < intel.languages.size(); idx++) {
            LangIntel lang = intel.languages.get(idx);
            String lbl = "Languages " + (idx + 1);
            if (!hasText(lang.name)) { pts += issue(issues, "blank_language", lbl + " > Name", "", CFG.d("blankLanguage"), "Remove the blank language row or enter a language name.", "tip", 80); continue; }
            if (seen.contains(lang.name.toLowerCase(Locale.ROOT))) pts += issue(issues, "duplicate_language", lbl + " > Name", lang.name, CFG.d("duplicateLanguage"), "Remove duplicate language entries.", "tip", 80);
            seen.add(lang.name.toLowerCase(Locale.ROOT));
            pts += checkProgress(lang.progress, lbl + " > Proficiency", issues);
        }
        return pts;
    }

    private int checkInterests(ResumeIntelligence intel, List<RefineSuggestion> issues) {
        int pts = 0;
        Set<String> seen = new HashSet<>();
        for (int idx = 0; idx < intel.interests.size(); idx++) {
            String interest = intel.interests.get(idx);
            String lbl = "Interests " + (idx + 1);
            if (!hasText(interest)) { pts += issue(issues, "blank_interest", lbl, "", CFG.d("blankInterest"), "Remove blank interest rows.", "tip", 80); continue; }
            if (seen.contains(interest.toLowerCase(Locale.ROOT))) pts += issue(issues, "duplicate_interest", lbl, interest, CFG.d("duplicateInterest"), "Remove duplicate interests.", "tip", 80);
            seen.add(interest.toLowerCase(Locale.ROOT));
        }
        if (intel.interests.size() > T.getTooManyInterestsCount()) {
            pts += issue(issues, "too_many_interests", "Interests", String.valueOf(intel.interests.size()), CFG.d("tooManyInterests"),
              "Keep interests short or remove them for ATS-first resumes. Use the space for experience, skills, or projects.", "tip", 80);
        }
        return pts;
    }

    private int checkPresentation(ResumeIntelligence intel, List<RefineSuggestion> issues) {
        int pts = 0;
        String template = intel.template;
        Integer risk = CFG.getTemplateRisk() != null ? CFG.getTemplateRisk().get(template) : null;
        if (risk != null) {
            String name = CFG.getTemplateNames() != null ? CFG.getTemplateNames().getOrDefault(template, template) : template;
            pts += issue(issues, "template_parse_risk", "Customization > Template", name, risk,
              "For ATS uploads, use Classic or Minimal. Keep visual templates for human-facing PDFs.", risk >= 6 ? "warning" : "tip", 80);
        }
        String photoUrl = intel.photoUrl;
        String photoShape = safe(intel.decoratives.get("photoShape"));
        if (hasText(photoUrl) && !"none".equals(photoShape)) {
            pts += issue(issues, "profile_photo_parse_risk", "Profile > Photo", "Photo attached", CFG.d("profilePhotoParseRisk"),
              "Remove the photo for ATS-first resumes. Photos are often ignored and can reduce parser consistency.", "warning", 90);
        }
        String headerStyle = safe(intel.decoratives.get("headerStyle"));
        if ("full-bleed".equals(headerStyle) || "card".equals(headerStyle)) {
            pts += issue(issues, "decorative_header", "Customization > Header style", headerStyle, CFG.d("decorativeHeader"),
              "Use Minimal header style for ATS uploads. Decorative headers can change read order in some parsers.", "tip", 80);
        }
        if ("true".equals(intel.decoratives.get("sectionIcons"))) {
            pts += issue(issues, "section_icons", "Customization > Section icons", "Enabled", CFG.d("sectionIcons"),
              "Disable section icons for ATS uploads. Icons can be parsed as stray characters.", "warning", 80);
        }
        if ("true".equals(intel.decoratives.get("sectionNumbers"))) {
            pts += issue(issues, "section_numbers", "Customization > Section numbers", "Enabled", CFG.d("sectionNumbers"),
              "Disable section numbers if the resume is being uploaded to an ATS. Plain section headings parse cleaner.", "tip", 80);
        }
        String divStyle = safe(intel.decoratives.get("dividerStyle"));
        if ("dots".equals(divStyle) || "gradient".equals(divStyle)) {
            pts += issue(issues, "decorative_divider", "Customization > Divider style", divStyle, CFG.d("decorativeDivider"),
              "Use a simple line divider or no divider for ATS uploads.", "tip", 80);
        }
        String progStyle = safe(intel.decoratives.get("progressStyle"));
        if ("bar".equals(progStyle) || "dots".equals(progStyle)) {
            pts += issue(issues, "visual_progress", "Customization > Skill progress style", progStyle, CFG.d("visualProgress"),
              "ATS reads skill names, not bars or dots. Keep the skill names textual and do not rely on visual proficiency.", "tip", 80);
        }
        if (hasText(intel.fontPairing) && !"inter".equals(intel.fontPairing)) {
            pts += issue(issues, "custom_font", "Customization > Font", intel.fontPairing, CFG.d("customFont"),
              "Use a common system-like font for ATS uploads. Keep custom fonts for human-facing versions.", "tip", 80);
        }
        return pts;
    }

    private int checkKeywords(ResumeIntelligence intel, String category, List<RefineSuggestion> issues) {
        List<String> keywords = CFG.getCategories() != null ? CFG.getCategories().get(category) : null;
        if (keywords == null || keywords.isEmpty()) return 0;
        String fullText = allText(intel.rawResume).toLowerCase(Locale.ROOT);
        List<String> missing = new ArrayList<>();
        for (String kw : keywords) {
            if (!fullText.contains(kw.toLowerCase(Locale.ROOT))) {
                missing.add(kw);
            }
        }
        if (missing.isEmpty()) return 0;

        missing = filterMissingKeywords(missing, fullText, category, intel.stage, intel.rawResume);
        if (missing.isEmpty()) return 0;

        double ratio = (double) missing.size() / keywords.size();
        int pts = (int) Math.max(CFG.d("keywordGapMin"), Math.round(CFG.d("keywordGapMax") * ratio));
        int maxDisplay = T.getMaxKeywordDisplay();
        List<String> top = missing.subList(0, Math.min(maxDisplay, missing.size()));
        
        String suggestion = "Including key technical terms for a " + category + " role helps parser matching. If you have experience with these adjacent concepts, consider adding them to your skills or project descriptions: " + String.join(", ", top) + ". Otherwise, focus on clarifying your core strengths.";

        return issue(issues, "keyword_gap", "Role keywords", "Missing: " + String.join(", ", top), pts, suggestion, pts >= 10 ? "warning" : "tip", 90);
    }

    // ─── Helpers & Date Utilities ────────────────────────────────────────────────

    private int checkDateRange(String start, String end, String section, List<RefineSuggestion> issues, boolean allowPresent) {
        int pts = 0;
        if (!hasText(start)) pts += issue(issues, "missing_start_date", section + " > Start date", "", CFG.d("missingStartDate"), "Add a start month and year.", "warning", 90);
        if (!hasText(end))   pts += issue(issues, "missing_end_date",   section + " > End date",   "", CFG.d("missingEndDate"),   allowPresent ? "Add an end month/year or mark it as Present." : "Add an end month and year.", "warning", 90);
        if (!hasText(start) || !hasText(end)) return pts;
        if ("present".equalsIgnoreCase(end)) {
            if (!allowPresent) pts += issue(issues, "invalid_end_date", section + " > End date", end, CFG.d("invalidEndDate"), "Use a real end month and year for this section.", "warning", 90);
            return pts;
        }
        Integer sv = parseMonthYear(start), ev = parseMonthYear(end);
        if (sv == null) pts += issue(issues, "invalid_start_date", section + " > Start date", start, CFG.d("invalidStartDate"), "Use the editor date format: month plus four-digit year.", "warning", 85);
        if (ev == null) pts += issue(issues, "invalid_end_date",   section + " > End date",   end,   CFG.d("invalidEndDate"),   "Use the editor date format: month plus four-digit year.", "warning", 85);
        if (sv != null && ev != null && sv > ev) pts += issue(issues, "date_order", section + " > Dates", start + " to " + end, CFG.d("dateOrder"), "Start date is after end date. Correct the timeline before applying.", "error", 95);
        return pts;
    }

    private int checkQuality(String text, String section, int minLen, boolean requireMetric, List<RefineSuggestion> issues, String stage) {
        int pts = 0;
        String v = safe(text);
        if (v.length() < minLen)              pts += issue(issues, "short_description",   section, v,                          CFG.d("shortDescription"),  "Add more detail: action, tools, scope, and result.", "warning", 80);
        if (!ACTION_VERB.matcher(v).find())   pts += issue(issues, "missing_action_verb", section, trim(v, 120),                   CFG.d("missingActionVerb"), "Start at least one sentence or bullet with a strong action verb such as Built, Led, Improved, Reduced, or Automated.", "tip", 85);
        
        if (requireMetric && !HAS_METRIC.matcher(v).find()) {
            int conf = ("Student".equals(stage) || "Fresher".equals(stage)) ? 75 : 95;
            pts += issue(issues, "missing_metric", section, trim(v, 120),             CFG.d("missingMetric"),     "Add a number or measurable result, for example: Reduced latency by 40% or Supported 10K users.", "warning", conf);
        }
        
        if (PASSIVE_VOICE.matcher(v).find())  pts += issue(issues, "passive_voice",        section, firstMatch(PASSIVE_VOICE,v), CFG.d("passiveVoice"),      "Rewrite this in active voice. Use a direct action verb and state your impact.", "warning", 90);
        if (FILLER_WORDS.matcher(v).find())   pts += issue(issues, "filler_word",          section, firstMatch(FILLER_WORDS,v), CFG.d("fillerWord"),        "Remove vague wording and replace it with a concrete action or result.", "warning", 85);
        return pts;
    }

    private int checkProgress(Integer progress, String section, List<RefineSuggestion> issues) {
        if (progress == null) return issue(issues, "missing_progress", section, "", CFG.d("missingProgress"), "Set a proficiency value or remove the visual proficiency control for ATS-first resumes.", "tip", 80);
        if (progress < 0 || progress > 100) return issue(issues, "invalid_progress", section, String.valueOf(progress), CFG.d("invalidProgress"), "Keep proficiency between 0 and 100.", "tip", 80);
        return 0;
    }

    private int checkYear(String year, String section, List<RefineSuggestion> issues) {
        if (!hasText(year)) return issue(issues, "missing_cert_year", section, "", CFG.d("missingCertYear"), "Add the completion year or remove the year field if unknown.", "tip", 80);
        if (!year.matches("\\d{4}")) return issue(issues, "invalid_year", section, year, CFG.d("invalidYear"), "Use a four-digit year.", "warning", 85);
        int n = Integer.parseInt(year);
        if (n < 1950 || n > CURRENT_YEAR + 10) return issue(issues, "year_out_of_range", section, year, CFG.d("yearOutOfRange"), "Use a realistic year.", "warning", 85);
        return 0;
    }

    private int optUrl(String value, String section, String fix, List<RefineSuggestion> issues) {
        String url = safe(value);
        if (!hasText(url)) return 0;
        if (!URL.matcher(url).matches()) return issue(issues, "invalid_url", section, url, CFG.d("invalidUrl"), fix, "tip", 90);
        return 0;
    }

    private int issue(List<RefineSuggestion> issues, String type, String section, String original, int points, String suggestion, String severity, int confidence) {
        if (confidence < 70) return 0;
        issues.add(RefineSuggestion.builder()
            .type(type).section(section).original(original)
            .suggestion(suggestion)
            .severity(severity).points(points).confidence(confidence)
            .build());
        return points;
    }

    private String formatRecruiterSuggestion(String type, String section, String original, int points, String suggestion, String severity, String evidence, Map<String, Integer> impact, List<String> affected, String example) {
        StringBuilder res = new StringBuilder();
        res.append("Reason: ").append(suggestion).append("\n");
        if (evidence != null && !evidence.isEmpty()) {
            res.append("Evidence: ").append(evidence).append("\n");
        }
        res.append("Impact: ATS +").append(impact.get("ats"))
           .append(", Recruiter +").append(impact.get("recruiter"))
           .append(", Interview +").append(impact.get("interview")).append("\n");
        res.append("Actionable improvement: Adjust this section to improve layout quality and recruiter readability.\n");
        if (affected != null && !affected.isEmpty()) {
            res.append("Affected items: ").append(String.join(", ", affected)).append("\n");
        }
        if (example != null && !example.isEmpty()) {
            res.append("Example: ").append(example).append("\n");
        }
        return res.toString();
    }

    private boolean isDeployedProject(ProjIntel proj) {
        String text = (safe(proj.title) + " " + safe(proj.description) + " " + safe(proj.github) + " " + safe(proj.liveDemo)).toLowerCase(Locale.ROOT);
        return List.of("deploy", "deployed", "render", "vercel", "netlify", "aws", "gcp", "azure", "docker", "kubernetes", "railway", "heroku")
            .stream().anyMatch(text::contains);
    }

    private List<RefineSuggestion> optimizeRecommendations(List<RefineSuggestion> rawIssues, ResumeIntelligence intel) {
        Map<String, List<RefineSuggestion>> grouped = new HashMap<>();
        Set<String> singleTypes = Set.of("missing_name", "missing_designation", "missing_email", "missing_phone", "missing_location", "missing_summary", "missing_education", "missing_skills", "missing_projects", "missing_experience", "template_parse_risk", "profile_photo_parse_risk", "keyword_gap");
        
        List<RefineSuggestion> mergedIssues = new ArrayList<>();

        for (RefineSuggestion iss : rawIssues) {
            if (singleTypes.contains(iss.getType())) {
                mergedIssues.add(iss);
            } else {
                grouped.computeIfAbsent(iss.getType(), k -> new ArrayList<>()).add(iss);
            }
        }

        for (Map.Entry<String, List<RefineSuggestion>> entry : grouped.entrySet()) {
            String type = entry.getKey();
            List<RefineSuggestion> list = entry.getValue();
            if (list.size() == 1) {
                mergedIssues.add(list.get(0));
            } else {
                RefineSuggestion firstIss = list.get(0);
                List<String> sections = new ArrayList<>();
                int sumPoints = 0;
                int sumConf = 0;
                for (RefineSuggestion i : list) {
                    sections.add(i.getSection());
                    sumPoints += i.getPoints();
                    sumConf += i.getConfidence();
                }
                sumPoints = Math.min(15, sumPoints);
                int avgConf = Math.round((float) sumConf / list.size());
                
                String groupedSuggestion = firstIss.getSuggestion();
                if ("missing_metric".equals(type)) {
                    groupedSuggestion = "Quantifiable metrics are missing across multiple experience/project bullet points. Add measurable numbers (revenue, users, percentages) to prove your impact.";
                } else if ("missing_action_verb".equals(type)) {
                    groupedSuggestion = "Multiple descriptive sentences do not start with action verbs. Replace passive statements with direct action verbs like Built, Led, or Optimized.";
                } else if ("passive_voice".equals(type)) {
                    groupedSuggestion = "Passive voice phrasing detected across multiple lines. Rewrite using active verbs.";
                } else if ("filler_word".equals(type)) {
                    groupedSuggestion = "Redundant filler buzzwords are listed multiple times. Simplify statements to maintain professional tone.";
                }
                
                mergedIssues.add(RefineSuggestion.builder()
                    .type(firstIss.getType())
                    .section(firstIss.getSection().split(" ")[0])
                    .original("")
                    .suggestion(groupedSuggestion)
                    .severity(firstIss.getSeverity())
                    .points(sumPoints)
                    .confidence(avgConf)
                    .build());
            }
        }

        // Prioritization sorting (stable lexicographical)
        mergedIssues.sort((a, b) -> {
            int rA = severityRank(a.getSeverity());
            int rB = severityRank(b.getSeverity());
            if (rA != rB) return Integer.compare(rA, rB);
            int p = Integer.compare(b.getPoints(), a.getPoints());
            if (p != 0) return p;
            int t = a.getType().compareTo(b.getType());
            if (t != 0) return t;
            return a.getSection().compareTo(b.getSection());
        });

        // Anti-Spam Gate constraints
        List<RefineSuggestion> errors = new ArrayList<>();
        List<RefineSuggestion> warnings = new ArrayList<>();
        List<RefineSuggestion> tips = new ArrayList<>();

        for (RefineSuggestion iss : mergedIssues) {
            if ("error".equals(iss.getSeverity())) {
                if (errors.size() < 2) errors.add(iss);
            } else if ("warning".equals(iss.getSeverity())) {
                if (warnings.size() < 3) warnings.add(iss);
            } else {
                if (tips.size() < 3) tips.add(iss);
            }
        }

        List<RefineSuggestion> capped = new ArrayList<>();
        capped.addAll(errors);
        capped.addAll(warnings);
        capped.addAll(tips);

        if (capped.size() > 8) {
            capped = capped.subList(0, 8);
        }

        // Late formatting mapping
        List<RefineSuggestion> finalSuggestions = new ArrayList<>();
        Set<String> seenAdvice = new HashSet<>();
        for (RefineSuggestion iss : capped) {
            String evidence = "";
            Map<String, Integer> impact = Map.of("ats", 1, "recruiter", 3, "interview", 3);
            String exampleText = "";
            
            boolean isGrouped = (iss.getOriginal() == null || iss.getOriginal().isEmpty()) 
                && List.of("missing_metric", "missing_action_verb", "passive_voice", "filler_word").contains(iss.getType());
            String adviceKey = iss.getType() + "|" + iss.getSection() + "|" + safe(iss.getSuggestion()).toLowerCase(Locale.ROOT);
            if (!seenAdvice.add(adviceKey)) {
                continue;
            }
            int confidenceGate = "error".equals(iss.getSeverity()) ? 70 : "warning".equals(iss.getSeverity()) ? 80 : 85;
            if (iss.getConfidence() == null || iss.getConfidence() < confidenceGate) {
                continue;
            }

            if ("keyword_gap".equals(iss.getType())) {
                evidence = "Resume matches " + intel.targetRole + " profile but lacks adjacent stack terms";
                impact = Map.of("ats", 5, "recruiter", 4, "interview", 3);
                ProjIntel proj = intel.projectsList.isEmpty() ? null : intel.projectsList.get(0);
                exampleText = (proj != null && !proj.title.isEmpty()) ? proj.title + " - Included only adjacent stack terms already reflected in the project context." : "Include only stack terms that reflect real project work.";
            } else if ("missing_metric".equals(iss.getType())) {
                if (isGrouped) {
                    evidence = "Multiple occurrences of missing_metric layout error";
                    impact = Map.of("ats", 2, "recruiter", 5, "interview", 5);
                } else {
                    evidence = "Bullets contain descriptive text but lack numerical symbols (%)";
                    impact = Map.of("ats", 2, "recruiter", 5, "interview", 5);
                    JobIntel job = intel.jobs.isEmpty() ? null : intel.jobs.get(0);
                    exampleText = (job != null && !job.company.isEmpty()) ? job.company + " - Reduced API latency by 30%." : "Improved inference runtime by 25%.";
                }
            } else if ("project_deployment_missing".equals(iss.getType())) {
                boolean alreadyDeployed = intel.projectsList.stream().anyMatch(this::isDeployedProject);
                if (alreadyDeployed) {
                    continue;
                }
                evidence = "Project descriptions do not include deployment or hosting details";
                impact = Map.of("ats", 1, "recruiter", 4, "interview", 5);
                ProjIntel proj = intel.projectsList.isEmpty() ? null : intel.projectsList.get(0);
                exampleText = (proj != null && !proj.title.isEmpty()) ? proj.title + " - Deployed on Render with a production build pipeline." : "Deployed application on AWS using GitHub Actions.";
            } else if ("project_architecture_weak".equals(iss.getType())) {
                evidence = "Project description does not describe engineering pattern details";
                impact = Map.of("ats", 1, "recruiter", 3, "interview", 4);
                ProjIntel proj = intel.projectsList.isEmpty() ? null : intel.projectsList.get(0);
                exampleText = (proj != null && !proj.title.isEmpty()) ? proj.title + " - Designed a multi-tier microservices architecture." : "Designed system using Event-Driven pattern.";
            } else if (iss.getType().startsWith("missing_")) {
                evidence = "Required section field [" + iss.getSection() + "] is blank";
                impact = Map.of("ats", 5, "recruiter", 5, "interview", 2);
            } else {
                if (isGrouped) {
                    evidence = "Multiple occurrences of " + iss.getType() + " layout error";
                    impact = Map.of("ats", 2, "recruiter", 5, "interview", 5);
                } else {
                    evidence = "Parser warning flag triggered on layout check";
                    impact = Map.of("ats", 3, "recruiter", 2, "interview", 1);
                }
            }

            List<String> affected = new ArrayList<>();
            if (isGrouped) {
                // Locate the original raw issues of this type to retrieve their sections
                for (RefineSuggestion raw : rawIssues) {
                    if (raw.getType().equals(iss.getType())) {
                        affected.add(raw.getSection());
                    }
                }
            }

            String formatted = formatRecruiterSuggestion(
                iss.getType(),
                iss.getSection(),
                iss.getOriginal(),
                iss.getPoints(),
                iss.getSuggestion(),
                iss.getSeverity(),
                evidence,
                impact,
                affected,
                exampleText
            );

            finalSuggestions.add(RefineSuggestion.builder()
                .type(iss.getType())
                .section(iss.getSection())
                .original(iss.getOriginal())
                .suggestion(formatted)
                .severity(iss.getSeverity())
                .points(iss.getPoints())
                .confidence(iss.getConfidence())
                .build());
        }
        return finalSuggestions;
    }

    private String feedback(int score, List<RefineSuggestion> issues, String category) {
        String role = category != null ? " for a " + category + " role" : "";
        if (score >= 95) return "Exceptional recruiter-grade resume structure" + role + ". Excellent focus, technical depth, and metrics.";
        if (score >= 90) return "Highly Competitive candidate profile" + role + ". Formatted well with strong impact alignment.";
        if (score >= 80) return "Good ATS foundation" + role + ". Address the remaining warning(s) and tip(s) to optimize.";
        if (score >= 70) return "Average performance" + role + ". Enhance your project details and experience bullets to stand out.";
        if (score >= 60) return "Needs Improvement" + role + ". Resolve critical issues and warnings to lower ATS risk.";
        return "Major Resume Issues detected" + role + ". High formatting and missing fields risk; overhaul structure before applying.";
    }

    private int severityRank(String severity) {
        if ("error".equals(severity))   return 0;
        if ("warning".equals(severity)) return 1;
        return 2;
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

    private String firstMatch(Pattern pattern, String text) {
        var matcher = pattern.matcher(safe(text));
        return matcher.find() ? matcher.group() : "";
    }

    private String normalizeSkillName(String s) {
        return safe(s).toLowerCase(Locale.ROOT).replaceAll("\\s+", "").replaceAll("[^a-z0-9]", "");
    }

    private Integer parseMonthYear(String value) {
        String str = safe(value).trim().toLowerCase(Locale.ROOT);
        str = str.replace("expected ", "");
        String[] parts = str.split("\\s+");
        if (parts.length == 1 && parts[0].matches("\\d{4}")) {
            int year = Integer.parseInt(parts[0]);
            if (year < 1950 || year > CURRENT_YEAR + 10) return null;
            return year * 12 + 1;
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

    private String detectCareerStage(Resume resume) {
        String designation = safe(resume.getProfileInfo() != null ? resume.getProfileInfo().getDesignation() : "").toLowerCase(Locale.ROOT);
        boolean hasFutureEducation = false;
        for (Resume.Education edu : list(resume.getEducation())) {
            String end = safe(edu.getEndDate()).toLowerCase(Locale.ROOT);
            if (end.contains("present") || end.contains("expected")) {
                hasFutureEducation = true;
            }
        }
        if (designation.contains("student") || designation.contains("intern") || designation.contains("undergrad") || designation.contains("candidate") || hasFutureEducation) {
            return "Student";
        }
        if (designation.contains("fresher") || designation.contains("graduate")) return "Fresher";
        if (designation.contains("manager") || designation.contains("director") || designation.contains("vp")) return "Manager";
        if (designation.contains("lead")) return "Lead";
        if (designation.contains("senior") || designation.contains("sr.")) return "Senior";
        if (designation.contains("junior") || designation.contains("jr.")) return "Junior";
        return "Mid-Level";
    }

    private String detectCareerPath(Resume resume, String designation, String text) {
        String lowerDes = designation.toLowerCase(Locale.ROOT);
        
        Map<String, List<String>> matrixTerms = Map.of(
            "Backend Java", List.of("java", "spring", "spring boot", "maven", "hibernate", "jpa", "junit"),
            "Frontend", List.of("javascript", "typescript", "react", "html", "css", "vue", "angular", "next.js"),
            "Machine Learning", List.of("python", "pytorch", "tensorflow", "ml", "machine learning", "nlp", "scikit-learn"),
            "DevOps", List.of("docker", "kubernetes", "jenkins", "ci/cd", "terraform", "ansible", "aws", "gcp"),
            "QA", List.of("selenium", "cypress", "testng", "cucumber", "testing", "manual testing"),
            "Data Analyst", List.of("sql", "power bi", "tableau", "excel", "data analysis", "pandas")
        );

        String bestPath = "General Software Engineer";
        int maxScore = 0;

        for (Map.Entry<String, List<String>> entry : matrixTerms.entrySet()) {
            int score = 0;
            String pathName = entry.getKey();
            List<String> terms = entry.getValue();
            
            for (String term : terms) {
                if (lowerDes.contains(term)) score += 12;
                if (text.contains(term)) score += 1;
            }
            for (Resume.Skill sk : list(resume.getSkills())) {
                String name = safe(sk.getName()).toLowerCase(Locale.ROOT);
                for (String term : terms) {
                    if (name.contains(term)) score += 4;
                }
            }
            if (score > maxScore) {
                maxScore = score;
                bestPath = pathName;
            }
        }

        if (maxScore < 8) {
            return "General Software Engineer";
        }
        return bestPath;
    }

    private List<String> filterMissingKeywords(List<String> missing, String lowerText, String category, String stage, Resume resume) {
        String designation = safe(resume.getProfileInfo() != null ? resume.getProfileInfo().getDesignation() : "");
        String careerPath = detectCareerPath(resume, designation, lowerText);
        boolean isJuniorOrStudent = "Student".equals(stage) || "Fresher".equals(stage) || "Junior".equals(stage);
        
        List<String> filtered = new ArrayList<>();
        for (String kw : missing) {
            String kwLower = kw.toLowerCase(Locale.ROOT);
            
            if ("spring boot".equals(kwLower) && !lowerText.contains("java")) continue;
            if ("typescript".equals(kwLower) && !lowerText.contains("react") && !lowerText.contains("javascript")) continue;
            if (List.of("pandas", "numpy", "scikit-learn", "tensorflow", "pytorch").contains(kwLower) && !lowerText.contains("python")) continue;
            if ("kubernetes".equals(kwLower) && !lowerText.contains("docker")) continue;
            if ("microservices".equals(kwLower) && !lowerText.contains("rest api") && !lowerText.contains("backend") && !lowerText.contains("spring") && !lowerText.contains("node")) continue;
            
            if ("software engineer".equalsIgnoreCase(category)) {
                if ("Backend Java".equals(careerPath)) {
                    if (List.of("react", "typescript", "javascript", "vue", "angular", "html", "css", "pandas", "pytorch", "tensorflow").contains(kwLower)) continue;
                } else if ("Frontend".equals(careerPath)) {
                    if (List.of("java", "spring boot", "hibernate", "maven", "python", "pytorch", "tensorflow", "docker", "kubernetes", "c++").contains(kwLower)) continue;
                } else if ("Machine Learning".equals(careerPath)) {
                    if (List.of("react", "typescript", "javascript", "spring boot", "hibernate", "angular", "vue", "html", "css").contains(kwLower)) continue;
                } else if ("DevOps".equals(careerPath)) {
                    if (List.of("react", "typescript", "javascript", "spring boot", "hibernate", "angular", "vue", "html", "css").contains(kwLower)) continue;
                } else if ("QA".equals(careerPath)) {
                    if (List.of("react", "typescript", "docker", "kubernetes", "spring boot", "hibernate", "microservices", "aws", "azure", "gcp").contains(kwLower)) continue;
                } else if ("Data Analyst".equals(careerPath)) {
                    if (List.of("react", "typescript", "docker", "kubernetes", "spring boot", "hibernate", "microservices", "aws", "gcp", "azure").contains(kwLower)) continue;
                } else if ("General Software Engineer".equals(careerPath)) {
                    if (List.of("spring boot", "hibernate", "react", "typescript", "kubernetes", "tensorflow", "pytorch", "scikit-learn", "vue", "angular", "pandas", "numpy").contains(kwLower)) continue;
                }
            }
            
            if (isJuniorOrStudent) {
                if (List.of("kubernetes", "docker", "microservices", "ci/cd", "aws", "system design").contains(kwLower)) {
                    List<String> infra = List.of("linux", "git", "rest api", "sql", "cloud", "backend");
                    boolean hasInfra = false;
                    for (String inf : infra) {
                        if (lowerText.contains(inf)) hasInfra = true;
                    }
                    if (!hasInfra) continue;
                }
            }
            
            filtered.add(kw);
        }
        return filtered;
    }

    private String allText(Resume resume) {
        List<String> p = new ArrayList<>();
        Resume.ProfileInfo pr = resume.getProfileInfo() != null ? resume.getProfileInfo() : new Resume.ProfileInfo();
        Resume.ContactInfo co = resume.getContactInfo() != null ? resume.getContactInfo() : new Resume.ContactInfo();
        
        p.add(safe(pr.getFullName()));
        p.add(safe(pr.getDesignation()));
        p.add(safe(pr.getSummary()));
        p.add(safe(co.getLocation()));
        p.add(safe(co.getLinkedIn()));
        p.add(safe(co.getGithub()));
        p.add(safe(co.getWebsite()));
        
        for (Resume.WorkExperience j : list(resume.getWorkExperience())) {
            p.add(safe(j.getCompany()));
            p.add(safe(j.getRole()));
            p.add(safe(j.getDescription()));
        }
        for (Resume.Education e : list(resume.getEducation())) {
            p.add(safe(e.getDegree()));
            p.add(safe(e.getInstitution()));
        }
        for (Resume.Skill s : list(resume.getSkills())) {
            p.add(safe(s.getName()));
        }
        for (Resume.Project q : list(resume.getProjects())) {
            p.add(safe(q.getTitle()));
            p.add(safe(q.getDescription()));
            p.add(safe(q.getGithub()));
            p.add(safe(q.getLiveDemo()));
        }
        for (Resume.Certification c : list(resume.getCertifications())) {
            p.add(safe(c.getTitle()));
            p.add(safe(c.getIssuer()));
            p.add(safe(c.getYear()));
        }
        for (Resume.Language l : list(resume.getLanguages())) {
            p.add(safe(l.getName()));
        }
        for (String i : list(resume.getInterests())) {
            p.add(safe(i));
        }
        return String.join(" ", p);
    }
}
