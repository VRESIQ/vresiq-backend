package in.rithik.resumebuilderapi.controller;

import in.rithik.resumebuilderapi.document.User;
import in.rithik.resumebuilderapi.document.Resume;
import in.rithik.resumebuilderapi.document.Payment;
import in.rithik.resumebuilderapi.document.UserAiStats;
import in.rithik.resumebuilderapi.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getAnalytics() {
        return ResponseEntity.ok(adminService.getAnalytics());
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getUsers() {
        return ResponseEntity.ok(adminService.getUsers());
    }

    @PutMapping("/users/{userId}/toggle-status")
    public ResponseEntity<User> toggleUserStatus(@PathVariable String userId, Authentication authentication) {
        return ResponseEntity.ok(adminService.toggleUserStatus(userId, authentication.getPrincipal()));
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable String userId, Authentication authentication) {
        adminService.deleteUser(userId, authentication.getPrincipal());
        return ResponseEntity.ok(Map.of("message", "User and all associated data deleted successfully"));
    }

    @GetMapping("/resumes")
    public ResponseEntity<List<Resume>> getResumes() {
        return ResponseEntity.ok(adminService.getResumes());
    }

    @DeleteMapping("/resumes/{resumeId}")
    public ResponseEntity<Map<String, String>> deleteResume(@PathVariable String resumeId) {
        adminService.deleteResume(resumeId);
        return ResponseEntity.ok(Map.of("message", "Resume deleted successfully"));
    }

    @GetMapping("/payments")
    public ResponseEntity<List<Payment>> getPayments() {
        return ResponseEntity.ok(adminService.getPayments());
    }

    @GetMapping("/ai-stats")
    public ResponseEntity<List<UserAiStats>> getAiStats() {
        return ResponseEntity.ok(adminService.getAiStats());
    }
}
