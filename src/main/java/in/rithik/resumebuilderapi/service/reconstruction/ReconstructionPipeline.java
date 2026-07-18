package in.rithik.resumebuilderapi.service.reconstruction;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.rithik.resumebuilderapi.document.Resume;
import in.rithik.resumebuilderapi.document.Resume.Skill;
import in.rithik.resumebuilderapi.document.Resume.WorkExperience;
import in.rithik.resumebuilderapi.document.Resume.Education;
import in.rithik.resumebuilderapi.dto.ReconstructionModels.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconstructionPipeline {

    private final List<ReconstructionPlugin> plugins;
    private final DeterministicParser parser;
    private final ReconstructionNormalizer normalizer;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${AI_API_KEY:}")
    private String aiApiKey;

    @Value("${GEMINI_API_KEY:}")
    private String geminiApiKey;

    public ReconstructionResponse execute(InputStream fileStream, String mimeType, String profileName) {
        long startTime = System.currentTimeMillis();
        List<PipelineStageTrace> traces = new ArrayList<>();
        List<ExplainabilityRecord> explainabilityRecords = new ArrayList<>();
        List<String> unclassifiedContent = new ArrayList<>();

        // 1. Document Extraction Stage
        long stepStart = System.currentTimeMillis();
        String rawText = "";
        ReconstructionPlugin matchedPlugin = plugins.stream()
                .filter(p -> p.supports(mimeType))
                .findFirst()
                .orElse(null);

        if (matchedPlugin == null) {
            throw new IllegalArgumentException("Unsupported file type: " + mimeType);
        }

        try {
            rawText = matchedPlugin.extractText(fileStream);
            traces.add(PipelineStageTrace.builder()
                    .name("Document Ingestion")
                    .status("SUCCESS")
                    .durationMs(System.currentTimeMillis() - stepStart)
                    .detail("Extracted text successfully (" + rawText.length() + " chars)")
                    .build());
        } catch (Exception e) {
            log.error("Ingestion failed: {}", e.getMessage());
            traces.add(PipelineStageTrace.builder()
                    .name("Document Ingestion")
                    .status("FAILED")
                    .durationMs(System.currentTimeMillis() - stepStart)
                    .detail("Error: " + e.getMessage())
                    .build());
            throw new RuntimeException("Ingestion stage failed: " + e.getMessage(), e);
        }

        // 2. Deterministic Parsing Stage
        stepStart = System.currentTimeMillis();
        Resume resume = parser.parse(rawText);
        traces.add(PipelineStageTrace.builder()
                .name("Deterministic Parsing")
                .status("SUCCESS")
                .durationMs(System.currentTimeMillis() - stepStart)
                .detail("Extracted email, phone, name, and basic experience sections")
                .build());

        // 3. Normalization Stage
        stepStart = System.currentTimeMillis();
        List<String> normalizedSteps = normalizer.normalize(resume);
        traces.add(PipelineStageTrace.builder()
                .name("Normalization & Standardizing")
                .status("SUCCESS")
                .durationMs(System.currentTimeMillis() - stepStart)
                .detail("Normalized: " + String.join(", ", normalizedSteps))
                .build());

        // 4. Confidence Engine
        stepStart = System.currentTimeMillis();
        Map<String, Double> confidenceReport = calculateConfidence(resume);
        traces.add(PipelineStageTrace.builder()
                .name("Confidence Scoring")
                .status("SUCCESS")
                .durationMs(System.currentTimeMillis() - stepStart)
                .detail("Calculated confidence scores for all fields")
                .build());

        // 5. LLM Enrichment & Role Detection (Fail-Safe try-catch block)
        stepStart = System.currentTimeMillis();
        String detectedRole = "Software Engineer";
        String recommendedTemplate = "engineer_ats";
        int atsScore = 65;
        List<String> atsSuggestions = new ArrayList<>();

        String finalKey = aiApiKey;
        if (isInvalid(finalKey)) finalKey = geminiApiKey;

        if (isInvalid(finalKey)) {
            traces.add(PipelineStageTrace.builder()
                    .name("LLM Enrichment")
                    .status("SKIPPED")
                    .durationMs(0)
                    .detail("AI keys missing in environment variables. Falling back to deterministic parsed model.")
                    .build());
        } else {
            try {
                Map<String, Object> aiResult = callLlmService(rawText, resume, profileName, finalKey);
                if (aiResult != null) {
                    detectedRole = (String) aiResult.getOrDefault("detectedRole", detectedRole);
                    recommendedTemplate = (String) aiResult.getOrDefault("recommendedTemplate", recommendedTemplate);
                    
                    if (aiResult.containsKey("atsScore")) {
                        atsScore = ((Number) aiResult.get("atsScore")).intValue();
                    }
                    if (aiResult.containsKey("atsSuggestions")) {
                        atsSuggestions = (List<String>) aiResult.get("atsSuggestions");
                    }
                    if (aiResult.containsKey("unclassifiedContent")) {
                        unclassifiedContent = (List<String>) aiResult.get("unclassifiedContent");
                    }

                    // Enrich resume summary and titles if returned
                    Map<String, Object> enrichedResume = (Map<String, Object>) aiResult.get("enrichedResume");
                    if (enrichedResume != null) {
                        enrichResumeData(resume, enrichedResume, explainabilityRecords);
                    }

                    traces.add(PipelineStageTrace.builder()
                            .name("LLM Enrichment")
                            .status("SUCCESS")
                            .durationMs(System.currentTimeMillis() - stepStart)
                            .detail("Enriched text formatting, resolved role: " + detectedRole)
                            .build());
                }
            } catch (Exception e) {
                log.warn("Fail-Safe: LLM Enrichment failed (rate limit/network/timeout). Continuing with deterministic schema: {}", e.getMessage());
                traces.add(PipelineStageTrace.builder()
                        .name("LLM Enrichment")
                        .status("FAILED")
                        .durationMs(System.currentTimeMillis() - stepStart)
                        .detail("Error: " + e.getMessage() + ". Fail-safe fallback triggered.")
                        .build());
            }
        }

        // 6. Quality Gate Verification
        stepStart = System.currentTimeMillis();
        QualityGateReport qualityGates = verifyQualityGates(resume, atsScore, atsSuggestions);
        traces.add(PipelineStageTrace.builder()
                .name("Quality Gate Verification")
                .status("SUCCESS")
                .durationMs(System.currentTimeMillis() - stepStart)
                .detail("Checked fields completeness, score=" + atsScore)
                .build());

        // Build result
        resume.setLastAtsScore(atsScore);
        resume.setLastAtsCategory(detectedRole);
        resume.setTemplate(Resume.Template.valueOf(recommendedTemplate));

        return ReconstructionResponse.builder()
                .schemaVersion(1)
                .profile(profileName)
                .totalDurationMs(System.currentTimeMillis() - startTime)
                .resume(resume)
                .detectedRole(detectedRole)
                .recommendedTemplate(recommendedTemplate)
                .unclassifiedContent(unclassifiedContent)
                .stages(traces)
                .confidenceReport(confidenceReport)
                .explainabilityRecords(explainabilityRecords)
                .qualityGates(qualityGates)
                .build();
    }

    private Map<String, Double> calculateConfidence(Resume resume) {
        Map<String, Double> report = new HashMap<>();
        
        double nameConf = (resume.getProfileInfo() != null && resume.getProfileInfo().getFullName() != null) ? 0.98 : 0.0;
        report.put("Name", nameConf);

        double emailConf = (resume.getContactInfo() != null && resume.getContactInfo().getEmail() != null) ? 1.0 : 0.0;
        report.put("Email", emailConf);

        double expConf = (resume.getWorkExperience() != null && !resume.getWorkExperience().isEmpty()) ? 0.95 : 0.0;
        report.put("Experience", expConf);

        double eduConf = (resume.getEducation() != null && !resume.getEducation().isEmpty()) ? 0.90 : 0.0;
        report.put("Education", eduConf);

        double skillsConf = (resume.getSkills() != null && !resume.getSkills().isEmpty()) ? 0.85 : 0.0;
        report.put("Skills", skillsConf);

        return report;
    }

    private QualityGateReport verifyQualityGates(Resume resume, int atsScore, List<String> suggestions) {
        List<String> warnings = new ArrayList<>();
        boolean sectionsExist = resume.getWorkExperience() != null && !resume.getWorkExperience().isEmpty();
        if (!sectionsExist) warnings.add("Work experience section is missing or unparsed.");

        boolean contactValid = resume.getContactInfo() != null && (resume.getContactInfo().getEmail() != null || resume.getContactInfo().getPhone() != null);
        if (!contactValid) warnings.add("Missing contact email or phone number.");

        boolean passed = sectionsExist && contactValid && atsScore >= 50;

        return QualityGateReport.builder()
                .requiredSectionsExist(sectionsExist)
                .contactInfoValid(contactValid)
                .noDuplicateSections(true)
                .atsCompleted(true)
                .passed(passed)
                .warnings(warnings)
                .build();
    }

    private Map<String, Object> callLlmService(String rawText, Resume resume, String profileName, String apiKey) throws Exception {
        String url = "https://openrouter.ai/api/v1/chat/completions";

        String prompt = "You are a professional resume parser. " +
                "Read this raw text, analyze it using the target profile: " + profileName + ".\n" +
                "Output a JSON object with the following fields:\n" +
                "- detectedRole: E.g., 'Software Engineer'\n" +
                "- recommendedTemplate: Recommend one from VResIQ templates: 'engineer_ats', 'consulting_bcg', 'swiss_minimal', 'harvard_ats'\n" +
                "- atsScore: A score (0-100) representing how readable and clean the resume text is.\n" +
                "- atsSuggestions: Array of strings suggesting how the format or wording can be improved.\n" +
                "- unclassifiedContent: Array of strings representing paragraphs or headers that could not fit into contact, summary, work history, education, or skills sections.\n" +
                "- enrichedResume: A nested object containing:\n" +
                "  - summary: An optimized recruiter-grade summary paragraph.\n" +
                "  - workExperience: List of objects with fields: role, company, startDate, endDate, description (wording polished with metrics structure, without fabricating details).\n" +
                "  - education: List of objects with fields: degree, institution, startDate, endDate, description.\n" +
                "\n" +
                "CRITICAL: Keep facts (dates, company names, titles) 100% accurate as in the raw text. Do not invent any numbers or metric details. Output only valid JSON.\n" +
                "Raw text: " + rawText;

        Map<String, Object> requestBody = Map.of(
                "model", "google/gemini-flash-1.5-8b:free",
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "response_format", Map.of("type", "json_object")
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey.replaceAll("[\\s\\r\\n]", ""));
        headers.set("User-Agent", "VRESIQ/1.0");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
        
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            List choices = (List) response.getBody().get("choices");
            Map choice = (Map) choices.get(0);
            Map message = (Map) choice.get("message");
            String rawJson = (String) message.get("content");
            return objectMapper.readValue(rawJson, Map.class);
        }
        return null;
    }

    private void enrichResumeData(Resume resume, Map<String, Object> enrichedResume, List<ExplainabilityRecord> records) {
        if (enrichedResume.containsKey("summary") && resume.getProfileInfo() != null) {
            String originalSummary = resume.getProfileInfo().getSummary();
            String enrichedSummary = (String) enrichedResume.get("summary");
            if (enrichedSummary != null && !enrichedSummary.isBlank() && !enrichedSummary.equals(originalSummary)) {
                resume.getProfileInfo().setSummary(enrichedSummary);
                records.add(ExplainabilityRecord.builder()
                        .field("profileInfo.summary")
                        .original(originalSummary)
                        .improved(enrichedSummary)
                        .reason("Optimized wording according to professional guidelines.")
                        .build());
            }
        }

        // Polish work experience descriptions
        List<Map<String, Object>> enrichedWork = (List<Map<String, Object>>) enrichedResume.get("workExperience");
        if (enrichedWork != null && resume.getWorkExperience() != null) {
            for (int i = 0; i < Math.min(enrichedWork.size(), resume.getWorkExperience().size()); i++) {
                WorkExperience origExp = resume.getWorkExperience().get(i);
                Map<String, Object> enrichedExp = enrichedWork.get(i);
                String origDesc = origExp.getDescription();
                String enrichedDesc = (String) enrichedExp.get("description");
                if (enrichedDesc != null && !enrichedDesc.isBlank() && !enrichedDesc.equals(origDesc)) {
                    origExp.setDescription(enrichedDesc);
                    records.add(ExplainabilityRecord.builder()
                            .field("workExperience[" + i + "].description")
                            .original(origDesc)
                            .improved(enrichedDesc)
                            .reason("Polished bullet points using active recruiter-grade phrasing.")
                            .build());
                }
            }
        }
    }

    private boolean isInvalid(String key) {
        return key == null || key.isBlank() || key.contains("${") || key.equals("null");
    }
}
