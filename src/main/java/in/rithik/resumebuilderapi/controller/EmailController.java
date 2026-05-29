package in.rithik.resumebuilderapi.controller;

import in.rithik.resumebuilderapi.document.User;
import in.rithik.resumebuilderapi.service.EmailService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/email")
@Slf4j
public class EmailController {

    private final EmailService emailService;

    @PostMapping(value = "/send-resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> sendResumeByEmail(
            @RequestPart("recipientEmail") String recipientEmail,
            @RequestPart("subject") String subject,
            @RequestPart("message") String message,
            @RequestPart("pdfFile") MultipartFile pdfFile,
            Authentication authentication
    ) throws IOException, MessagingException {

        Map<String, Object> response = new HashMap<>();

        // Guard: only premium users can send resumes by email.
        // The frontend already hides the button, but we enforce it here too
        // because anyone can call the API directly without the UI.
        User currentUser = (User) authentication.getPrincipal();
        if (!"premium".equalsIgnoreCase(currentUser.getSubscriptionPlan())) {
            response.put("success", "false");
            response.put("message", "Email sending is a Pro feature. Please upgrade your plan.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        if (Objects.isNull(recipientEmail) || Objects.isNull(pdfFile)) {
            response.put("success", "false");
            response.put("message", "Missing required fields: recipientEmail and pdfFile are required.");
            return ResponseEntity.badRequest().body(response);
        }

        // Validate MIME type
        String contentType = pdfFile.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            response.put("success", "false");
            response.put("message", "Invalid file type. Only PDF documents are allowed.");
            return ResponseEntity.badRequest().body(response);
        }

        // Validate file extension
        String originalFilename = pdfFile.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            response.put("success", "false");
            response.put("message", "Invalid file extension. The file name must end with '.pdf'.");
            return ResponseEntity.badRequest().body(response);
        }

        // Validate PDF magic bytes (%PDF)
        byte[] pdfBytes = pdfFile.getBytes();
        if (pdfBytes.length < 4 || 
            pdfBytes[0] != 0x25 || // '%'
            pdfBytes[1] != 0x50 || // 'P'
            pdfBytes[2] != 0x44 || // 'D'
            pdfBytes[3] != 0x46) { // 'F'
            response.put("success", "false");
            response.put("message", "Invalid PDF file header. The file content does not represent a valid PDF.");
            return ResponseEntity.badRequest().body(response);
        }

        log.info("Received sendResumeByEmail request: recipient={}, subjectLength={}, pdfFilename={}, pdfSize={} bytes, user={}", 
                recipientEmail, 
                Objects.nonNull(subject) ? subject.length() : 0, 
                originalFilename, 
                pdfFile.getSize(),
                currentUser.getEmail());

        String filename = originalFilename;

        String emailSubject = Objects.nonNull(subject) ? subject : "Resume Application";
        String emailBody = Objects.nonNull(message) ? message : "Please find my resume attached.\n\nBest Regards";

        try {
            emailService.sendEmailWithAttachment(recipientEmail, emailSubject, emailBody, pdfBytes, filename);
            response.put("success", "true");
            response.put("message", "Resume sent successfully to " + recipientEmail);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("Failed to send resume email to {}: ", recipientEmail, ex);
            response.put("success", "false");
            response.put("message", "Failed to send email: " + ex.getMessage());
            response.put("details", ex.toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
