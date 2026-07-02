package in.rithik.resumebuilderapi.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;

/**
 * AtsRulesLoader — loads atsRules.json from the classpath at class-load time.
 *
 * ─── SINGLE SOURCE OF TRUTH ────────────────────────────────────────────────
 * atsRules.json is the one file that defines all ATS scoring constants:
 *   - Deduction point values
 *   - Keyword category lists
 *   - Template risk values
 *   - Threshold constants
 *
 * Both the frontend (atsScorer.js) and backend (RefineService.java) read from
 * this file. To change any ATS scoring behaviour, edit atsRules.json only —
 * then copy the updated file to both:
 *   vresiq-frontend/src/assets/atsRules.json
 *   vresiq-backend/src/main/resources/atsRules.json
 *
 * ─── DESIGN ────────────────────────────────────────────────────────────────
 * The config is loaded once in a static initializer. It is immutable for the
 * lifetime of the JVM. If the JSON is missing or malformed, the application
 * fails fast at startup rather than silently scoring 0 for all resumes.
 *
 * IGNORE_UNKNOWN_PROPERTIES is set so that documentation-only fields in the
 * JSON (like "_comment") do not cause deserialization failures.
 */
@Slf4j
public final class AtsRulesLoader {

    private static final AtsConfig CONFIG;

    static {
        ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (InputStream is = AtsRulesLoader.class.getResourceAsStream("/atsRules.json")) {
            if (is == null) {
                throw new IllegalStateException(
                    "atsRules.json not found on classpath. " +
                    "Ensure it is present in src/main/resources/.");
            }
            CONFIG = mapper.readValue(is, AtsConfig.class);
            log.info("[AtsRulesLoader] Loaded ATS rules — {} categories, {} deduction keys, {} template risks",
                CONFIG.getCategories()   != null ? CONFIG.getCategories().size()    : 0,
                CONFIG.getDeductions()   != null ? CONFIG.getDeductions().size()    : 0,
                CONFIG.getTemplateRisk() != null ? CONFIG.getTemplateRisk().size()  : 0
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse atsRules.json: " + e.getMessage(), e);
        }
    }

    /** Returns the immutable ATS configuration loaded from atsRules.json. */
    public static AtsConfig getConfig() {
        return CONFIG;
    }

    // Utility — never instantiate
    private AtsRulesLoader() {}
}
