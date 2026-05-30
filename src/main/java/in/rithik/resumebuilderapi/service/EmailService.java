package in.rithik.resumebuilderapi.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    @Value("${spring.mail.properties.mail.smtp.from}")
    private String fromEmail;

    @Value("${BREVO_API_KEY:}")
    private String brevoApiKey;

    private final JavaMailSender mailSender;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        log.info("Preparing to send HTML email to: {}, subject: {}, from: {}", to, subject, fromEmail);

        // Render free blocks outbound SMTP ports (25/465/587). Prefer Brevo HTTPS API when key is set.
        if (brevoApiKey != null && !brevoApiKey.isBlank()) {
            sendHtmlViaBrevoApi(to, subject, htmlContent);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            
            log.info("Sending HTML message via JavaMailSender...");
            mailSender.send(message);
            log.info("HTML Email sent successfully to {}", to);
        } catch (MessagingException e) {
            log.error("MessagingException occurred while sending HTML email to {}: ", to, e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected exception occurred while sending HTML email to {}: ", to, e);
            throw new RuntimeException("Unexpected error during HTML email transmission: " + e.getMessage(), e);
        }
    }

    public void sendEmailWithAttachment(String to, String subject, String body, byte[] attachment, String filename) throws MessagingException {
        log.info("Preparing to send email with attachment to: {}, subject: {}, from: {}, filename: {}", to, subject, fromEmail, filename);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body);
            helper.addAttachment(filename, new ByteArrayResource(attachment));
            
            log.info("Sending message with attachment via JavaMailSender...");
            mailSender.send(message);
            log.info("Email with attachment sent successfully to {}", to);
        } catch (MessagingException e) {
            log.error("MessagingException occurred while sending email with attachment to {}: ", to, e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected exception occurred while sending email with attachment to {}: ", to, e);
            throw new RuntimeException("Unexpected error during email transmission: " + e.getMessage(), e);
        }
    }

    private void sendHtmlViaBrevoApi(String to, String subject, String htmlContent) {
        try {
            String safeHtml = jsonEscape(htmlContent);
            String payload = "{"
                    + "\"sender\":{\"email\":\"" + jsonEscape(fromEmail) + "\"},"
                    + "\"to\":[{\"email\":\"" + jsonEscape(to) + "\"}],"
                    + "\"subject\":\"" + jsonEscape(subject) + "\","
                    + "\"htmlContent\":\"" + safeHtml + "\""
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .timeout(Duration.ofSeconds(20))
                    .header("accept", "application/json")
                    .header("content-type", "application/json")
                    .header("api-key", brevoApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new RuntimeException("Brevo API send failed with status " + status + ": " + response.body());
            }

            log.info("HTML Email sent successfully to {} via Brevo API", to);
        } catch (Exception e) {
            log.error("Brevo API send failed for {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Email delivery failed via Brevo API: " + e.getMessage(), e);
        }
    }

    private String jsonEscape(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c <= 0x1F) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
