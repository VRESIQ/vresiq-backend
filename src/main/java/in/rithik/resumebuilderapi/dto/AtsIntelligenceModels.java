package in.rithik.resumebuilderapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * ATS intelligence DTOs for the Reconstruction Studio.
 *
 * <p>Two reports are deliberately kept separate:
 * <ul>
 *   <li>{@link SourceAtsReport} describes risks in the <em>uploaded file</em> (what an ATS
 *       parser would see before any VResIQ editing).</li>
 *   <li>{@link DraftAtsReport} describes the <em>structured editable draft</em>.</li>
 * </ul>
 * Source-file problems must never be reported as draft problems, and vice versa.
 *
 * <p>All findings are produced by deterministic checks. No proprietary employer system is
 * reproduced and no score is asserted without a failed-check trail behind it.
 */
public class AtsIntelligenceModels {

    /** One actionable finding, optionally linked to an editable field. */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class AtsFinding {
        private String id;
        /** "source" (uploaded file) or "draft" (editable VResIQ resume). */
        private String scope;
        /** "error" | "warning" | "tip". */
        private String severity;
        /** Human readable section label, e.g. "Experience". */
        private String section;
        /**
         * Machine address of the editable field, e.g. "contactInfo.email",
         * "workExperience[1].description". Empty when the finding has no single field.
         */
        private String fieldPath;
        /** The flagged text or value (may be blank for missing-data findings). */
        private String original;
        private String message;
        private String recommendation;
        /** Score weight for draft checks; null for source findings. */
        private Integer points;
        /** ATS check identifier, e.g. "missing_email", "source_no_headings". */
        private String check;
    }

    /** Report for the uploaded source document. */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class SourceAtsReport {
        private boolean available;
        private int extractedCharacters;
        private int foundSections;
        /** Canonical section headings expected but not detected in the source text. */
        private List<String> missingSectionHeadings;
        private List<AtsFinding> findings;
        private List<String> notes;
        /** Plain-language summary of what the source analysis can and cannot claim. */
        private String summary;
    }

    /** Report for the structured draft. */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class DraftAtsReport {
        private int score;
        /** "strong" | "good" | "moderate" | "needs_work". */
        private String readiness;
        private String category;
        private String summary;
        private List<String> strengths;
        private List<AtsFinding> findings;
        private KeywordCoverage keywordCoverage;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class KeywordCoverage {
        private int total;
        private int present;
        private int missing;
        private List<KeywordMatch> matches;
    }

    /** A single job-description keyword and whether the candidate's own text supports it. */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class KeywordMatch {
        private String keyword;
        /** "required" | "preferred" | "context". */
        private String importance;
        private boolean present;
        /**
         * Verbatim excerpt from the candidate's resume that supports this keyword.
         * Empty when the candidate has no supporting evidence - such keywords must not
         * be added automatically.
         */
        private String evidence;
        private String fieldPath;
    }

    /** Proposed, never auto-applied, tailoring change. */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TailoringSuggestion {
        private String id;
        /** "keyword" | "bullet" | "summary" | "reorder". */
        private String type;
        private String section;
        private String fieldPath;
        /** Candidate-authored text the proposal is derived from. */
        private String evidence;
        /** Proposed text. Never contains facts absent from the evidence. */
        private String proposal;
        private String rationale;
        /**
         * True when the proposal cannot be applied without the candidate confirming they
         * actually possess the described experience.
         */
        private boolean requiresConfirmation;
    }

    /** Result of matching a job description against the candidate's own evidence. */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TailorReport {
        private boolean available;
        private String detectedRole;
        private List<KeywordMatch> requiredKeywords;
        private List<KeywordMatch> preferredKeywords;
        private List<TailoringSuggestion> suggestions;
        private List<String> unsupported;
        private List<String> notes;
        private String summary;
    }

    /** Request body for draft analysis and job tailoring. */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AtsAnalysisRequest {
        private in.rithik.resumebuilderapi.document.Resume resume;
        /** Optional target job description. Blank disables tailoring. */
        private String jobDescription;
    }

    /** Aggregate response for POST /api/admin/reconstruct/analyze. */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class AtsAnalysisResponse {
        @Builder.Default
        private List<AtsFinding> sourceFindings = new ArrayList<>();
        private DraftAtsReport draft;
        private TailorReport tailor;
        private long durationMs;
        /** Explicit statement of what these reports are and are not. */
        private String disclaimer;
    }
}
