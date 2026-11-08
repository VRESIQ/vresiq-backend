package in.rithik.resumebuilderapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private static final URI BREVO_SEND_URI = URI.create("https://api.brevo.com/v3/smtp/email");

    @Value("${spring.mail.properties.mail.smtp.from:admin@vresiq.app}")
    private String fromEmail;

    // Only the explicit Brevo API key selects the HTTPS API. SMTP passwords
    // must remain SMTP credentials and must never be sent as an HTTP API key.
    @Value("${BREVO_API_KEY:}")
    private String brevoApiKey;
    @Value("${spring.mail.username:}")
    private String smtpUsername;
    @Value("${spring.mail.password:}")
    private String smtpPassword;

    private final JavaMailSender mailSender;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public EmailSendResult sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        if (hasBrevoApiKey()) {
            return sendHtmlViaBrevoApi(to, subject, htmlContent);
        }
        requireSmtpConfiguration();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            message.saveChanges();
            String messageId = message.getMessageID();
            mailSender.send(message);
            log.info("Email accepted by SMTP server");
            return new EmailSendResult("SMTP", messageId);
        } catch (MailAuthenticationException ex) {
            log.warn("SMTP authentication was rejected by the provider");
            throw new EmailDeliveryException("SMTP_AUTH_REJECTED", EmailDeliveryException.Outcome.REJECTED, true);
        } catch (MailException ex) {
            log.warn("SMTP send outcome is unknown ({})", ex.getClass().getSimpleName());
            throw new EmailDeliveryException("SMTP_OUTCOME_UNKNOWN", EmailDeliveryException.Outcome.UNKNOWN, false);
        }
    }

    public void sendEmailWithAttachment(String to, String subject, String body, byte[] attachment, String filename) throws MessagingException {
        if (hasBrevoApiKey()) {
            String htmlContent = "<p>" + HtmlUtils.htmlEscape(body == null ? "" : body).replace("\n", "<br>") + "</p>";
            Map<String, Object> payload = Map.of(
                    "sender", Map.of("email", fromEmail),
                    "to", List.of(Map.of("email", to)),
                    "subject", subject,
                    "htmlContent", htmlContent,
                    "attachment", List.of(Map.of(
                            "content", Base64.getEncoder().encodeToString(attachment),
                            "name", filename)));
            sendViaBrevoApi(payload);
            return;
        }

        requireSmtpConfiguration();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body == null ? "" : body);
            helper.addAttachment(filename, new ByteArrayResource(attachment));
            message.saveChanges();
            mailSender.send(message);
            log.info("Email with attachment accepted by SMTP server");
        } catch (MailAuthenticationException ex) {
            log.warn("SMTP authentication was rejected by the provider");
            throw new EmailDeliveryException("SMTP_AUTH_REJECTED", EmailDeliveryException.Outcome.REJECTED, true);
        } catch (MailException ex) {
            log.warn("SMTP attachment send outcome is unknown ({})", ex.getClass().getSimpleName());
            throw new EmailDeliveryException("SMTP_OUTCOME_UNKNOWN", EmailDeliveryException.Outcome.UNKNOWN, false);
        }
    }

    private EmailSendResult sendHtmlViaBrevoApi(String to, String subject, String htmlContent) {
        return sendViaBrevoApi(Map.of(
                "sender", Map.of("email", fromEmail),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "htmlContent", htmlContent));
    }

    private EmailSendResult sendViaBrevoApi(Map<String, Object> payload) {
        final String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (IOException ex) {
            log.error("Could not serialize email provider request");
            throw new EmailDeliveryException("EMAIL_REQUEST_INVALID", EmailDeliveryException.Outcome.REJECTED, false);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(BREVO_SEND_URI)
                .timeout(Duration.ofSeconds(20))
                .header("accept", "application/json")
                .header("content-type", "application/json")
                .header("api-key", brevoApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                log.warn("Brevo rejected email request with HTTP status {}", status);
                if (status == 429) {
                    throw new EmailDeliveryException("BREVO_RATE_LIMITED", EmailDeliveryException.Outcome.REJECTED, true);
                }
                if (status >= 500) {
                    throw new EmailDeliveryException("BREVO_SERVER_OUTCOME_UNKNOWN", EmailDeliveryException.Outcome.UNKNOWN, false);
                }
                throw new EmailDeliveryException("BREVO_REQUEST_REJECTED_" + status, EmailDeliveryException.Outcome.REJECTED, false);
            }

            String messageId = null;
            try {
                JsonNode body = objectMapper.readTree(response.body());
                if (body != null && body.hasNonNull("messageId")) messageId = body.get("messageId").asText();
            } catch (IOException ignored) {
                // A 2xx response still confirms provider acceptance; the ID is optional.
            }
            log.info("Email accepted by Brevo API (provider message ID returned: {})", messageId != null);
            return new EmailSendResult("BREVO_API", messageId);
        } catch (EmailDeliveryException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Brevo request was interrupted; provider outcome is unknown");
            throw new EmailDeliveryException("BREVO_OUTCOME_UNKNOWN", EmailDeliveryException.Outcome.UNKNOWN, false);
        } catch (IOException ex) {
            log.warn("Brevo request failed before a response; provider outcome is unknown");
            throw new EmailDeliveryException("BREVO_OUTCOME_UNKNOWN", EmailDeliveryException.Outcome.UNKNOWN, false);
        }
    }

    private boolean hasBrevoApiKey() {
        return brevoApiKey != null && !brevoApiKey.isBlank();
    }

    private void requireSmtpConfiguration() {
        if (smtpUsername == null || smtpUsername.isBlank() || smtpPassword == null || smtpPassword.isBlank()) {
            log.warn("Email provider is not configured: set BREVO_API_KEY or both SMTP username and password variables");
            throw new EmailDeliveryException("EMAIL_PROVIDER_NOT_CONFIGURED", EmailDeliveryException.Outcome.REJECTED, true);
        }
    }
}
