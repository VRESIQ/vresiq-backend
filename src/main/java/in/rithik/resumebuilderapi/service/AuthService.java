package in.rithik.resumebuilderapi.service;

import in.rithik.resumebuilderapi.document.User;
import in.rithik.resumebuilderapi.dto.AuthResponse;
import in.rithik.resumebuilderapi.dto.LoginRequest;
import in.rithik.resumebuilderapi.dto.RegisterRequest;
import in.rithik.resumebuilderapi.exception.ResourceExistsException;
import in.rithik.resumebuilderapi.repository.UserRepository;
import in.rithik.resumebuilderapi.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;

    @Value("${app.frontend.public-url}")
    private String frontendPublicUrl;

    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        log.info("Registering new user: {}", email);

        if (userRepository.existsByEmail(email)) {
            throw new ResourceExistsException("Email already exists");
        }

        User newUser = buildUserFromRequest(request);
        userRepository.save(newUser);
        sendVerificationEmail(newUser);
        return toResponse(newUser);
    }

    private void sendVerificationEmail(User user) {
        try {
            String verifyLink = resolveFrontendPublicBaseUrl() + "/verify-email?token=" + user.getVerificationToken() + "&email=" + user.getEmail();
            String html =
                    "<div style=\"font-family:sans-serif;\">" +
                            "<h2>Verify your email</h2>" +
                            "<p>Hi " + user.getName() + ", please confirm your email to activate your VRESIQ account.</p>" +
                            "<p>" +
                            "<a href=\"" + verifyLink + "\" " +
                            "style=\"display:inline-block;padding:10px 16px;background:#6366f1;color:#fff;border-radius:6px;text-decoration:none;\">" +
                            "Verify Email</a>" +
                            "</p>" +
                            "<p>Or paste this link in your browser: " + verifyLink + "</p>" +
                            "<p>This link expires in 24 hours.</p>" +
                            "</div>";

            emailService.sendHtmlEmail(user.getEmail(), "Verify your VRESIQ account", html);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", user.getEmail(), e.getMessage());
            throw new RuntimeException("Failed to send verification email: " + e.getMessage());
        }
    }

    private String resolveFrontendPublicBaseUrl() {
        if (frontendPublicUrl == null || frontendPublicUrl.isBlank()) {
            throw new IllegalStateException("FRONTEND_PUBLIC_URL is not configured.");
        }

        for (String candidateRaw : frontendPublicUrl.split(",")) {
            String candidate = candidateRaw == null ? "" : candidateRaw.trim();
            if (candidate.isEmpty()) continue;
            if (candidate.endsWith("/")) candidate = candidate.substring(0, candidate.length() - 1);

            // Wildcard origins are valid for CORS patterns, but invalid for user-facing links.
            String lower = candidate.toLowerCase();
            if (lower.contains("*") || lower.contains("%2a")) continue;

            try {
                URI uri = URI.create(candidate);
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if (host != null && !host.isBlank() &&
                        ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // try the next configured candidate
            }
        }

        throw new IllegalStateException("Invalid FRONTEND_PUBLIC_URL. Provide an exact origin like https://vresiq-frontend.vercel.app");
    }

    public String verifyEmail(String token, String email) {
        if (token == null || token.isBlank()) return "invalid";

        User user = null;
        if (email != null && !email.isBlank()) {
            user = userRepository.findByEmail(email.toLowerCase().trim()).orElse(null);
        }
        if (user == null) user = userRepository.findByVerificationToken(token).orElse(null);
        if (user == null) return "invalid";
        if (user.isEmailVerified()) return "already_verified";
        if (user.getVerificationToken() == null || !user.getVerificationToken().equals(token)) return "invalid";
        if (user.getVerificationExpires() != null && user.getVerificationExpires().isBefore(LocalDateTime.now())) return "expired";

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationExpires(null);
        user.setLastVerificationSent(null);
        userRepository.save(user);
        return "verified";
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Email not registered. Please create an account."));

        if (!user.isActive()) throw new RuntimeException("Your account has been disabled. Please contact support.");
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UsernameNotFoundException("Incorrect password for this email.");
        }
        if (!user.isEmailVerified()) throw new RuntimeException("Please verify your email before logging in.");

        String accessToken = jwtUtil.generateToken(user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());
        persistRefreshToken(user, refreshToken);

        AuthResponse response = toResponse(user);
        response.setToken(accessToken);
        response.setRefreshToken(refreshToken);
        return response;
    }

    public AuthResponse refreshAccessToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RuntimeException("Refresh token is required.");
        }
        if (!jwtUtil.validateToken(refreshToken) || jwtUtil.isTokenExpired(refreshToken)) {
            throw new RuntimeException("Invalid or expired refresh token.");
        }
        String tokenType = jwtUtil.getTokenTypeFromToken(refreshToken);
        if (!"refresh".equalsIgnoreCase(tokenType)) {
            throw new RuntimeException("Invalid token type.");
        }

        String email = jwtUtil.getUserIdFromToken(refreshToken);
        User user = userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getRefreshTokenHash() == null || user.getRefreshTokenExpiresAt() == null) {
            throw new RuntimeException("Refresh token has been revoked.");
        }
        if (user.getRefreshTokenExpiresAt().isBefore(LocalDateTime.now())) {
            clearRefreshToken(user);
            userRepository.save(user);
            throw new RuntimeException("Refresh token expired.");
        }
        if (!sha256(refreshToken).equals(user.getRefreshTokenHash())) {
            throw new RuntimeException("Refresh token mismatch.");
        }

        // Rotation: old refresh token is invalidated and replaced on every use.
        String newAccessToken = jwtUtil.generateToken(user.getEmail());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getEmail());
        persistRefreshToken(user, newRefreshToken);

        AuthResponse response = toResponse(user);
        response.setToken(newAccessToken);
        response.setRefreshToken(newRefreshToken);
        return response;
    }

    public Map<String, String> logout(Object principalObject) {
        User user = (User) principalObject;
        clearRefreshToken(user);
        userRepository.save(user);
        return Map.of("message", "Logged out successfully");
    }

    public void resendVerification(String email) {
        String normalizedEmail = email.toLowerCase().trim();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new RuntimeException("No account found with that email"));

        if (user.isEmailVerified()) throw new RuntimeException("Email is already verified");

        if (user.getLastVerificationSent() != null &&
                user.getLastVerificationSent().plusSeconds(60).isAfter(LocalDateTime.now())) {
            long secondsLeft = Duration.between(LocalDateTime.now(), user.getLastVerificationSent().plusSeconds(60)).toSeconds();
            throw new RuntimeException("Please wait " + (secondsLeft > 0 ? secondsLeft : 60) + " seconds before requesting another verification email.");
        }

        user.setVerificationToken(UUID.randomUUID().toString());
        user.setVerificationExpires(LocalDateTime.now().plusHours(24));
        user.setLastVerificationSent(LocalDateTime.now());
        userRepository.save(user);
        sendVerificationEmail(user);
    }

    private String hashSha256(String base) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Hashing failed: " + ex.getMessage(), ex);
        }
    }

    public void forgotPassword(String email) {
        String normalizedEmail = email.toLowerCase().trim();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new RuntimeException("No account found with that email"));

        if (user.getLastPasswordResetRequest() != null &&
                user.getLastPasswordResetRequest().plusSeconds(60).isAfter(LocalDateTime.now())) {
            long secondsLeft = Duration.between(LocalDateTime.now(), user.getLastPasswordResetRequest().plusSeconds(60)).toSeconds();
            throw new RuntimeException("Please wait " + (secondsLeft > 0 ? secondsLeft : 60) + " seconds before requesting another password reset.");
        }

        String rawToken = UUID.randomUUID().toString();
        String hashedToken = hashSha256(rawToken);

        user.setPasswordResetToken(hashedToken);
        user.setPasswordResetExpires(LocalDateTime.now().plusHours(1));
        user.setLastPasswordResetRequest(LocalDateTime.now());
        userRepository.save(user);
        
        sendForgotPasswordEmail(user, rawToken);
    }

    private void sendForgotPasswordEmail(User user, String rawToken) {
        try {
            String resetLink = resolveFrontendPublicBaseUrl() + "/reset-password?token=" + rawToken;
            String html =
                    "<div style=\"font-family:sans-serif;\">" +
                            "<h2>Reset your password</h2>" +
                            "<p>Hi " + user.getName() + ", you requested a password reset for your VRESIQ account.</p>" +
                            "<p>Please click the button below to set a new password:</p>" +
                            "<p>" +
                            "<a href=\"" + resetLink + "\" " +
                            "style=\"display:inline-block;padding:10px 16px;background:#6366f1;color:#fff;border-radius:6px;text-decoration:none;\">" +
                            "Reset Password</a>" +
                            "</p>" +
                            "<p>Or paste this link in your browser: " + resetLink + "</p>" +
                            "<p>This link expires in 1 hour.</p>" +
                            "<p>If you did not request this, you can safely ignore this email.</p>" +
                            "</div>";

            emailService.sendHtmlEmail(user.getEmail(), "Reset your VRESIQ password", html);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage());
            throw new RuntimeException("Failed to send password reset email: " + e.getMessage());
        }
    }

    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new RuntimeException("Reset token is required.");
        }
        String hashedToken = hashSha256(token);
        User user = userRepository.findByPasswordResetToken(hashedToken)
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset token."));

        if (user.getPasswordResetExpires() == null || user.getPasswordResetExpires().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Reset token has expired.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpires(null);
        clearRefreshToken(user);
        userRepository.save(user);
    }

    public AuthResponse getProfile(Object principalObject) {
        User user = (User) principalObject;
        return toResponse(user);
    }

    public AuthResponse updateProfile(Object principalObject, Map<String, String> updates) {
        User user = (User) principalObject;

        if (updates.containsKey("name") && updates.get("name") != null && !updates.get("name").trim().isEmpty()) {
            user.setName(updates.get("name"));
        }
        if (updates.containsKey("profileImageUrl")) {
            user.setProfileImageUrl(updates.get("profileImageUrl"));
        }
        if (updates.containsKey("password") && updates.get("password") != null && !updates.get("password").trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(updates.get("password")));
            user.setMustResetPassword(false);
        }

        userRepository.save(user);
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        return AuthResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .profileImageUrl(user.getProfileImageUrl())
                .emailVerified(user.isEmailVerified())
                .subscriptionPlan(user.getSubscriptionPlan())
                .role(user.getRole())
                .mustResetPassword(user.isMustResetPassword())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private User buildUserFromRequest(RegisterRequest request) {
        return User.builder()
                .name(request.getName())
                .email(request.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .profileImageUrl(request.getProfileImageUrl())
                .subscriptionPlan("basic")
                .emailVerified(false)
                .verificationToken(UUID.randomUUID().toString())
                .verificationExpires(LocalDateTime.now().plusHours(24))
                .lastVerificationSent(LocalDateTime.now())
                .build();
    }

    private void persistRefreshToken(User user, String refreshToken) {
        user.setRefreshTokenHash(sha256(refreshToken));
        user.setRefreshTokenIssuedAt(LocalDateTime.now());
        user.setRefreshTokenExpiresAt(LocalDateTime.now().plusNanos(jwtUtil.getRefreshTokenExpiryMs() * 1_000_000L));
        userRepository.save(user);
    }

    private void clearRefreshToken(User user) {
        user.setRefreshTokenHash(null);
        user.setRefreshTokenIssuedAt(null);
        user.setRefreshTokenExpiresAt(null);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unable to hash token", e);
        }
    }
}
