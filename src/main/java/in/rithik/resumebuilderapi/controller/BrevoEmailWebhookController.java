package in.rithik.resumebuilderapi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/webhooks/brevo")
public class BrevoEmailWebhookController {
    private static final Set<String> DELIVERY_FAILURE_EVENTS = Set.of("soft_bounce", "hard_bounce", "blocked", "invalid", "error");
    private final MongoTemplate mongoTemplate;

    @Value("${BREVO_WEBHOOK_TOKEN:}")
    private String webhookToken;

    @PostMapping("/transactional-email")
    public ResponseEntity<Void> handleTransactionalEmailEvent(
            @RequestHeader(name = "X-VResIQ-Brevo-Token", required = false) String suppliedToken,
            @RequestBody JsonNode event) {
        if (webhookToken == null || webhookToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (!constantTimeEquals(webhookToken, suppliedToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String eventName = event.path("event").asText("").toLowerCase();
        String providerMessageId = event.path("message-id").asText("");
        if (providerMessageId.isBlank()) return ResponseEntity.ok().build();

        String status;
        if ("delivered".equals(eventName)) {
            status = "DELIVERED";
        } else if (DELIVERY_FAILURE_EVENTS.contains(eventName)) {
            status = "DELIVERY_FAILED";
        } else {
            return ResponseEntity.ok().build();
        }

        Query query = Query.query(Criteria.where("premiumUpgradeEmailProviderMessageId").is(providerMessageId)
                .and("premiumUpgradeEmailProvider").in("BREVO_API", "SMTP")
                .and("premiumUpgradeEmailStatus").is("ACCEPTED"));
        Update update = new Update()
                .set("premiumUpgradeEmailStatus", status)
                .set("premiumUpgradeEmailDeliveryEventAt", LocalDateTime.now(ZoneOffset.UTC));
        if ("DELIVERY_FAILED".equals(status)) {
            update.set("premiumUpgradeEmailFailureCode", "BREVO_" + eventName.toUpperCase());
        }
        mongoTemplate.updateFirst(query, update, in.rithik.resumebuilderapi.document.User.class);
        log.info("Brevo transactional email status event recorded: {}", status);
        return ResponseEntity.ok().build();
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
