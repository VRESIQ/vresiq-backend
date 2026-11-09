package in.rithik.resumebuilderapi.service.reconstruction;

import in.rithik.resumebuilderapi.document.Resume;
import in.rithik.resumebuilderapi.dto.AtsIntelligenceModels.AtsFinding;
import in.rithik.resumebuilderapi.dto.AtsIntelligenceModels.DraftAtsReport;
import in.rithik.resumebuilderapi.dto.AtsIntelligenceModels.KeywordMatch;
import in.rithik.resumebuilderapi.dto.AtsIntelligenceModels.SourceAtsReport;
import in.rithik.resumebuilderapi.dto.AtsIntelligenceModels.TailorReport;
import in.rithik.resumebuilderapi.dto.AtsIntelligenceModels.TailoringSuggestion;
import in.rithik.resumebuilderapi.service.RefineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deterministic checks for the ATS intelligence reports. No network, no database. */
class AtsIntelligenceServiceTest {

    private AtsIntelligenceService service;

    @BeforeEach
    void setUp() {
        service = new AtsIntelligenceService(new RefineService());
    }

    private Resume baseResume() {
        return Resume.builder()
                .profileInfo(Resume.ProfileInfo.builder()
                        .fullName("Alex Morgan")
                        .designation("Senior Frontend Engineer")
                        .summary("Frontend engineer with eight years building design systems and data-heavy dashboards for large teams.")
                        .build())
                .contactInfo(Resume.ContactInfo.builder()
                        .email("alex@example.com")
                        .phone("+1 555 0100")
                        .location("Toronto, Canada")
                        .build())
                .workExperience(List.of(
                        Resume.WorkExperience.builder()
                                .company("Northwind")
                                .role("Frontend Engineer")
                                .startDate("Jan 2020")
                                .endDate("Present")
                                .description("Built a React design system used by four product teams. Responsible for the migration to TypeScript.")
                                .build()))
                .education(List.of(Resume.Education.builder()
                        .degree("B.Sc. Computer Science")
                        .institution("State University")
                        .startDate("Sep 2012")
                        .endDate("May 2016")
                        .build()))
                .skills(List.of(
                        new Resume.Skill("React", 80),
                        new Resume.Skill("TypeScript", 75)))
                .build();
    }

    @Test
    @DisplayName("Source report flags a missing email and phone from file text only")
    void sourceReportFlagsMissingContact() {
        SourceAtsReport report = service.analyzeSource(
                "Alex Morgan\nSUMMARY\nFrontend engineer.\nEXPERIENCE\nBuilt things at Northwind.", baseResume());

        assertTrue(report.isAvailable());
        List<String> checks = report.getFindings().stream().map(AtsFinding::getCheck).toList();
        assertTrue(checks.contains("source_missing_email"), "expected missing email finding, got " + checks);
        assertTrue(checks.contains("source_missing_phone"), "expected missing phone finding, got " + checks);
        assertEquals("source", report.getFindings().get(0).getScope());
    }

    @Test
    @DisplayName("Empty extraction is reported as unavailable instead of a fabricated analysis")
    void sourceReportHandlesEmptyText() {
        SourceAtsReport report = service.analyzeSource("   \n  ", baseResume());

        assertFalse(report.isAvailable());
        assertEquals(0, report.getExtractedCharacters());
        assertEquals("source_empty", report.getFindings().get(0).getCheck());
    }

    @Test
    @DisplayName("RefineService section labels map to editable field paths")
    void mapsSectionLabelsToFieldPaths() {
        assertEquals("contactInfo.email", AtsIntelligenceService.fieldPathFor("Contact > Email"));
        assertEquals("profileInfo.summary", AtsIntelligenceService.fieldPathFor("Profile > Summary"));
        assertEquals("workExperience[0].startDate", AtsIntelligenceService.fieldPathFor("Experience 1 > Start date"));
        assertEquals("workExperience[1].description", AtsIntelligenceService.fieldPathFor("Experience 2"));
        assertEquals("education[1].degree", AtsIntelligenceService.fieldPathFor("Education 2 > Degree"));
        assertEquals("skills[0].name", AtsIntelligenceService.fieldPathFor("Skills 1 > Name"));
        assertEquals("projects[2].title", AtsIntelligenceService.fieldPathFor("Projects 3 > Title"));
        assertEquals("template", AtsIntelligenceService.fieldPathFor("Customization > Template"));
        assertEquals("", AtsIntelligenceService.fieldPathFor("Role keywords"));
    }

