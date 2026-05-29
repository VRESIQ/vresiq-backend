package in.rithik.resumebuilderapi.service;

import in.rithik.resumebuilderapi.document.AiRewriteCache;
import in.rithik.resumebuilderapi.document.UserAiStats;
import in.rithik.resumebuilderapi.repository.AiRewriteCacheRepository;
import in.rithik.resumebuilderapi.repository.UserAiStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final AiRewriteCacheRepository cacheRepository;
    private final UserAiStatsRepository statsRepository;
    private final RestTemplate restTemplate;
    private final org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    @Value("${AI_API_KEY:}")
    private String aiApiKey;

    @Value("${GEMINI_API_KEY:}")
    private String geminiApiKey;

    private static final int DAILY_QUOTA = 10;

    public String rewriteContent(String userId, String content, String tone) {
        if (content == null || content.isBlank()) return content;

        String hash = generateHash(content + "|" + tone);
        Optional<AiRewriteCache> cached = cacheRepository.findByContentHash(hash);
        if (cached.isPresent()) {
            return cached.get().getRewrittenContent();
        }

        // Check quota
        UserAiStats stats = statsRepository.findByUserId(userId)
                .orElse(UserAiStats.builder().userId(userId).dailyRewriteCount(0).build());

        if (stats.getDailyRewriteCount() >= DAILY_QUOTA) {
            throw new RuntimeException("Daily AI rewrite quota reached. Try again tomorrow.");
        }

        // Call AI
        String rewritten = callAiApi(content, tone);

        // Update stats and cache
        stats.setDailyRewriteCount(stats.getDailyRewriteCount() + 1);
        statsRepository.save(stats);

        cacheRepository.save(AiRewriteCache.builder()
                .contentHash(hash)
                .originalContent(content)
                .rewrittenContent(rewritten)
                .createdAt(LocalDateTime.now())
                .build());

        return rewritten;
    }

    public Map<String, Object> analyzeResume(String resumeJson) {
        // Use the same smart key and free model logic
        String finalKey = aiApiKey;
        if (isInvalid(finalKey)) finalKey = geminiApiKey;

        if (isInvalid(finalKey)) throw new RuntimeException("AI Key missing.");
        finalKey = finalKey.replaceAll("[\\s\\r\\n]", "");

        String url = "https://openrouter.ai/api/v1/chat/completions";
        
        String prompt = "Analyze this resume JSON for ATS compatibility. " +
                "Provide a 'score' (0-100), a list of 'suggestions', and a 'summary'. " +
                "Format the response as pure JSON with keys: score, suggestions (array of strings), summary (string). " +
                "Resume JSON: " + resumeJson;

        Map<String, Object> requestBody = Map.of(
                "model", "google/gemini-flash-1.5-8b:free",
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "response_format", Map.of("type", "json_object")
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + finalKey);
        headers.set("User-Agent", "VRESIQ/1.0");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List choices = (List) response.getBody().get("choices");
                Map choice = (Map) choices.get(0);
                Map message = (Map) choice.get("message");
                String rawJson = (String) message.get("content");
                
                // Simplified parsing for demonstration
                return Map.of("analysis", rawJson); 
            }
        } catch (Exception e) {
            log.error("ATS Analysis failed: {}", e.getMessage());
        }
        return Map.of("error", "Failed to analyze resume.");
    }

    private String callAiApi(String content, String tone) {
        // SMART KEY DETECTION: Check environment keys
        String finalKey = aiApiKey;
        if (isInvalid(finalKey)) finalKey = geminiApiKey;

        if (isInvalid(finalKey)) {
            log.error("CRITICAL: AI API Key not found in AI_API_KEY or GEMINI_API_KEY.");
            throw new RuntimeException("AI API Key missing. Please check your environment variables.");
        }

        // DEEP CLEAN: Remove all whitespace, carriage returns, and non-printable characters
        finalKey = finalKey.replaceAll("[\\s\\r\\n]", "");

        String url = "https://openrouter.ai/api/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + finalKey);
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.set("HTTP-Referer", "https://vresiq.app");
        headers.set("X-Title", "VRESIQ");

        String toneInstructions = switch (tone.toLowerCase()) {
            case "punchy" -> "Make it extremely concise and high-impact.";
            case "quantified" -> "Focus on numbers and metrics.";
            case "professional" -> "Maintain a formal, executive tone.";
            default -> "Make it more professional and ATS-friendly.";
        };

        String prompt = String.format(
            "You are an expert executive resume writer. Your task is to rewrite a single resume bullet point to make it more impactful, recruiter-grade, and professional.\n\n" +
            "CRITICAL CONSTRAINTS:\n" +
            "- Always start with a strong action verb (e.g. Optimized, Engineered, Spearheaded, Accelerated).\n" +
            "- Structure the output to emphasize quantifiable results and measurable engineering or business outcomes (e.g., latency, users, throughput, cost, revenue).\n" +
            "- Do NOT fabricate, hallucinate, or invent fake metrics, numbers, awards, technologies, or achievements. Maintain 100%% factual alignment with the input content.\n" +
            "- If the input contains metrics, highlight them. If it lacks metrics, keep it numbers-ready or focus on technical mechanics without inventing values.\n" +
            "- Tone instruction: %s. %s\n" +
            "- Output ONLY the rewritten bullet point directly. Do not include any introductory sentences, conversational filler, markdown formatting (like bolding, italics, or quotes), or list bullets.\n\n" +
            "Original content: %s\n\n" +
            "Rewritten bullet point:",
            tone, toneInstructions, content
        );

        // Switch to a FREE model to ensure no credit issues
        Map<String, Object> requestBody = Map.of(
                "model", "google/gemini-flash-1.5-8b:free",
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List choices = (List) response.getBody().get("choices");
                if (choices == null || choices.isEmpty()) throw new RuntimeException("AI returned no results.");
                Map choice = (Map) choices.get(0);
                Map message = (Map) choice.get("message");
                return ((String) message.get("content")).trim();
            } else {
                log.error("AI API Error: {}", response.getBody());
                throw new RuntimeException("AI API returned status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("AI Request Failed: {}", e.getMessage(), e);
            throw new RuntimeException("AI Error: " + e.getMessage());
        }
    }

    private boolean isInvalid(String key) {
        return key == null || key.isBlank() || key.contains("${") || key.equals("null");
    }

    private String generateHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hashing error", e);
        }
    }

    @Scheduled(cron = "0 0 0 * * *") // Every midnight
    public void resetDailyStats() {
        log.info("Resetting daily AI rewrite stats in batch...");
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        org.springframework.data.mongodb.core.query.Update update = new org.springframework.data.mongodb.core.query.Update().set("dailyRewriteCount", 0);
        mongoTemplate.updateMulti(query, update, UserAiStats.class);
    }
}
