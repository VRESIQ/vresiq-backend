package in.rithik.resumebuilderapi.service;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * AtsConfig — Java POJO representation of atsRules.json.
 *
 * This class is the backend's view of the single source of truth for all
 * ATS scoring constants. It is populated at startup by AtsRulesLoader,
 * which reads the file from the classpath. RefineService and AtsKeywords
 * read from an instance of this class — never from hardcoded constants.
 *
 * Field names use camelCase to match the JSON keys in atsRules.json.
 */
@Data
@NoArgsConstructor
public class AtsConfig {

    /** Length / count thresholds */
    private Thresholds thresholds = new Thresholds();

    /** Score classification thresholds (85 = strong, 70 = good, 50 = moderate) */
    private ScoreBands scoreBands = new ScoreBands();

    /** Point deductions — keys match the camelCase names in atsRules.json */
    private Map<String, Integer> deductions;

    /** Template-specific parse-risk deductions (keyed by template enum name) */
    private Map<String, Integer> templateRisk;

    /** Human-readable template display names */
    private Map<String, String> templateNames;

    /** ATS keyword lists keyed by job category (e.g. "software engineer") */
    private Map<String, List<String>> categories;

    // ─── Nested config types ──────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    public static class Thresholds {
        private int minSummaryLen         = 80;
        private int minExperienceDescLen  = 45;
        private int minProjectDescLen     = 35;
        private int tooFewSkillsCount     = 5;
        private int tooManyInterestsCount = 6;
        private int maxKeywordDisplay     = 8;
    }

    @Data
    @NoArgsConstructor
    public static class ScoreBands {
        private int strong   = 85;
        private int good     = 70;
        private int moderate = 50;
    }

    // ─── Convenience deduction accessors ─────────────────────────────────────

    /** Returns the deduction for the given key, or 0 if not found. */
    public int d(String key) {
        if (deductions == null) return 0;
        return deductions.getOrDefault(key, 0);
    }
}
