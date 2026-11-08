package in.rithik.resumebuilderapi.service;

import in.rithik.resumebuilderapi.document.User;
import in.rithik.resumebuilderapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {
    private static final String PENDING = "PENDING";
    private static final String RETRY = "RETRY";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String ACCEPTED = "ACCEPTED";
    private static final String FAILED = "FAILED";
    private static final String UNKNOWN = "UNKNOWN";
    private static final int MAX_AUTOMATIC_ATTEMPTS = 5;
    private static final int BATCH_SIZE = 100;

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final MongoOperations mongoTemplate;

    @Value("${FRONTEND_PUBLIC_URL:}")
    private String frontendPublicUrl;

    /**
     * Changes the plan atomically. A Basic-to-Pro transition writes the durable
     * notification request in the same user-document update, before any email
     * provider call is attempted.
     */
    public User updateSubscription(String userId, String requestedPlan) {
        String newPlan = normalizePlan(requestedPlan);
        if ("premium".equals(newPlan)) {
            return promoteAndQueueNotification(userId);
        }

        LocalDateTime now = utcNow();
        Query query = Query.query(Criteria.where("_id").is(userId));
        Update update = new Update().set("subscriptionPlan", "basic").set("updatedAt", now);
        User saved = mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), User.class);
        if (saved == null) throw userNotFound(userId);
        return saved;
    }

    public User updateSubscription(User user, String newPlan) {
        if (user == null || user.getId() == null) throw new UsernameNotFoundException("User not found");
        return updateSubscription(user.getId(), newPlan);
    }

    private User promoteAndQueueNotification(String userId) {
        LocalDateTime now = utcNow();
        String notificationId = UUID.randomUUID().toString();
        Query query = Query.query(Criteria.where("_id").is(userId)
                .and("subscriptionPlan").not().regex("(?i)^premium$"));
        Update update = new Update()
                .set("subscriptionPlan", "premium")
                .set("updatedAt", now)
                .set("premiumUpgradeEmailNotificationId", notificationId)
                .set("premiumUpgradeEmailStatus", PENDING)
                .set("premiumUpgradeEmailActivatedAt", now)
                .set("premiumUpgradeEmailAttempts", 0)
                .set("premiumUpgradeEmailNextAttemptAt", now)
                .unset("premiumUpgradeEmailLastAttemptAt")
                .unset("premiumUpgradeEmailLeaseUntil")
                .unset("premiumUpgradeEmailAcceptedAt")
                .unset("premiumUpgradeEmailProvider")
                .unset("premiumUpgradeEmailProviderMessageId")
                .unset("premiumUpgradeEmailFailureCode");

        User promoted = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), User.class);
        if (promoted == null) {
            // Another request may already have completed the transition. That
            // request owns the one notification event; this is an idempotent no-op.
            return userRepository.findById(userId).orElseThrow(() -> userNotFound(userId));
        }

        attemptNotification(userId, notificationId);
        // Return fresh status for the admin response when the provider call finished.
        return userRepository.findById(userId).orElse(promoted);
    }

    @Scheduled(fixedDelayString = "${premium.upgrade.email.poll-delay-ms:30000}")
    public void retryPendingUpgradeNotifications() {
        LocalDateTime now = utcNow();
        markStaleAttemptsUnknown(now);

        Query due = Query.query(Criteria.where("subscriptionPlan").is("premium")
                .and("premiumUpgradeEmailStatus").in(PENDING, RETRY)
                .and("premiumUpgradeEmailNextAttemptAt").lte(now))
                .with(Sort.by(Sort.Direction.ASC, "premiumUpgradeEmailNextAttemptAt"))
                .limit(BATCH_SIZE);
        List<User> users = mongoTemplate.find(due, User.class);
        for (User user : users) {
            attemptNotification(user.getId(), user.getPremiumUpgradeEmailNotificationId());
        }
    }

    private void attemptNotification(String userId, String notificationId) {
        if (notificationId == null) return;
        LocalDateTime now = utcNow();
        Query claim = Query.query(Criteria.where("_id").is(userId)
                .and("subscriptionPlan").is("premium")
                .and("premiumUpgradeEmailNotificationId").is(notificationId)
                .and("premiumUpgradeEmailStatus").in(PENDING, RETRY)
                .and("premiumUpgradeEmailNextAttemptAt").lte(now));
        Update claimUpdate = new Update()
                .set("premiumUpgradeEmailStatus", IN_PROGRESS)
                .set("premiumUpgradeEmailLastAttemptAt", now)
                .set("premiumUpgradeEmailLeaseUntil", now.plusMinutes(2))
                .inc("premiumUpgradeEmailAttempts", 1);
        User claimed = mongoTemplate.findAndModify(claim, claimUpdate,
                FindAndModifyOptions.options().returnNew(true), User.class);
        if (claimed == null) return;

        try {
            String html = PremiumUpgradeEmailTemplate.render(claimed, claimed.getPremiumUpgradeEmailActivatedAt(), resolveDashboardUrl());
            EmailSendResult result = emailService.sendHtmlEmail(
                    claimed.getEmail(), "Your VResIQ Premium access is active", html);
            updateNotification(userId, notificationId, new Update()
                    .set("premiumUpgradeEmailStatus", ACCEPTED)
                    .set("premiumUpgradeEmailAcceptedAt", utcNow())
                    .set("premiumUpgradeEmailProvider", result.provider())
                    .set("premiumUpgradeEmailProviderMessageId", result.providerMessageId())
                    .unset("premiumUpgradeEmailLeaseUntil")
                    .unset("premiumUpgradeEmailNextAttemptAt")
                    .unset("premiumUpgradeEmailFailureCode"));
            log.info("Premium upgrade notification {} accepted by provider {}", notificationId, result.provider());
        } catch (EmailDeliveryException ex) {
            recordFailure(userId, notificationId, claimed.getPremiumUpgradeEmailAttempts(), ex);
        } catch (Exception ex) {
            // Do not emit exception messages or stack traces: mail libraries may
            // include addresses or provider request data in them.
            recordFailure(userId, notificationId, claimed.getPremiumUpgradeEmailAttempts(),
                    new EmailDeliveryException("EMAIL_SEND_FAILED", EmailDeliveryException.Outcome.UNKNOWN, false));
        }
    }

    private void recordFailure(String userId, String notificationId, Integer attemptCount, EmailDeliveryException failure) {
        int attempts = attemptCount == null ? 1 : attemptCount;
        boolean scheduleRetry = failure.getOutcome() == EmailDeliveryException.Outcome.REJECTED
                && failure.isRetryable() && attempts < MAX_AUTOMATIC_ATTEMPTS;
        String status = scheduleRetry ? RETRY
                : failure.getOutcome() == EmailDeliveryException.Outcome.UNKNOWN ? UNKNOWN : FAILED;

        Update update = new Update()
                .set("premiumUpgradeEmailStatus", status)
                .set("premiumUpgradeEmailFailureCode", failure.getFailureCode())
                .unset("premiumUpgradeEmailLeaseUntil");
        if (scheduleRetry) {
            long delayMinutes = Math.min(60, 1L << Math.min(attempts - 1, 6));
            update.set("premiumUpgradeEmailNextAttemptAt", utcNow().plusMinutes(delayMinutes));
        } else {
            update.unset("premiumUpgradeEmailNextAttemptAt");
        }
        updateNotification(userId, notificationId, update);

        if (status.equals(RETRY)) {
            log.warn("Premium upgrade notification {} was rejected; retry {} of {} is scheduled ({})",
                    notificationId, attempts, MAX_AUTOMATIC_ATTEMPTS, failure.getFailureCode());
        } else if (status.equals(UNKNOWN)) {
            log.error("Premium upgrade notification {} has an unknown provider outcome; automatic resend suppressed ({})",
                    notificationId, failure.getFailureCode());
        } else {
            log.error("Premium upgrade notification {} failed permanently after {} attempt(s) ({})",
                    notificationId, attempts, failure.getFailureCode());
        }
    }

    private void markStaleAttemptsUnknown(LocalDateTime now) {
        Query stale = Query.query(Criteria.where("premiumUpgradeEmailStatus").is(IN_PROGRESS)
                .and("premiumUpgradeEmailLeaseUntil").lte(now));
        Update update = new Update().set("premiumUpgradeEmailStatus", UNKNOWN)
                .set("premiumUpgradeEmailFailureCode", "DELIVERY_OUTCOME_UNKNOWN")
                .unset("premiumUpgradeEmailLeaseUntil")
                .unset("premiumUpgradeEmailNextAttemptAt");
        mongoTemplate.updateMulti(stale, update, User.class);
    }

    private void updateNotification(String userId, String notificationId, Update update) {
        Query query = Query.query(Criteria.where("_id").is(userId)
                .and("premiumUpgradeEmailNotificationId").is(notificationId));
        mongoTemplate.updateFirst(query, update, User.class);
    }

    private String normalizePlan(String plan) {
        String normalized = plan == null ? "" : plan.trim().toLowerCase();
        if (!Set.of("basic", "premium").contains(normalized)) {
            throw new IllegalArgumentException("Plan must be either basic or premium");
        }
        return normalized;
    }

    private UsernameNotFoundException userNotFound(String userId) {
        return new UsernameNotFoundException("User not found: " + userId);
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private String resolveDashboardUrl() {
        if (frontendPublicUrl == null || frontendPublicUrl.isBlank()) return "https://vresiq.com/dashboard";
        String candidate = frontendPublicUrl.split(",")[0].trim();
        while (candidate.endsWith("/")) candidate = candidate.substring(0, candidate.length() - 1);
        return candidate + "/dashboard";
    }
}
