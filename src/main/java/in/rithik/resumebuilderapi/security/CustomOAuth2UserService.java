package in.rithik.resumebuilderapi.security;

import in.rithik.resumebuilderapi.document.Provider;
import in.rithik.resumebuilderapi.document.User;
import in.rithik.resumebuilderapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        
        String clientRegistrationId = userRequest.getClientRegistration().getRegistrationId();
        Provider provider = Provider.valueOf(clientRegistrationId.toUpperCase());
        
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String email = null;
        String name = null;
        String picture = null;
        String providerId = oAuth2User.getName();

        if (provider == Provider.GOOGLE) {
            email = (String) attributes.get("email");
            name = (String) attributes.get("name");
            picture = (String) attributes.get("picture");
        }

        if (email == null) {
            throw new OAuth2AuthenticationException("Email not found from OAuth2 provider");
        }

        String normalizedEmail = email.toLowerCase().trim();
        Optional<User> existingUserOpt = userRepository.findByEmail(normalizedEmail);
        User user;
        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
            log.info("OAuth: Linking provider {} to existing user email: {}", provider, normalizedEmail);
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
            log.info("OAuth: Creating new user via provider {}: {}", provider, normalizedEmail);
            Map<String, String> socialProviders = new HashMap<>();
            socialProviders.put(provider.name().toLowerCase(), providerId);

            user = User.builder()
                    .name(name != null ? name : normalizedEmail.split("@")[0])
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

        Map<String, Object> customAttributes = new HashMap<>(attributes);
        customAttributes.put("db_user_email", user.getEmail());

        String keyName = "email";

        return new DefaultOAuth2User(
                oAuth2User.getAuthorities(),
                customAttributes,
                keyName
        );
    }
}
