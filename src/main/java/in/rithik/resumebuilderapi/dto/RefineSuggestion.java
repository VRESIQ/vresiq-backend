package in.rithik.resumebuilderapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RefineSuggestion — one flagged issue found in the resume.
 *
 * Each suggestion tells the user:
 *  - WHERE the problem is (section + field)
 *  - WHAT the problem is (the original bad text)
 *  - HOW to fix it (the suggestion text)
 *  - HOW MANY points were deducted
 *  - HOW BAD it is (severity: error > warning > tip)
 *
 * These are displayed in the frontend ATS panel grouped by severity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefineSuggestion {
    String type;       // "passive_voice" | "filler_word" | "missing_metric" | "ats_keyword" | "weak_summary" | "empty_section" | "date_error"
    String section;    // e.g. "Experience[0]", "Summary", "Skills"
    String original;   // the flagged text (shown to user so they know what to fix)
    String suggestion; // actionable fix guidance
    String severity;   // "error" | "warning" | "tip"
    Integer points;    // score deduction for this issue
}
