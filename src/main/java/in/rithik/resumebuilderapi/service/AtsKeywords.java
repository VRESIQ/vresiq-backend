package in.rithik.resumebuilderapi.service;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * AtsKeywords — provides job-category keyword lists and category detection.
 *
 * ─── SINGLE SOURCE OF TRUTH ────────────────────────────────────────────────
 * All keyword lists are loaded from atsRules.json via AtsRulesLoader.
 * Do NOT add hardcoded keyword lists here. Edit atsRules.json instead.
 *
 * The category detection logic (detectCategory) is language-specific string
 * matching — it cannot be expressed in JSON, so it lives here as code, but
 * it operates on the JSON-sourced category keys.
 */
@Component
public class AtsKeywords {

    /**
     * Returns the keyword list for each job category.
     * Sourced from atsRules.json via AtsRulesLoader — no hardcoded data.
     */
    public static Map<String, List<String>> getCategoryKeywords() {
        return AtsRulesLoader.getConfig().getCategories();
    }

    /**
     * Finds the best-matching ATS category for a given job designation.
     *
     * Strategy (mirrors atsScorer.js detectCategory):
     *   1. Full phrase match — all words in the category key appear in the title
     *      e.g. "software engineer" matches "Senior Software Engineer"
     *   2. Single-word fallback — any one word from the category appears in title
     *      e.g. "designer" in "UI/UX Designer"
     *
     * Returns the matching category key, or null if none found.
     */
    public static String detectCategory(String designation) {
        if (designation == null || designation.isBlank()) return null;
        String lower = designation.toLowerCase();
        Map<String, List<String>> cats = getCategoryKeywords();
        if (cats == null) return null;

        // Pass 1 — full phrase match
        for (String category : cats.keySet()) {
            String[] parts = category.split(" ");
            if (Arrays.stream(parts).allMatch(lower::contains)) return category;
        }
        // Pass 2 — any-word fallback
        for (String category : cats.keySet()) {
            String[] parts = category.split(" ");
            if (Arrays.stream(parts).anyMatch(lower::contains)) return category;
        }
        return null;
    }
}
