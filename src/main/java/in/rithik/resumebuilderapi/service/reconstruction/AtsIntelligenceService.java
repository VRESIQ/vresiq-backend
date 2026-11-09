package in.rithik.resumebuilderapi.service.reconstruction;

import in.rithik.resumebuilderapi.document.Resume;
import in.rithik.resumebuilderapi.dto.AtsIntelligenceModels.AtsFinding;
import in.rithik.resumebuilderapi.dto.AtsIntelligenceModels.DraftAtsReport;
import in.rithik.resumebuilderapi.dto.AtsIntelligenceModels.KeywordCoverage;
import in.rithik.resumebuilderapi.dto.AtsIntelligenceModels.KeywordMatch;
import in.rithik.resumebuilderapi.dto.AtsIntelligenceModels.SourceAtsReport;
import in.rithik.resumebuilderapi.dto.AtsIntelligenceModels.TailorReport;
import in.rithik.resumebuilderapi.dto.AtsIntelligenceModels.TailoringSuggestion;
import in.rithik.resumebuilderapi.dto.RefineResponse;
import in.rithik.resumebuilderapi.dto.RefineSuggestion;
import in.rithik.resumebuilderapi.service.RefineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic ATS intelligence for reconstructed resumes.
 *
 * <p>Three independent products are produced here:
 * <ol>
 *   <li>a <b>source</b> report describing risks in the uploaded file's extracted text,</li>
 *   <li>a <b>draft</b> report describing the structured editable resume (reusing the
 *       existing, rules-driven {@link RefineService} scoring),</li>
 *   <li>an evidence-based <b>tailoring</b> report for an optional job description.</li>
 * </ol>
 *
 * <p>Nothing in this service invents candidate facts: every tailoring proposal either
 * moves text the candidate already wrote or asks them to confirm it. Nothing is applied
 * automatically, and no external service is contacted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AtsIntelligenceService {

    private final RefineService refineService;

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(\\+?\\d[\\d\\s().-]{7,}\\d)");
    private static final Pattern URL = Pattern.compile("(https?://|www\\.)[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAGE_FURNITURE = Pattern.compile("^(page\\s*\\d+(\\s*(of|/)\\s*\\d+)?|\\d{1,2}\\s*\\|\\s*page)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]");
    private static final Pattern FILLER_OPENER = Pattern.compile(
            "^(responsible for|worked on|helped (with|to)|assisted (with|in)|involved in|participated in|tasked with|duties included|was responsible for)\\s*:?\\s*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STRONG_VERB = Pattern.compile(
            "^(built|led|designed|developed|managed|created|delivered|implemented|improved|reduced|increased|launched|automated|migrated|optimized|owned|shipped|streamlined|trained|transformed|drove|achieved|established|scaled|negotiated|analyzed|coordinated|mentored|maintained|supported|tested|deployed|documented)\\b",
            Pattern.CASE_INSENSITIVE);

    /** Canonical headings an ATS parser is expected to recognise, with accepted aliases. */
    private static final Map<String, List<String>> HEADINGS = new LinkedHashMap<>();
    static {
        HEADINGS.put("Summary", List.of("summary", "profile summary", "professional summary", "objective", "career objective", "professional profile", "about me", "executive summary"));
        HEADINGS.put("Experience", List.of("experience", "work experience", "professional experience", "employment history", "work history", "career history", "professional background"));
        HEADINGS.put("Education", List.of("education", "academic background", "academic qualifications", "qualifications", "education and training"));
        HEADINGS.put("Skills", List.of("skills", "technical skills", "core competencies", "key skills", "areas of expertise", "competencies", "technical proficiencies"));
        HEADINGS.put("Projects", List.of("projects", "personal projects", "side projects", "key projects", "selected projects"));
        HEADINGS.put("Certifications", List.of("certifications", "certificates", "licenses", "licenses & certifications", "certifications and licenses", "professional certifications"));
        HEADINGS.put("Publications", List.of("publications", "papers", "research", "presentations"));
        HEADINGS.put("Languages", List.of("languages", "language proficiency"));
        HEADINGS.put("Awards", List.of("awards", "honors", "honours", "achievements", "accomplishments"));
        HEADINGS.put("Interests", List.of("interests", "hobbies", "activities"));
        HEADINGS.put("Volunteering", List.of("volunteering", "volunteer experience", "community involvement", "community service"));
        HEADINGS.put("Internships", List.of("internships", "internship experience", "internship"));
        HEADINGS.put("Training", List.of("training", "courses", "coursework", "professional development", "workshops"));
    }

    /**
     * Skill vocabulary used to recognise job-description requirements. Deliberately a fixed,
     * auditable list so keyword extraction never depends on an external model.
     */
    private static final List<String> SKILL_LEXICON = List.of(
            "java", "python", "javascript", "typescript", "c++", "c#", "go", "rust", "ruby", "php", "kotlin", "swift", "scala",
            "react", "react native", "next.js", "vue", "angular", "svelte", "redux", "tailwind", "html", "css", "sass",
            "node.js", "express", "spring boot", "spring", "django", "flask", "fastapi", "graphql", "rest api", "restful services",
            "microservices", "serverless", "docker", "kubernetes", "terraform", "ansible", "jenkins", "ci/cd", "github actions",
            "aws", "azure", "gcp", "google cloud", "lambda", "s3", "ec2", "cloudformation", "linux", "unix", "bash", "shell scripting",
            "sql", "mysql", "postgresql", "postgres", "mongodb", "redis", "elasticsearch", "dynamodb", "oracle", "database design",
            "kafka", "rabbitmq", "etl", "data pipelines", "data analysis", "data visualization", "machine learning", "deep learning",
            "nlp", "computer vision", "pandas", "numpy", "scikit-learn", "tensorflow", "pytorch", "spark", "hadoop", "tableau",
            "power bi", "looker", "excel", "r", "matlab", "statistics", "a/b testing",
            "git", "github", "gitlab", "jira", "confluence", "agile", "scrum", "kanban", "tdd", "unit testing", "integration testing",
            "pytest", "jest", "cypress", "selenium", "playwright", "code review", "system design", "distributed systems",
            "performance optimization", "security", "oauth", "jwt", "encryption", "accessibility", "seo", "responsive design",
            "figma", "sketch", "adobe xd", "photoshop", "illustrator", "ui design", "ux design", "user research", "wireframing", "prototyping",
            "product management", "project management", "stakeholder management", "roadmap", "product strategy", "okrs",
            "salesforce", "hubspot", "sap", "quickbooks", "netsuite", "crm", "erp",
            "customer service", "account management", "sales", "negotiation", "business development", "marketing", "content writing",
            "copywriting", "social media", "campaign management", "google analytics", "email marketing",
            "communication", "leadership", "team management", "mentoring", "problem solving", "collaboration", "time management",
            "budgeting", "forecasting", "financial analysis", "accounting", "auditing", "compliance", "risk management",
            "supply chain", "logistics", "inventory management", "quality assurance", "manufacturing", "lean", "six sigma",
            "troubleshooting", "technical support", "documentation", "training and development", "recruiting", "onboarding"
    );

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "if", "then", "than", "that", "this", "these", "those", "with", "without",
            "for", "from", "into", "onto", "over", "under", "about", "across", "after", "before", "during", "between",
            "to", "of", "in", "on", "at", "by", "as", "is", "are", "was", "were", "be", "been", "being", "will", "would",
            "can", "could", "should", "may", "might", "must", "shall", "have", "has", "had", "do", "does", "did",
            "you", "your", "we", "our", "they", "their", "it", "its", "he", "she", "his", "her", "i", "me", "my",
            "who", "whom", "which", "what", "when", "where", "why", "how", "all", "any", "both", "each", "few", "more",
            "most", "other", "some", "such", "no", "nor", "not", "only", "own", "same", "so", "too", "very", "just",
            "also", "well", "work", "working", "role", "job", "position", "candidate", "ability", "able", "experience",
            "experienced", "years", "year", "team", "teams", "company", "including", "etc", "plus", "strong", "good",
            "excellent", "great", "required", "requirements", "preferred", "responsibilities", "responsible", "you'll",
            "we're", "us", "new", "least", "one", "two", "three", "four", "five", "day", "days"
    );

    // ─────────────────────────────────────────────────────────────────────────
    // Source (uploaded file) report
    // ─────────────────────────────────────────────────────────────────────────

    public SourceAtsReport analyzeSource(String rawText, Resume parsed) {
        String text = rawText == null ? "" : rawText;
        List<AtsFinding> findings = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        if (text.isBlank()) {
            return SourceAtsReport.builder()
                    .available(false)
                    .extractedCharacters(0)
                    .foundSections(0)
                    .missingSectionHeadings(new ArrayList<>(HEADINGS.keySet()))
                    .findings(List.of(AtsFinding.builder()
                            .id("source_empty").scope("source").severity("error").check("source_empty")
                            .section("Document").fieldPath("")
                            .message("No selectable text was found in the uploaded file.")
                            .recommendation("The file is most likely a scan or an image export. Upload a text-based PDF or DOCX; OCR is not configured.")
                            .build()))
                    .notes(notes)
                    .summary("The uploaded file produced no extractable text, so no source analysis is possible.")
                    .build();
        }

        int characters = text.replaceAll("\\s", "").length();
        List<String> lines = text.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();

        if (!EMAIL.matcher(text).find()) {
            findings.add(sourceFinding("source_missing_email", "error", "Contact", "contactInfo.email", "",
                    "No email address could be read from the file text.",
                    "Add an email address to the header. An ATS that cannot parse an email usually cannot contact the candidate."));
        }
        if (!PHONE.matcher(text).find()) {
            findings.add(sourceFinding("source_missing_phone", "warning", "Contact", "contactInfo.phone", "",
                    "No phone number could be read from the file text.",
                    "Add a phone number in plain digits, without images or unusual separators."));
        }
        if (!URL.matcher(text).find()) {
            findings.add(sourceFinding("source_no_links", "tip", "Contact", "contactInfo.linkedIn", "",
                    "No links (LinkedIn, portfolio, GitHub) were found in the file.",
                    "Add at least one link as real text or as a PDF link annotation so it survives extraction."));
        }

        List<String> detected = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        String lowered = text.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : HEADINGS.entrySet()) {
            boolean found = entry.getValue().stream().anyMatch(alias ->
                    Pattern.compile("(?m)^\\s*" + Pattern.quote(alias) + "\\s*:?\\s*$").matcher(lowered).find()
                            || lowered.contains("\n" + alias + "\n"));
            (found ? detected : missing).add(entry.getKey());
        }
        if (detected.isEmpty()) {
            findings.add(sourceFinding("source_no_headings", "error", "Document structure", "",
                    "", "No standard section headings were recognised in the extracted text.",
                    "Use conventional headings (Summary, Experience, Education, Skills) as plain text lines. Headings placed inside graphics or tables are invisible to parsers."));
        } else if (missing.size() >= 5) {
            findings.add(sourceFinding("source_few_headings", "warning", "Document structure", "",
                    String.join(", ", missing.subList(0, Math.min(5, missing.size()))),
                    "Only " + detected.size() + " standard section heading(s) were recognised.",
                    "Confirm the remaining sections use conventional names; unusual headings can be read as body text."));
        }

        long longLines = lines.stream().filter(line -> line.length() > 240).count();
        if (longLines > 0) {
            findings.add(sourceFinding("source_long_lines", "warning", "Document structure", "",
                    longLines + " line(s) over 240 characters",
                    "Very long extracted lines usually mean columns were merged during extraction.",
                    "Simplify the layout to a single column and re-export, or verify the affected content after reconstruction."));
        }

        long pageFurniture = lines.stream().filter(line -> PAGE_FURNITURE.matcher(line).matches()).count();
        if (pageFurniture > 0) {
            findings.add(sourceFinding("source_page_furniture", "warning", "Document structure", "",
                    String.valueOf(pageFurniture),
                    "Page numbers or footer text were extracted as resume content.",
                    "Remove running headers/footers from the source document. They become stray lines in parsed sections."));
        }

        int duplicates = 0;
        Set<String> seen = new LinkedHashSet<>();
        for (String line : lines) {
            if (line.length() > 12 && !seen.add(line.toLowerCase(Locale.ROOT))) duplicates++;
        }
        if (duplicates > 0) {
            findings.add(sourceFinding("source_duplicate_lines", "warning", "Document structure", "",
                    duplicates + " repeated line(s)",
                    "The same text appears more than once in the extracted content.",
                    "Check for duplicated blocks or two-column text read twice. Duplicates waste space and confuse keyword counting."));
        }

        long controlCharacters = CONTROL_CHARS.matcher(text).results().count();
        if (controlCharacters > 0) {
            findings.add(sourceFinding("source_extraction_artifacts", "tip", "Document structure", "",
                    controlCharacters + " control character(s)",
                    "The extracted text contains characters that indicate a fragile PDF text layer.",
                    "Prefer exporting from Word to DOCX, or verify each section after reconstruction."));
        }

        if (characters < 200) {
            findings.add(sourceFinding("source_thin_text", "error", "Document structure", "",
                    characters + " non-space characters",
                    "Very little text was extracted from the whole document.",
                    "The document may be image-based. Upload a text-based PDF/DOCX, or paste the resume text instead."));
        }

        if (parsed != null && parsed.getWorkExperience() != null && !parsed.getWorkExperience().isEmpty()) {
            parsed.getWorkExperience().forEach(exp -> {
                if (exp.getStartDate() != null && exp.getEndDate() != null
                        && isReverseChronology(exp.getStartDate(), exp.getEndDate())) {
                    findings.add(sourceFinding("source_date_order", "warning", "Experience", "",
                            exp.getStartDate() + " to " + exp.getEndDate(),
                            "A parsed employment date range ends before it starts.",
                            "Correct the dates on this entry; ATS parsers flag reversed ranges as timeline errors."));
                }
            });
        }

        notes.add("Source analysis reads only the text an ATS parser would receive from the uploaded file.");
        notes.add("Multi-column reading order, decorative text and image-only content cannot be assessed reliably from text alone.");
        notes.add("Portrait photos and graphics are not evaluated here; they are handled during reconstruction.");
        notes.add("Severity reflects parser risk, not employer screening outcomes.");

        return SourceAtsReport.builder()
                .available(true)
                .extractedCharacters(characters)
                .foundSections(detected.size())
                .missingSectionHeadings(missing)
                .findings(sortFindings(findings))
                .notes(notes)
                .summary(detected.isEmpty()
                        ? "The file text was readable but no standard section headings were recognised."
                        : "The file text was readable and " + detected.size() + " standard section heading(s) were recognised. "
                          + findings.size() + " source-level issue(s) were found.")
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Draft (structured resume) report
    // ─────────────────────────────────────────────────────────────────────────

    public DraftAtsReport analyzeDraft(Resume resume) {
        RefineResponse refined;
        try {
            refined = refineService.analyze(resume);
        } catch (RuntimeException exception) {
            log.warn("Draft ATS analysis failed; exceptionType={}", exception.getClass().getSimpleName());
            return DraftAtsReport.builder()
                    .score(0).readiness("unknown").category("")
                    .summary("Draft analysis could not be completed for this resume.")
                    .strengths(new ArrayList<>())
                    .findings(new ArrayList<>())
                    .keywordCoverage(KeywordCoverage.builder().total(0).present(0).missing(0).matches(new ArrayList<>()).build())
                    .build();
        }

        List<AtsFinding> findings = new ArrayList<>();
        for (RefineSuggestion suggestion : refined.getIssues()) {
            findings.add(AtsFinding.builder()
                    .id(suggestion.getType() + "-" + findings.size())
                    .scope("draft")
                    .check(suggestion.getType())
                    .severity(suggestion.getSeverity())
                    .section(suggestion.getSection())
                    .fieldPath(fieldPathFor(suggestion.getSection()))
                    .original(suggestion.getOriginal())
                    .message(suggestion.getSuggestion())
                    .recommendation(recommendationFor(suggestion))
                    .points(suggestion.getPoints())
                    .build());
        }

        int score = refined.getAtsScore();
        String readiness = score >= 85 ? "strong" : score >= 70 ? "good" : score >= 50 ? "moderate" : "needs_work";
        List<String> strengths = refined.getStrengths() == null ? new ArrayList<>() : refined.getStrengths();

        List<KeywordMatch> keywordMatches = new ArrayList<>();

        return DraftAtsReport.builder()
                .score(score)
                .readiness(readiness)
                .category(refined.getCategory())
                .summary(refined.getOverallFeedback())
                .strengths(strengths)
                .findings(sortFindings(findings))
                .keywordCoverage(KeywordCoverage.builder()
                        .total(keywordMatches.size())
                        .present((int) keywordMatches.stream().filter(KeywordMatch::isPresent).count())
                        .missing((int) keywordMatches.stream().filter(m -> !m.isPresent()).count())
                        .matches(keywordMatches)
                        .build())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Job-description tailoring
    // ─────────────────────────────────────────────────────────────────────────

    public TailorReport tailor(Resume resume, String jobDescription) {
        List<String> notes = new ArrayList<>();
        if (jobDescription == null || jobDescription.trim().length() < 40) {
            return TailorReport.builder()
                    .available(false)
                    .detectedRole("")
                    .requiredKeywords(new ArrayList<>())
                    .preferredKeywords(new ArrayList<>())
                    .suggestions(new ArrayList<>())
                    .unsupported(new ArrayList<>())
                    .notes(List.of("Paste a job description of at least a few lines to enable tailoring."))
                    .summary("No job description provided.")
                    .build();
        }

        String jd = jobDescription.toLowerCase(Locale.ROOT);
        List<String> sentences = List.of(jobDescription.split("(?<=[.!?\\n])\\s+"));
        Map<String, String> keywordImportance = new LinkedHashMap<>();
        for (String keyword : SKILL_LEXICON) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b", Pattern.CASE_INSENSITIVE);
            if (!p.matcher(jd).find()) continue;
            String importance = "context";
            for (String sentence : sentences) {
                if (!p.matcher(sentence.toLowerCase(Locale.ROOT)).find()) continue;
                String s = sentence.toLowerCase(Locale.ROOT);
                if (s.matches("(?s).*\\b(must have|must-have|required|requirement|minimum|essential|need to have)\\b.*")) {
                    importance = "required";
                    break;
                }
                if (s.matches("(?s).*\\b(preferred|nice to have|nice-to-have|plus|bonus|advantageous)\\b.*")) {
                    importance = "preferred";
                }
            }
            keywordImportance.put(keyword, importance);
        }

        if (keywordImportance.isEmpty()) {
            notes.add("No recognised skill keywords were found in this job description. Add the concrete tools, technologies or competencies the role lists.");
        }

        List<FieldText> fieldTexts = flattenResume(resume);
        List<KeywordMatch> required = new ArrayList<>();
        List<KeywordMatch> preferred = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        List<TailoringSuggestion> suggestions = new ArrayList<>();
        Set<String> skillsLower = new LinkedHashSet<>();
        if (resume.getSkills() != null) {
            for (Resume.Skill skill : resume.getSkills()) {
                if (skill != null && skill.getName() != null) skillsLower.add(skill.getName().toLowerCase(Locale.ROOT).trim());
            }
        }

        int suggestionSeq = 0;
        List<String> surfaced = new ArrayList<>();
        for (Map.Entry<String, String> entry : keywordImportance.entrySet()) {
            String keyword = entry.getKey();
            FieldText evidence = findEvidence(fieldTexts, keyword);
            KeywordMatch match = KeywordMatch.builder()
                    .keyword(keyword)
                    .importance(entry.getValue())
                    .present(evidence != null)
                    .evidence(evidence == null ? "" : evidence.excerpt)
                    .fieldPath(evidence == null ? "" : evidence.fieldPath)
                    .build();
            ("required".equals(entry.getValue()) ? required : preferred).add(match);

            if (evidence == null) {
                unsupported.add(keyword);
                continue;
            }
            boolean alreadyListed = skillsLower.stream().anyMatch(skill -> skill.equals(keyword) || skill.contains(keyword));
            if (!alreadyListed && evidence.fieldPath != null && !evidence.fieldPath.startsWith("skills")) {
                surfaced.add(keyword);
                suggestions.add(TailoringSuggestion.builder()
                        .id("kw-" + (suggestionSeq++))
                        .type("keyword")
                        .section("Skills")
                        .fieldPath("skills")
                        .evidence(evidence.excerpt)
                        .proposal(keyword)
                        .rationale("This skill appears in your own " + sectionName(evidence.fieldPath)
                                + " text but is not listed under Skills. Adding the existing evidence improves keyword coverage without changing any facts.")
                        .requiresConfirmation(false)
                        .build());
            }
        }

        // A weak-opener bullet that already carries a job keyword can be tightened using only
        // the candidate's own words.
        if (resume.getWorkExperience() != null) {
            for (int i = 0; i < resume.getWorkExperience().size(); i++) {
                Resume.WorkExperience exp = resume.getWorkExperience().get(i);
                if (exp == null || exp.getDescription() == null) continue;
                String[] bulletCandidates = exp.getDescription().split("(?<=[.!?\\n])\\s+|\\n+");
                for (String candidate : bulletCandidates) {
                    String trimmed = candidate.trim();
                    if (trimmed.length() < 20) continue;
                    Matcher filler = FILLER_OPENER.matcher(trimmed);
                    if (!filler.find()) continue;
                    String rest = trimmed.substring(filler.end()).trim();
                    if (rest.length() < 15 || !STRONG_VERB.matcher(rest).find()) continue;
                    String proposal = Character.toUpperCase(rest.charAt(0)) + rest.substring(1);
                    suggestions.add(TailoringSuggestion.builder()
                            .id("bl-" + (suggestionSeq++))
                            .type("bullet")
                            .section("Experience " + (i + 1))
                            .fieldPath("workExperience[" + i + "].description")
                            .evidence(trimmed)
                            .proposal(proposal)
                            .rationale("Removes a weak opener (e.g. \"responsible for\") so the bullet starts with the action you already describe. No wording beyond the filler removal is changed.")
                            .requiresConfirmation(false)
                            .build());
                }
            }
        }

        // Summary alignment: only evidenced keywords, phrased as evidence rather than claims.
        if (!surfaced.isEmpty() && resume.getProfileInfo() != null) {
            String summary = resume.getProfileInfo().getSummary();
            List<String> reusable = surfaced.stream().limit(4).toList();
            if (reusable.size() >= 2 && (summary == null || summary.length() < 700)) {
                suggestions.add(TailoringSuggestion.builder()
                        .id("sm-" + (suggestionSeq++))
                        .type("summary")
                        .section("Summary")
                        .fieldPath("profileInfo.summary")
                        .evidence(String.join(", ", reusable))
                        .proposal("Evidenced strengths: " + String.join(", ", reusable) + ".")
                        .rationale("These terms are already present elsewhere in your resume. Add this sentence only if it accurately represents your work.")
                        .requiresConfirmation(true)
                        .build());
            }
        }

        // Reordering: surface the entry that carries the most required keywords.
        if (resume.getWorkExperience() != null && resume.getWorkExperience().size() > 1) {
            int bestIndex = 0;
            int bestScore = -1;
            for (int i = 0; i < resume.getWorkExperience().size(); i++) {
                String description = resume.getWorkExperience().get(i) == null ? "" : String.valueOf(resume.getWorkExperience().get(i).getDescription());
                int hits = 0;
                for (KeywordMatch match : required) {
                    if (!match.isPresent()) continue;
                    if (description.toLowerCase(Locale.ROOT).contains(match.getKeyword())) hits++;
                }
                if (hits > bestScore) { bestScore = hits; bestIndex = i; }
            }
            if (bestIndex > 0 && bestScore > 0) {
                suggestions.add(TailoringSuggestion.builder()
                        .id("ro-" + (suggestionSeq++))
                        .type("reorder")
                        .section("Experience")
                        .fieldPath("")
                        .evidence("The most job-relevant entry is currently position " + (bestIndex + 1) + ".")
                        .proposal("workExperience order: " + bestIndex)
                        .rationale("Move the entry containing the most required keywords to the top so a recruiter sees relevant work first. Content is unchanged.")
                        .requiresConfirmation(true)
                        .build());
            }
        }

        if (!unsupported.isEmpty()) {
            notes.add("No supporting evidence was found for: " + String.join(", ", unsupported)
                    + ". These were not added to the resume and must not be claimed unless genuinely held.");
        }
        notes.add("Every change is a proposal. The reconstructed resume is not modified until you accept it, and accepted changes can be undone individually.");

        return TailorReport.builder()
                .available(true)
                .detectedRole(detectRole(jobDescription))
                .requiredKeywords(required)
                .preferredKeywords(preferred)
                .suggestions(suggestions)
                .unsupported(unsupported)
                .notes(notes)
                .summary(required.size() + " required and " + preferred.size() + " preferred keyword(s) recognised; "
                        + required.stream().filter(KeywordMatch::isPresent).count() + "/" + required.size()
                        + " required keyword(s) have supporting evidence in the resume.")
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private AtsFinding sourceFinding(String id, String severity, String section, String fieldPath,
                                     String original, String message, String recommendation) {
        return AtsFinding.builder()
                .id(id).scope("source").severity(severity).check(id)
                .section(section).fieldPath(fieldPath)
                .original(original).message(message).recommendation(recommendation)
                .build();
    }

    private String recommendationFor(RefineSuggestion suggestion) {
        if ("keyword_gap".equals(suggestion.getType())) {
            return "Add only the missing keywords you can genuinely support, then re-run the check.";
        }
        return suggestion.getSuggestion();
    }

    /**
     * Maps a RefineService section label ("Experience 2 > Start date") to a stable field path
     * the Studio editor can scroll to and focus.
     */
    static String fieldPathFor(String label) {
        if (label == null) return "";
        String section = label.split(">")[0].trim();
        String field = label.contains(">") ? label.split(">")[1].trim().toLowerCase(Locale.ROOT) : "";

        int index = -1;
        Matcher m = Pattern.compile("^(Experience|Education|Skills|Projects|Certifications|Languages|Interests)\\s*(\\d+)").matcher(section);
        if (m.find()) index = Integer.parseInt(m.group(2)) - 1;

        if (section.startsWith("Profile")) {
            return switch (field) {
                case "full name" -> "profileInfo.fullName";
                case "designation" -> "profileInfo.designation";
                case "summary" -> "profileInfo.summary";
                case "photo" -> "profileInfo.profilePreviewUrl";
                default -> "profileInfo";
            };
        }
        if (section.startsWith("Contact")) {
            return switch (field) {
                case "email" -> "contactInfo.email";
                case "phone" -> "contactInfo.phone";
                case "location" -> "contactInfo.location";
                case "linkedin" -> "contactInfo.linkedIn";
                case "github" -> "contactInfo.github";
                case "website" -> "contactInfo.website";
                default -> "contactInfo";
            };
        }
        if (section.startsWith("Customization")) {
            return switch (field) {
                case "template" -> "template";
                case "font" -> "fontPairing";
                case "header style" -> "decoratives.headerStyle";
                case "divider style" -> "decoratives.dividerStyle";
                case "section icons" -> "decoratives.sectionIcons";
                case "section numbers" -> "decoratives.sectionNumbers";
                case "skill progress style" -> "decoratives.skillProgressStyle";
                default -> "decoratives";
            };
        }
        if (section.startsWith("Experience")) {
            String base = index >= 0 ? "workExperience[" + index + "]" : "workExperience";
            return switch (field) {
                case "start date" -> base + ".startDate";
                case "end date" -> base + ".endDate";
                case "dates" -> base + ".startDate";
                default -> index >= 0 ? base + ".description" : base;
            };
        }
        if (section.startsWith("Education")) {
            String base = index >= 0 ? "education[" + index + "]" : "education";
            return switch (field) {
                case "degree" -> base + ".degree";
                case "institution" -> base + ".institution";
                case "start date" -> base + ".startDate";
                case "end date" -> base + ".endDate";
                default -> index >= 0 ? base + ".description" : base;
            };
        }
        if (section.startsWith("Skills")) {
            return index >= 0 ? "skills[" + index + "].name" : "skills";
        }
        if (section.startsWith("Projects")) {
            String base = index >= 0 ? "projects[" + index + "]" : "projects";
            return "title".equals(field) ? base + ".title" : index >= 0 ? base + ".description" : base;
        }
        if (section.startsWith("Certifications")) {
            String base = index >= 0 ? "certifications[" + index + "]" : "certifications";
            return switch (field) {
                case "title" -> base + ".title";
                case "issuer" -> base + ".issuer";
                default -> "year".equals(field) ? base + ".year" : base;
            };
        }
        if (section.startsWith("Languages")) return index >= 0 ? "languages[" + index + "].name" : "languages";
        if (section.startsWith("Interests")) return index >= 0 ? "interests[" + index + "]" : "interests";
        if (section.startsWith("Role keywords")) return "";
        return "";
    }

    private List<AtsFinding> sortFindings(List<AtsFinding> findings) {
        findings.sort(Comparator.comparingInt(f -> switch (f.getSeverity()) {
            case "error" -> 0;
            case "warning" -> 1;
            default -> 2;
        }));
        return findings;
    }

    private boolean isReverseChronology(String start, String end) {
        Integer s = yearOf(start);
        Integer e = yearOf(end);
        if (s == null || e == null) return false;
        if (s != null && e != null && s > e) return true;
        if (s.equals(e)) {
            Integer sm = monthOf(start);
            Integer em = monthOf(end);
            return sm != null && em != null && sm > em;
        }
        return false;
    }

    private Integer yearOf(String value) {
        Matcher m = Pattern.compile("(19|20)\\d{2}").matcher(value == null ? "" : value);
        return m.find() ? Integer.parseInt(m.group()) : null;
    }

    private Integer monthOf(String value) {
        if (value == null) return null;
        String v = value.toLowerCase(Locale.ROOT);
        String[] months = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
        for (int i = 0; i < months.length; i++) if (v.startsWith(months[i])) return i + 1;
        Matcher m = Pattern.compile("^\\s*(\\d{1,2})[/-]").matcher(v);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private record FieldText(String fieldPath, String text, String excerpt) {}

    private List<FieldText> flattenResume(Resume resume) {
        List<FieldText> out = new ArrayList<>();
        if (resume == null) return out;
        if (resume.getProfileInfo() != null) {
            add(out, "profileInfo.summary", resume.getProfileInfo().getSummary());
            add(out, "profileInfo.designation", resume.getProfileInfo().getDesignation());
            add(out, "profileInfo.fullName", resume.getProfileInfo().getFullName());
        }
        if (resume.getContactInfo() != null) {
            add(out, "contactInfo.website", str(resume.getContactInfo().getWebsite()));
            add(out, "contactInfo.linkedIn", str(resume.getContactInfo().getLinkedIn()));
            add(out, "contactInfo.github", str(resume.getContactInfo().getGithub()));
            add(out, "contactInfo.location", str(resume.getContactInfo().getLocation()));
        }
        if (resume.getWorkExperience() != null) {
            for (int i = 0; i < resume.getWorkExperience().size(); i++) {
                Resume.WorkExperience exp = resume.getWorkExperience().get(i);
                if (exp == null) continue;
                add(out, "workExperience[" + i + "].company", exp.getCompany());
                add(out, "workExperience[" + i + "].role", exp.getRole());
                add(out, "workExperience[" + i + "].description", exp.getDescription());
            }
        }
        if (resume.getEducation() != null) {
            for (int i = 0; i < resume.getEducation().size(); i++) {
                Resume.Education edu = resume.getEducation().get(i);
                if (edu == null) continue;
                add(out, "education[" + i + "].degree", edu.getDegree());
                add(out, "education[" + i + "].institution", edu.getInstitution());
                add(out, "education[" + i + "].description", edu.getDescription());
            }
        }
        if (resume.getSkills() != null) {
            for (int i = 0; i < resume.getSkills().size(); i++) {
                Resume.Skill skill = resume.getSkills().get(i);
                if (skill == null) continue;
                add(out, "skills[" + i + "].name", skill.getName());
            }
        }
        if (resume.getProjects() != null) {
            for (int i = 0; i < resume.getProjects().size(); i++) {
                Resume.Project project = resume.getProjects().get(i);
                if (project == null) continue;
                add(out, "projects[" + i + "].title", project.getTitle());
                add(out, "projects[" + i + "].description", project.getDescription());
            }
        }
        if (resume.getCertifications() != null) {
            for (int i = 0; i < resume.getCertifications().size(); i++) {
                Resume.Certification cert = resume.getCertifications().get(i);
                if (cert == null) continue;
                add(out, "certifications[" + i + "].title", cert.getTitle());
                add(out, "certifications[" + i + "].issuer", cert.getIssuer());
            }
        }
        if (resume.getLanguages() != null) {
            for (int i = 0; i < resume.getLanguages().size(); i++) {
                Resume.Language language = resume.getLanguages().get(i);
                if (language == null) continue;
                add(out, "languages[" + i + "].name", language.getName());
            }
        }
        if (resume.getInterests() != null) {
            for (int i = 0; i < resume.getInterests().size(); i++) {
                add(out, "interests[" + i + "]", resume.getInterests().get(i));
            }
        }
        if (resume.getCustomSections() != null) {
            resume.getCustomSections().forEach((section, entries) -> {
                if (entries == null) return;
                for (int i = 0; i < entries.size(); i++) {
                    Resume.CustomSectionEntry entry = entries.get(i);
                    if (entry == null) continue;
                    add(out, "customSections." + section + "[" + i + "].description", entry.getDescription());
                    add(out, "customSections." + section + "[" + i + "].title", entry.getTitle());
                }
            });
        }
        return out;
    }

    private void add(List<FieldText> out, String fieldPath, String text) {
        if (text == null || text.isBlank()) return;
        String cleaned = text.replaceAll("\\s+", " ").trim();
        out.add(new FieldText(fieldPath, cleaned, excerpt(cleaned, text)));
    }

    private String excerpt(String cleaned, String original) {
        return cleaned.length() <= 180 ? cleaned : cleaned.substring(0, 177) + "...";
    }

    private FieldText findEvidence(List<FieldText> fields, String keyword) {
        Pattern p = Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b", Pattern.CASE_INSENSITIVE);
        // Prefer non-skill fields as evidence: a skill already listed needs no surfacing.
        for (FieldText field : fields) {
            if (p.matcher(field.text).find()) return field;
        }
        return null;
    }

    private String str(Object value) {
        return value == null ? null : value.toString();
    }

    private String sectionName(String fieldPath) {
        if (fieldPath == null || fieldPath.isEmpty()) return "resume";
        String base = fieldPath.split("\\.")[0].replaceAll("\\[\\d+\\]", "");
        return switch (base) {
            case "workExperience" -> "experience";
            case "projects" -> "project";
            case "education" -> "education";
            case "customSections" -> "additional section";
            case "profileInfo" -> "summary";
            case "skills" -> "skills";
            default -> base;
        };
    }

    private String detectRole(String jobDescription) {
        String[] lines = jobDescription.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() >= 3 && trimmed.length() <= 90
                    && trimmed.matches("(?i).*\\b(engineer|developer|designer|manager|analyst|architect|consultant|scientist|specialist|lead|director|administrator|coordinator|accountant|recruiter)\\b.*")) {
                return trimmed;
            }
        }
        return "";
    }
}
