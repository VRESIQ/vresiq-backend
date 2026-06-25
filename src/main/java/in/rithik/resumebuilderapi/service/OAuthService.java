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

    public AuthResponse loginOrRegisterGoogle(String token) {
        log.info("Verifying Google token");
        try {
            com.google.api.client.json.gson.GsonFactory jsonFactory = com.google.api.client.json.gson.GsonFactory.getDefaultInstance();
            com.google.api.client.http.javanet.NetHttpTransport transport = new com.google.api.client.http.javanet.NetHttpTransport();
            
            String googleClientId = System.getenv("VITE_GOOGLE_CLIENT_ID");
            if (googleClientId == null) {
                googleClientId = System.getenv("GOOGLE_CLIENT_ID");
            }
            
            com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier verifier = 
                new com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier.Builder(transport, jsonFactory)
                    .setAudience(googleClientId != null ? java.util.Collections.singletonList(googleClientId) : java.util.Collections.emptyList())
                    .build();

            com.google.api.client.googleapis.auth.oauth2.GoogleIdToken idToken = verifier.verify(token);
            if (idToken == null) {
                throw new RuntimeException("Invalid Google cryptographic token");
            }

            com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String picture = (String) payload.get("picture");
            String providerId = payload.getSubject();
            return processSocialUser(email, name, picture, Provider.GOOGLE, providerId);
        } catch (Exception e) {
            log.error("Google ID Token verification failed: {}", e.getMessage());
            throw new RuntimeException("Google token validation failed: " + e.getMessage());
        }
    }

    public AuthResponse loginOrRegisterMicrosoft(String token) {
        log.info("Verifying Microsoft Entra ID Token cryptographically");
        try {
            Map<String, Object> claims = verifyMicrosoftJwtSignature(token);
            String email = (String) claims.get("email");
            if (email == null) {
                email = (String) claims.get("preferred_username");
            }
            String name = (String) claims.get("name");
            String providerId = (String) claims.get("sub");

            if (email == null) {
                throw new RuntimeException("Could not retrieve email from Microsoft identity token");
            }

            return processSocialUser(email, name, null, Provider.MICROSOFT, providerId);
        } catch (Exception e) {
            log.error("Microsoft ID Token verification failed: {}", e.getMessage());
            throw new RuntimeException("Invalid Microsoft identity token signature, issuer, or audience: " + e.getMessage());
        }
    }

    private Map<String, Object> verifyMicrosoftJwtSignature(String token) throws Exception {
        String microsoftKeysUrl = "https://login.microsoftonline.com/common/discovery/v2.0/keys";
        Map<String, Object> jwks = restTemplate.getForObject(microsoftKeysUrl, Map.class);
        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");

        String[] parts = token.split("\\.");
        String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Object> header = mapper.readValue(headerJson, Map.class);
        String kid = (String) header.get("kid");

        Map<String, Object> matchingKey = keys.stream()
                .filter(k -> kid.equals(k.get("kid")))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Matching key not found in Microsoft JWK set"));

        String nStr = (String) matchingKey.get("n");
        String eStr = (String) matchingKey.get("e");

        byte[] nBytes = Base64.getUrlDecoder().decode(nStr);
        byte[] eBytes = Base64.getUrlDecoder().decode(eStr);

        BigInteger n = new BigInteger(1, nBytes);
        BigInteger e = new BigInteger(1, eBytes);

        RSAPublicKeySpec spec = new RSAPublicKeySpec(n, e);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = factory.generatePublic(spec);

        String microsoftClientId = System.getenv("VITE_MICROSOFT_CLIENT_ID");
        if (microsoftClientId == null) {
            microsoftClientId = System.getenv("MICROSOFT_CLIENT_ID");
        }

        io.jsonwebtoken.Claims claims = io.jsonwebtoken.Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        // Validate Issuer
        String issuer = claims.getIssuer();
        if (issuer == null || !issuer.startsWith("https://login.microsoftonline.com/") || !issuer.endsWith("/v2.0")) {
            throw new RuntimeException("Microsoft ID token issuer invalid");
        }

        // Validate Audience
        if (microsoftClientId != null && !claims.getAudience().contains(microsoftClientId)) {
            throw new RuntimeException("Microsoft ID token audience mismatch");
        }

        return claims;
    }

    public AuthResponse loginOrRegisterApple(String token, String providedEmail, String providedName) {
        log.info("Verifying Apple ID Token cryptographically");
        try {
            Map<String, Object> claims = verifyAppleJwtSignature(token);
            String email = (String) claims.get("email");
            if (email == null) {
                email = providedEmail;
            }
            String providerId = (String) claims.get("sub");
            String name = providedName != null ? providedName : (email != null ? email.split("@")[0] : "Apple User");

            if (email == null) {
                throw new RuntimeException("Could not retrieve email from Apple identity token");
            }

            return processSocialUser(email, name, null, Provider.APPLE, providerId);
        } catch (Exception e) {
            log.error("Apple ID Token verification failed: {}", e.getMessage());
            throw new RuntimeException("Invalid Apple identity token signature, issuer, or audience: " + e.getMessage());
        }
    }

    private Map<String, Object> verifyAppleJwtSignature(String token) throws Exception {
        String appleKeysUrl = "https://appleid.apple.com/auth/keys";
        Map<String, Object> jwks = restTemplate.getForObject(appleKeysUrl, Map.class);
        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");

        String[] parts = token.split("\\.");
        String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Object> header = mapper.readValue(headerJson, Map.class);
        String kid = (String) header.get("kid");

        Map<String, Object> matchingKey = keys.stream()
                .filter(k -> kid.equals(k.get("kid")))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Matching key not found in Apple JWK set"));

        String nStr = (String) matchingKey.get("n");
        String eStr = (String) matchingKey.get("e");

        byte[] nBytes = Base64.getUrlDecoder().decode(nStr);
        byte[] eBytes = Base64.getUrlDecoder().decode(eStr);

        BigInteger n = new BigInteger(1, nBytes);
        BigInteger e = new BigInteger(1, eBytes);

        RSAPublicKeySpec spec = new RSAPublicKeySpec(n, e);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = factory.generatePublic(spec);

        String appleClientId = System.getenv("VITE_APPLE_CLIENT_ID");
        if (appleClientId == null) {
            appleClientId = System.getenv("APPLE_CLIENT_ID");
        }

        io.jsonwebtoken.Claims claims = io.jsonwebtoken.Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .requireIssuer("https://appleid.apple.com")
                .build()
                .parseClaimsJws(token)
                .getBody();

        if (appleClientId != null && !claims.getAudience().contains(appleClientId)) {
            throw new RuntimeException("Apple ID token audience mismatch");
        }

        return claims;
    }

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

    private AuthResponse processSocialUser(String email, String name, String picture, Provider provider, String providerId) {
        String normalizedEmail = email.toLowerCase().trim();
        Optional<User> existingUserOpt = userRepository.findByEmail(normalizedEmail);

        User user;
        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
            log.info("Linking provider {} to existing user email: {}", provider, normalizedEmail);
            if (user.getSocialProviders() == null) {
                user.setSocialProviders(new HashMap<>());
            }
            user.getSocialProviders().put(provider.name().toLowerCase(), providerId);
            if ((user.getProfileImageUrl() == null || user.getProfileImageUrl().isBlank()) && picture != null) {
                user.setProfileImageUrl(picture);
            }
            user.setProvider(provider);
            user.setProviderId(providerId);
            user.setEmailVerified(true);
            userRepository.save(user);
        } else {
            log.info("Creating new user via provider {}: {}", provider, normalizedEmail);
            Map<String, String> socialProviders = new HashMap<>();
            socialProviders.put(provider.name().toLowerCase(), providerId);

            user = User.builder()
                    .name(name)
                    .email(normalizedEmail)
                    .password(null)
                    .profileImageUrl(picture)
                    .avatarUrl(picture)
                    .provider(provider)
                    .providerId(providerId)
                    .socialProviders(socialProviders)
                    .emailVerified(true)
                    .subscriptionPlan("basic")
                    .active(true)
                    .build();
            userRepository.save(user);
        }

        return generateAuthResponse(user);
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
