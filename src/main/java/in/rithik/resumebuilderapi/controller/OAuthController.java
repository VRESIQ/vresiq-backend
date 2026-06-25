package in.rithik.resumebuilderapi.controller;

import in.rithik.resumebuilderapi.document.User;
import in.rithik.resumebuilderapi.dto.AuthResponse;
import in.rithik.resumebuilderapi.service.OAuthService;
import in.rithik.resumebuilderapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class OAuthController {

    private final OAuthService oAuthService;
    private final UserRepository userRepository;

    @PostMapping("/oauth/google")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Token is required"));
        }
        AuthResponse response = oAuthService.loginOrRegisterGoogle(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/oauth/microsoft")
    public ResponseEntity<?> microsoftLogin(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Token is required"));
        }
        AuthResponse response = oAuthService.loginOrRegisterMicrosoft(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/oauth/apple")
    public ResponseEntity<?> appleLogin(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String email = body.get("email");
        String name = body.get("name");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Token is required"));
        }
        AuthResponse response = oAuthService.loginOrRegisterApple(token, email, name);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/phone/verify")
    public ResponseEntity<?> phoneVerify(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String phone = body.get("phone");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Token is required"));
        }
        AuthResponse response = oAuthService.loginOrRegisterPhone(token, phone);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/providers")
    public ResponseEntity<?> getProviders(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }
        User user = (User) authentication.getPrincipal();
        // Refresh from DB to make sure we have latest info
        user = userRepository.findById(user.getId()).orElse(user);

        Map<String, Object> response = new HashMap<>();
        Map<String, String> social = user.getSocialProviders();
        response.put("google", social != null && social.containsKey("google"));
        response.put("microsoft", social != null && social.containsKey("microsoft"));
        response.put("apple", social != null && social.containsKey("apple"));
        response.put("phone", social != null && social.containsKey("phone"));
        response.put("local", user.getPassword() != null);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/providers/unlink")
    public ResponseEntity<?> unlinkProvider(Authentication authentication, @RequestBody Map<String, String> body) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }
        String provider = body.get("provider");
        if (provider == null || provider.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Provider parameter is required"));
        }
        
        User user = (User) authentication.getPrincipal();
        user = userRepository.findById(user.getId()).orElse(user);

        if (user.getSocialProviders() != null && user.getSocialProviders().containsKey(provider.toLowerCase())) {
            // Check if user has at least one authentication method left
            boolean hasLocal = user.getPassword() != null;
            long socialCount = user.getSocialProviders().size();
            if (!hasLocal && socialCount <= 1) {
                return ResponseEntity.badRequest().body(Map.of("message", "Cannot unlink the only login method. Set a password first."));
            }

            user.getSocialProviders().remove(provider.toLowerCase());
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("success", true, "message", provider + " unlinked successfully"));
        }

        return ResponseEntity.badRequest().body(Map.of("message", "Provider not linked or already unlinked"));
    }
}
