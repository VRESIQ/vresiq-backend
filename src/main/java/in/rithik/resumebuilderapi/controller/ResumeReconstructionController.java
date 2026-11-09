package in.rithik.resumebuilderapi.controller;

import in.rithik.resumebuilderapi.dto.AtsIntelligenceModels.AtsAnalysisRequest;
import in.rithik.resumebuilderapi.dto.AtsIntelligenceModels.AtsAnalysisResponse;
import in.rithik.resumebuilderapi.dto.ReconstructionModels.ReconstructionResponse;
import in.rithik.resumebuilderapi.service.reconstruction.AtsIntelligenceService;
import in.rithik.resumebuilderapi.service.reconstruction.ReconstructionPipeline;
import in.rithik.resumebuilderapi.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/reconstruct")
@Slf4j
public class ResumeReconstructionController {

    private final ReconstructionPipeline pipeline;
    private final AtsIntelligenceService atsIntelligence;
    private final AiService aiService;

    @PostMapping("/upload")
    public ResponseEntity<ReconstructionResponse> uploadResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "profile", defaultValue = "PROFESSIONAL") String profile) {
        
        // File names can contain personal information; log only safe request metadata.
        log.info("Admin resume reconstruction upload received; bytes={}, contentType={}", file.getSize(), file.getContentType());

        // Security: file size limit (10MB)
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("File size exceeds the 10MB limit.");
        }

        // Security: restrict MIME types
        String mimeType = file.getContentType();
        if (mimeType == null || 
            (!mimeType.equalsIgnoreCase("application/pdf") && 
             !mimeType.equalsIgnoreCase("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))) {
            throw new IllegalArgumentException("Unsupported file type. Only PDF and DOCX documents are allowed.");
        }

        try {
            ReconstructionResponse response = pipeline.execute(file.getInputStream(), mimeType, profile);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Failed to read reconstruction upload stream; exceptionType={}", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Draft-side ATS analysis plus optional job-description tailoring.
     *
     * <p>Deterministic only: no external model is called, nothing is written to the database,
     * and every tailoring item is a proposal the candidate must accept or reject.
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody AtsAnalysisRequest request) {
        if (request == null || request.getResume() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "A resume draft is required for analysis."));
        }
        long started = System.currentTimeMillis();
        try {
            var draft = atsIntelligence.analyzeDraft(request.getResume());
            var tailor = atsIntelligence.tailor(request.getResume(), request.getJobDescription());
            log.info("Reconstruction ATS analysis completed; draftFindings={}, tailorAvailable={}",
                    draft.getFindings() == null ? 0 : draft.getFindings().size(), tailor.isAvailable());
            return ResponseEntity.ok(AtsAnalysisResponse.builder()
                    .draft(draft)
                    .tailor(tailor)
                    .durationMs(System.currentTimeMillis() - started)
                    .disclaimer("These checks are deterministic, explainable heuristics. They do not reproduce any employer's proprietary screening system and do not predict hiring outcomes.")
                    .build());
        } catch (RuntimeException exception) {
            log.error("Reconstruction ATS analysis failed; exceptionType={}", exception.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "ATS analysis could not be completed for this draft."));
        }
    }

    @PostMapping("/enhance-bullet")
    public ResponseEntity<Map<String, String>> enhanceBullet(
            @RequestBody Map<String, String> payload) {
        String bulletText = payload.get("bulletText");
        String tone = payload.getOrDefault("tone", "professional");
        String userId = "6a36e4072796a30eba931275"; // Default Admin User ID

        if (bulletText == null || bulletText.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Bullet point text is required."));
        }

        try {
            String enhanced = aiService.rewriteContent(userId, bulletText, tone);
            return ResponseEntity.ok(Map.of("enhancedText", enhanced));
        } catch (Exception e) {
            log.error("Failed to enhance bullet; exceptionType={}", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Unable to enhance bullet text."));
        }
    }
}
