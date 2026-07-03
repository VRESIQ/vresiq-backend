package in.rithik.resumebuilderapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RefineResponse — the full ATS analysis result for one resume.
 *
 * Returned by POST /api/ai/refine. The frontend shows:
 *  - The score ring (0–100)
 *  - Grouped issue list (errors first, then warnings, then tips)
 *  - A 1–2 line overall summary at the top
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefineResponse {
    int atsScore;                  // 0–100, computed by RefineService
    List<RefineSuggestion> issues; // all flagged items, ordered by severity
    String overallFeedback;        // 1-2 sentence plain-English summary shown at the top
    String category;               // detected job category (e.g. "software engineer"), for display
    List<String> strengths;        // list of detected recruiter strengths
}
