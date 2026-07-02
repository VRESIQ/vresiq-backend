package in.rithik.resumebuilderapi.controller;

import in.rithik.resumebuilderapi.document.Resume;
import in.rithik.resumebuilderapi.document.User;
import in.rithik.resumebuilderapi.dto.RefineResponse;
import in.rithik.resumebuilderapi.repository.ResumeRepository;
import in.rithik.resumebuilderapi.service.AiService;
import in.rithik.resumebuilderapi.service.RefineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * AiController — handles AI and ATS endpoints.
 *
 * POST /api/ai/refine/{resumeId}
 *   Runs the backend ATS analysis via RefineService, returns a RefineResponse,
 *   and persists the confirmed score back to the Resume document so the
 *   frontend badge can hydrate from the DB on next page load (zero extra call).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
@Slf4j
public class AiController {

    private final RefineService    refineService;
    private final AiService        aiService;
    private final ResumeRepository resumeRepository;

    @PostMapping("/rewrite")
    public ResponseEntity<?> rewriteContent(@RequestBody Map<String, String> request, Authentication auth) {
        User user    = (User) auth.getPrincipal();
        String content = request.get("content");
        String tone    = request.getOrDefault("tone", "professional");
        try {
            String rewritten = aiService.rewriteContent(user.getId(), content, tone);
            return ResponseEntity.ok(Map.of("rewritten", rewritten));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/refine/{resumeId}")
    public ResponseEntity<?> refineResume(@PathVariable String resumeId, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        if (!"premium".equalsIgnoreCase(currentUser.getSubscriptionPlan())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "ATS Refine is a Pro feature. Please upgrade your plan."));
        }

        // Ownership-scoped lookup avoids global resource probing by ID.
        Optional<Resume> resumeOpt = resumeRepository.findByUserIdAndId(currentUser.getId(), resumeId);
        if (resumeOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Resume not found."));
        }

        Resume resume = resumeOpt.get();
        log.info("Running ATS refine for resume {} by user {}", resumeId, currentUser.getId());
        try {
            RefineResponse result = refineService.analyze(resume);

            // Persist the confirmed score so the frontend badge can hydrate from
            // the resume payload on next page load — no extra refine call needed.
            resume.setLastAtsScore(result.getAtsScore());
            resume.setLastAtsCategory(result.getCategory());
            resumeRepository.save(resume);

            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            log.error("ATS refine failed for resume {}: {}", resumeId, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "ATS analysis failed: " + ex.getMessage()));
        }
    }
}
