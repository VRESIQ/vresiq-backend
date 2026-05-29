package in.rithik.resumebuilderapi.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMailMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    @Value("${spring.mail.properties.mail.smtp.from}")
    private String fromEmail;

    private final JavaMailSender mailSender;

    public void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        log.info("Preparing to send HTML email to: {}, subject: {}, from: {}", to, subject, fromEmail);
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
}
