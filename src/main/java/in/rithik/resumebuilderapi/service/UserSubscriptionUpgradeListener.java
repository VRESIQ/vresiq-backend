package in.rithik.resumebuilderapi.service;

import in.rithik.resumebuilderapi.document.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class UserSubscriptionUpgradeListener implements BeforeConvertCallback<User> {

    private final MongoTemplate mongoTemplate;
    private final EmailService emailService;
    private final String frontendPublicUrl;

    @Autowired
    public UserSubscriptionUpgradeListener(
            MongoTemplate mongoTemplate,
            EmailService emailService,
            @Value("${FRONTEND_PUBLIC_URL:}") String frontendPublicUrl) {
        this.mongoTemplate = mongoTemplate;
        this.emailService = emailService;
        this.frontendPublicUrl = frontendPublicUrl;
    }

    @Override
    public User onBeforeConvert(User user, String collection) {
        if (user.getId() == null) {
            return user;
        }

        // 1. Fetch current database state to check the transition
        User existingUser = mongoTemplate.findById(user.getId(), User.class);
        if (existingUser == null) {
            return user;
        }

        boolean wasPremium = "premium".equalsIgnoreCase(existingUser.getSubscriptionPlan());
        boolean isPremiumNow = "premium".equalsIgnoreCase(user.getSubscriptionPlan());

        // Trigger only when transitioning from Basic (or any non-premium) to Premium
        if (!wasPremium && isPremiumNow) {
            log.info("Subscription upgrade transition detected for user {}: {} -> {}", user.getId(), existingUser.getSubscriptionPlan(), user.getSubscriptionPlan());
            
            try {
                sendUpgradeEmail(user);
                log.info("Successfully sent Premium upgrade email to user: {}", user.getEmail());
            } catch (Exception e) {
                // Email delivery failure must NOT roll back the subscription update
                log.error("Failed to send Premium upgrade email to user: {}. Error: {}", user.getEmail(), e.getMessage(), e);
            }
        }

        return user;
    }

    private void sendUpgradeEmail(User user) throws Exception {
        String loginLink = resolveLoginLink();
        String formattedDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String html =
                "<div style=\"font-family:sans-serif; max-width:600px; margin:0 auto; padding:20px; border:1px solid #e5e7eb; border-radius:8px;\">" +
                "  <h2 style=\"color:#6366f1; margin-bottom:16px;\">Congratulations, " + user.getName() + "! 🎉</h2>" +
                "  <p style=\"font-size:16px; color:#374151; line-height:1.5;\">We are thrilled to let you know that your <strong>Premium Plan</strong> has been successfully activated!</p>" +
                "  <div style=\"background-color:#f3f4f6; padding:16px; border-radius:6px; margin:20px 0;\">" +
                "    <table style=\"width:100%; border-collapse:collapse;\">" +
                "      <tr>" +
                "        <td style=\"padding:4px 0; color:#4b5563;\"><strong>Plan Name:</strong></td>" +
                "        <td style=\"padding:4px 0; color:#1f2937;\">VRESIQ Premium</td>" +
                "      </tr>" +
                "      <tr>" +
                "        <td style=\"padding:4px 0; color:#4b5563;\"><strong>Activation Date/Time:</strong></td>" +
                "        <td style=\"padding:4px 0; color:#1f2937;\">" + formattedDateTime + "</td>" +
                "      </tr>" +
                "    </table>" +
                "  </div>" +
                "  <p style=\"font-size:15px; color:#374151;\">Unlock all premium templates, unlimited AI rewrites, and priority PDF exports now.</p>" +
                "  <p style=\"text-align:center; margin:30px 0;\">" +
                "    <a href=\"" + loginLink + "\" style=\"display:inline-block; padding:12px 24px; background-color:#6366f1; color:#ffffff; font-weight:bold; text-decoration:none; border-radius:6px;\">Go to Dashboard / Login</a>" +
                "  </p>" +
                "  <p style=\"font-size:14px; color:#6b7280; line-height:1.5;\">Need help or have questions? Contact our support team at <a href=\"mailto:support@vresiq.com\" style=\"color:#6366f1;\">support@vresiq.com</a>.</p>" +
                "  <hr style=\"border:0; border-top:1px solid #e5e7eb; margin:20px 0;\">" +
                "  <p style=\"font-size:14px; color:#9ca3af; text-align:center;\">Thank you for choosing VRESIQ!<br>© 2026 VRESIQ. All rights reserved.</p>" +
                "</div>";

        emailService.sendHtmlEmail(user.getEmail(), "Welcome to VRESIQ Premium! 🎉", html);
    }

    private String resolveLoginLink() {
        if (frontendPublicUrl == null || frontendPublicUrl.isBlank()) {
            return "https://vresiq.com/login";
        }
        String candidate = frontendPublicUrl.split(",")[0].trim();
        if (candidate.endsWith("/")) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate + "/login";
    }
}