    @Test
    @DisplayName("Draft report returns a score with field-linked findings")
    void draftReportIsFieldLinked() {
        DraftAtsReport report = service.analyzeDraft(baseResume());

        assertNotNull(report.getFindings());
        assertTrue(report.getScore() > 0 && report.getScore() <= 100, "score out of range: " + report.getScore());
        assertNotNull(report.getReadiness());
        // Every finding that names a section must carry a usable field path or an explicit global marker.
        for (AtsFinding finding : report.getFindings()) {
            assertEquals("draft", finding.getScope());
            assertNotNull(finding.getSection());
        }
        boolean anyLinked = report.getFindings().stream().anyMatch(f -> !f.getFieldPath().isBlank());
        assertTrue(anyLinked, "expected at least one finding linked to an editable field");
    }

    @Test
    @DisplayName("Tailoring only proposes keywords that already have candidate evidence")
    void tailorNeverInventsSkills() {
        String jobDescription = "We are hiring a Senior Frontend Engineer.\n"
                + "Required: strong React and TypeScript experience.\n"
                + "Must have Kubernetes and Go experience.\n"
                + "Preferred: GraphQL.";

        TailorReport report = service.tailor(baseResume(), jobDescription);

        assertTrue(report.isAvailable());
        KeywordMatch react = report.getRequiredKeywords().stream()
                .filter(match -> "react".equals(match.getKeyword())).findFirst().orElseThrow();
        assertTrue(react.isPresent());
        assertFalse(react.getEvidence().isBlank(), "present keyword must carry verbatim evidence");

        KeywordMatch kubernetes = report.getRequiredKeywords().stream()
                .filter(match -> "kubernetes".equals(match.getKeyword())).findFirst().orElseThrow();
        assertFalse(kubernetes.isPresent());
        assertTrue(report.getUnsupported().contains("kubernetes"));

        // No proposal may introduce an unsupported keyword anywhere in the draft.
        for (TailoringSuggestion suggestion : report.getSuggestions()) {
            assertFalse("kubernetes".equalsIgnoreCase(suggestion.getProposal().trim()),
                    "unsupported keyword must never be proposed");
            assertFalse(suggestion.getProposal().toLowerCase().contains("kubernetes"),
                    "unsupported keyword leaked into a proposal: " + suggestion.getProposal());
        }
        assertTrue(report.getSuggestions().stream().allMatch(s -> s.getRationale() != null));
    }

    @Test
    @DisplayName("Keyword proposals keep the candidate's own wording as evidence")
    void keywordSuggestionCarriesEvidence() {
        Resume resume = baseResume();
        // Skills deliberately omit technologies that the experience text proves.
        resume.setSkills(List.of(new Resume.Skill("Communication", 80)));
        String jobDescription = "Senior Frontend Engineer role. Required: React, TypeScript, and design systems experience.";

        TailorReport report = service.tailor(resume, jobDescription);
        TailoringSuggestion keywordSuggestion = report.getSuggestions().stream()
                .filter(s -> "keyword".equals(s.getType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a keyword suggestion"));

        assertFalse(keywordSuggestion.getEvidence().isBlank());
        assertTrue(keywordSuggestion.getEvidence().toLowerCase().contains("react"),
                "evidence must quote the candidate's own text, got: " + keywordSuggestion.getEvidence());
        assertFalse(keywordSuggestion.isRequiresConfirmation(), "listing an existing evidenced skill needs no confirmation");
        assertEquals(1, resume.getSkills().size(), "service must not mutate the resume");
    }

    @Test
    @DisplayName("No job description yields an unavailable tailor report, not an empty score")
    void tailorRequiresJobDescription() {
        TailorReport report = service.tailor(baseResume(), "  ");
        assertFalse(report.isAvailable());
        assertTrue(report.getSuggestions().isEmpty());
    }
}
