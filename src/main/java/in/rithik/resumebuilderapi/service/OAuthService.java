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
    private final RestTemplate restTemplate = new RestTemplate();

    public AuthResponse loginOrRegisterPhone(String token, String phone) {
        log.info("Processing Phone OTP login with Firebase Admin SDK");
        try {
            com.google.firebase.auth.FirebaseToken decodedToken = 
                    com.google.firebase.auth.FirebaseAuth.getInstance().verifyIdToken(token);
            
            String phoneNum = (String) decodedToken.getClaims().get("phone_number");
            String providerId = decodedToken.getUid();
            String email = decodedToken.getEmail();

            String verifiedPhone = phoneNum != null ? phoneNum : phone;
            if (verifiedPhone == null || verifiedPhone.isBlank()) {
                throw new RuntimeException("Could not retrieve phone number from Firebase token");
            }

            Optional<User> existingUserOpt = userRepository.findByPhone(verifiedPhone);
            User user;
            if (existingUserOpt.isPresent()) {
                user = existingUserOpt.get();
                log.info("Found existing user with phone number: {}", verifiedPhone);
            } else {
                if (email != null && !email.isBlank()) {
                    Optional<User> userByEmail = userRepository.findByEmail(email.toLowerCase().trim());
                    if (userByEmail.isPresent()) {
                        user = userByEmail.get();
                        user.setPhone(verifiedPhone);
                        log.info("Linked phone to existing user by email: {}", email);
                    } else {
                        user = createNewPhoneUser(verifiedPhone, email, providerId);
                    }
                } else {
                    user = createNewPhoneUser(verifiedPhone, null, providerId);
                }
            }

            if (user.getSocialProviders() == null) {
                user.setSocialProviders(new HashMap<>());
            }
            user.getSocialProviders().put("phone", providerId);
            user.setProvider(Provider.PHONE);
            user.setProviderId(providerId);
            userRepository.save(user);

            return generateAuthResponse(user);
        } catch (Exception e) {
            log.error("Firebase ID Token verification failed: {}", e.getMessage());
            throw new RuntimeException("Invalid Firebase Phone credentials: " + e.getMessage());
        }
    }

    private User createNewPhoneUser(String phone, String email, String providerId) {
        String finalEmail = email != null ? email.toLowerCase().trim() : "phone_" + providerId + "@vresiq.internal";
        String name = "User " + phone;
        return User.builder()
                .name(name)
                .email(finalEmail)
                .phone(phone)
                .password(null)
                .provider(Provider.PHONE)
                .providerId(providerId)
                .emailVerified(true)
                .subscriptionPlan("basic")
                .active(true)
                .build();
    }

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
