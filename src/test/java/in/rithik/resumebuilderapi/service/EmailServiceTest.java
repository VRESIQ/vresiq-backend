package in.rithik.resumebuilderapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

class EmailServiceTest {
    @Test
    void smtpPasswordUsesSmtpAndIsNotSentAsAnApiKey() throws Exception {
        RecordingMailSender sender = new RecordingMailSender();
        RecordingHttpClient http = new RecordingHttpClient(201, "{\"messageId\":\"api-id\"}");
        EmailService service = new EmailService(sender, http, new ObjectMapper());
        set(service, "brevoApiKey", "");
        set(service, "smtpUsername", "smtp-user");
        set(service, "smtpPassword", "smtp-secret");
        set(service, "fromEmail", "noreply@example.test");

        EmailSendResult result = service.sendHtmlEmail("controlled@example.test", "Subject", "<p>Premium</p>");

        assertEquals("SMTP", result.provider());
        assertEquals(1, sender.sendCount);
        assertNull(http.request);
    }

    @Test
    void dedicatedBrevoApiKeyUsesHttpsAndReturnsProviderMessageId() throws Exception {
        RecordingMailSender sender = new RecordingMailSender();
        RecordingHttpClient http = new RecordingHttpClient(201, "{\"messageId\":\"<provider-id>\"}");
        EmailService service = new EmailService(sender, http, new ObjectMapper());
        set(service, "brevoApiKey", "dedicated-api-key");
        set(service, "smtpUsername", "smtp-user");
        set(service, "smtpPassword", "smtp-secret");
        set(service, "fromEmail", "noreply@example.test");

        EmailSendResult result = service.sendHtmlEmail("controlled@example.test", "Subject", "<p>Premium</p>");

        assertEquals("BREVO_API", result.provider());
        assertEquals("<provider-id>", result.providerMessageId());
        assertEquals("dedicated-api-key", http.request.headers().firstValue("api-key").orElseThrow());
        assertEquals(0, sender.sendCount);
    }

    @Test
    void apiRejectionIsReportedWithoutProviderResponseBody() throws Exception {
        RecordingMailSender sender = new RecordingMailSender();
        RecordingHttpClient http = new RecordingHttpClient(401, "contains-recipient@example.test and secret details");
        EmailService service = new EmailService(sender, http, new ObjectMapper());
        set(service, "brevoApiKey", "dedicated-api-key");
        set(service, "fromEmail", "noreply@example.test");

        EmailDeliveryException failure = assertThrows(EmailDeliveryException.class,
                () -> service.sendHtmlEmail("controlled@example.test", "Subject", "<p>Premium</p>"));

        assertEquals("BREVO_REQUEST_REJECTED_401", failure.getFailureCode());
        assertFalse(failure.getMessage().contains("recipient"));
        assertFalse(failure.getMessage().contains("secret"));
        assertEquals(0, sender.sendCount);
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        var field = EmailService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class RecordingMailSender extends JavaMailSenderImpl {
        private int sendCount;
        @Override public MimeMessage createMimeMessage() {
            return new MimeMessage(Session.getInstance(new Properties()));
        }
        @Override public void send(MimeMessage message) { sendCount++; }
    }

    private static final class RecordingHttpClient extends HttpClient {
        private final int status;
        private final String body;
        private HttpRequest request;

        private RecordingHttpClient(int status, String body) {
            this.status = status;
            this.body = body;
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
            this.request = request;
            return (HttpResponse<T>) java.lang.reflect.Proxy.newProxyInstance(
                    HttpResponse.class.getClassLoader(), new Class<?>[]{HttpResponse.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "statusCode" -> status;
                        case "body" -> body;
                        case "request" -> request;
                        case "previousResponse", "sslSession" -> Optional.empty();
                        case "headers" -> HttpHeadersStub.empty();
                        case "uri" -> URI.create("https://api.brevo.com/v3/smtp/email");
                        case "version" -> Version.HTTP_1_1;
                        default -> null;
                    });
        }

        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            return CompletableFuture.failedFuture(new IOException("Not used by unit test"));
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return CompletableFuture.failedFuture(new IOException("Not used by unit test"));
        }
    }

    private static final class HttpHeadersStub {
        private static java.net.http.HttpHeaders empty() {
            return java.net.http.HttpHeaders.of(java.util.Map.of(), (name, value) -> true);
        }
    }
}
