package in.rithik.resumebuilderapi.service;

import in.rithik.resumebuilderapi.document.Provider;
import in.rithik.resumebuilderapi.document.User;
import in.rithik.resumebuilderapi.dto.AuthResponse;
import in.rithik.resumebuilderapi.repository.UserRepository;
import in.rithik.resumebuilderapi.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    private AuthResponse generateAuthResponse(User user) {
        String accessToken = jwtUtil.generateToken(user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        user.setRefreshTokenHash(sha256(refreshToken));
        user.setRefreshTokenIssuedAt(LocalDateTime.now());
        user.setRefreshTokenExpiresAt(LocalDateTime.now().plusNanos(jwtUtil.getRefreshTokenExpiryMs() * 1_000_000L));
        userRepository.save(user);

        return AuthResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .profileImageUrl(user.getProfileImageUrl())
                .emailVerified(user.isEmailVerified())
                .subscriptionPlan(user.getSubscriptionPlan())
                .role(user.getRole())
                .mustResetPassword(user.isMustResetPassword())
                .token(accessToken)
                .refreshToken(refreshToken)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
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
