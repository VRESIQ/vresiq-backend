package in.rithik.resumebuilderapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.rithik.resumebuilderapi.document.User;
import in.rithik.resumebuilderapi.repository.UserRepository;
import jakarta.mail.MessagingException;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionServiceTest {
    private FakeMongoOperations mongo;
    private FakeUsers users;
    private FakeEmailService email;
    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        mongo = new FakeMongoOperations();
        users = new FakeUsers();
        email = new FakeEmailService();
        service = new SubscriptionService(users.repository(), email, mongo.operations());
    }

    @Test
    void basicToPremiumPersistsOneEventAndSendsCorrectBrandedDetails() {
        LocalDateTime activation = LocalDateTime.of(2026, 11, 8, 10, 0);
        User promoted = user("<A&lex>", "basic", activation, 0);
        User claimed = user("<A&lex>", "premium", activation, 1);
        mongo.findAndModifyResults.add(promoted);
        mongo.findAndModifyResults.add(claimed);
        email.result = new EmailSendResult("BREVO_API", "provider-message-123");
        users.user = user("<A&lex>", "premium", activation, 1);

        User response = service.updateSubscription("user-1", "premium");

        assertEquals("premium", response.getSubscriptionPlan());
        assertEquals("controlled@example.test", email.to);
        assertEquals("Your VResIQ Premium access is active", email.subject);
        assertTrue(email.html.contains("VResIQ Pro"));
        assertTrue(email.html.contains("8 Nov 2026 UTC"));
        assertTrue(email.html.contains("Unlimited resumes"));
        assertTrue(email.html.contains("All premium templates"));
        assertTrue(email.html.contains("&lt;A&amp;lex&gt;"));
        assertTrue(email.html.contains("One-time Pro access"));

        assertEquals(1, mongo.updates.size());
        Document set = mongo.updates.get(0).getUpdateObject().get("$set", Document.class);
        assertEquals("ACCEPTED", set.getString("premiumUpgradeEmailStatus"));
        assertEquals("BREVO_API", set.getString("premiumUpgradeEmailProvider"));
    }

    @Test
    void repeatedPremiumUpdateIsAnIdempotentNoOp() {
        users.user = user("Alex", "premium", null, 0);

        User response = service.updateSubscription("user-1", "premium");

        assertEquals("premium", response.getSubscriptionPlan());
        assertEquals(0, email.sendCount);
        assertEquals(0, mongo.updates.size());
        assertEquals(1, mongo.findAndModifyCalls);
    }

    @Test
    void invalidUserDoesNotQueueOrSendEmail() {
        assertThrows(RuntimeException.class,
                () -> service.updateSubscription("missing", "premium"));
        assertEquals(0, email.sendCount);
    }

    @Test
    void databaseFailurePreventsEmailAttempt() {
        mongo.failNextFindAndModify = true;

        assertThrows(IllegalStateException.class,
                () -> service.updateSubscription("user-1", "premium"));
        assertEquals(0, email.sendCount);
    }

    @Test
    void definiteTransientProviderRejectionIsPersistedForRetryWithoutRevertingPlan() {
        User promoted = user("Alex", "basic", LocalDateTime.of(2026, 11, 8, 10, 0), 0);
        User claimed = user("Alex", "premium", LocalDateTime.of(2026, 11, 8, 10, 0), 1);
        mongo.findAndModifyResults.add(promoted);
        mongo.findAndModifyResults.add(claimed);
        email.failure = new EmailDeliveryException("BREVO_RATE_LIMITED", EmailDeliveryException.Outcome.REJECTED, true);
        users.user = claimed;

        assertDoesNotThrow(() -> service.updateSubscription("user-1", "premium"));

        assertEquals("premium", users.user.getSubscriptionPlan());
        assertEquals(1, email.sendCount);
        Document set = mongo.updates.get(0).getUpdateObject().get("$set", Document.class);
        assertEquals("RETRY", set.getString("premiumUpgradeEmailStatus"));
        assertNotNull(set.get("premiumUpgradeEmailNextAttemptAt"));
        assertEquals("BREVO_RATE_LIMITED", set.getString("premiumUpgradeEmailFailureCode"));
    }

    @Test
    void ambiguousProviderOutcomeIsNotAutomaticallyResent() {
        User promoted = user("Alex", "basic", LocalDateTime.of(2026, 11, 8, 10, 0), 0);
        User claimed = user("Alex", "premium", LocalDateTime.of(2026, 11, 8, 10, 0), 1);
        mongo.findAndModifyResults.add(promoted);
        mongo.findAndModifyResults.add(claimed);
        email.failure = new EmailDeliveryException("BREVO_OUTCOME_UNKNOWN", EmailDeliveryException.Outcome.UNKNOWN, false);
        users.user = claimed;

        assertDoesNotThrow(() -> service.updateSubscription("user-1", "premium"));

        Document set = mongo.updates.get(0).getUpdateObject().get("$set", Document.class);
        assertEquals("UNKNOWN", set.getString("premiumUpgradeEmailStatus"));
        assertNull(set.get("premiumUpgradeEmailNextAttemptAt"));
    }

    @Test
    void adminJsonExposesStatusButNotOutboxInternals() throws Exception {
        User user = user("Alex", "premium", null, 1);
        user.setPremiumUpgradeEmailStatus("RETRY");

        String json = new ObjectMapper().writeValueAsString(user);

        assertTrue(json.contains("\"premiumEmailNotificationStatus\":\"RETRY\""));
        assertFalse(json.contains("premiumUpgradeEmailNotificationId"));
        assertFalse(json.contains("premiumUpgradeEmailProviderMessageId"));
    }

    private User user(String name, String plan, LocalDateTime activatedAt, int attempts) {
        return User.builder()
                .id("user-1")
                .name(name)
                .email("controlled@example.test")
                .subscriptionPlan(plan)
                .premiumUpgradeEmailNotificationId("notification-1")
                .premiumUpgradeEmailActivatedAt(activatedAt)
                .premiumUpgradeEmailAttempts(attempts)
                .premiumUpgradeEmailStatus(attempts == 0 ? "PENDING" : "IN_PROGRESS")
                .build();
    }

    private static final class FakeEmailService extends EmailService {
        private int sendCount;
        private String to;
        private String subject;
        private String html;
        private EmailSendResult result = new EmailSendResult("BREVO_API", "message-123");
        private EmailDeliveryException failure;

        private FakeEmailService() {
            super(new JavaMailSenderImpl(), new NoNetworkHttpClient(), new ObjectMapper());
        }

        @Override
        public EmailSendResult sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
            sendCount++;
            this.to = to;
            this.subject = subject;
            this.html = htmlContent;
            if (failure != null) throw failure;
            return result;
        }
    }

    private static final class NoNetworkHttpClient extends HttpClient {
        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
            throw new IOException("Network access disabled in unit test");
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            return CompletableFuture.failedFuture(new IOException("Network access disabled in unit test"));
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return CompletableFuture.failedFuture(new IOException("Network access disabled in unit test"));
        }
    }

    private static final class FakeUsers {
        private User user;

        private UserRepository repository() {
            return (UserRepository) java.lang.reflect.Proxy.newProxyInstance(
                    UserRepository.class.getClassLoader(), new Class<?>[]{UserRepository.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("findById")) return Optional.ofNullable(user);
                        if (method.getName().equals("toString")) return "FakeUserRepository";
                        throw new UnsupportedOperationException("Unexpected repository call: " + method.getName());
                    });
        }
    }

    private static final class FakeMongoOperations {
        private final Queue<User> findAndModifyResults = new ArrayDeque<>();
        private final List<Update> updates = new ArrayList<>();
        private int findAndModifyCalls;
        private boolean failNextFindAndModify;

        private MongoOperations operations() {
            return (MongoOperations) java.lang.reflect.Proxy.newProxyInstance(
                    MongoOperations.class.getClassLoader(), new Class<?>[]{MongoOperations.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("findAndModify")) {
                            findAndModifyCalls++;
                            if (failNextFindAndModify) {
                                failNextFindAndModify = false;
                                throw new IllegalStateException("database unavailable");
                            }
                            return findAndModifyResults.poll();
                        }
                        if (method.getName().equals("updateFirst")) {
                            updates.add((Update) args[1]);
                            return null;
                        }
                        if (method.getName().equals("updateMulti")) return null;
                        if (method.getName().equals("toString")) return "FakeMongoOperations";
                        throw new UnsupportedOperationException("Unexpected Mongo operation: " + method.getName());
                    });
        }
    }
}
