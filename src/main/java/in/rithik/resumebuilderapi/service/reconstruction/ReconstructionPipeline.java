package in.rithik.resumebuilderapi.service.reconstruction;

import in.rithik.resumebuilderapi.document.Resume;
import in.rithik.resumebuilderapi.dto.ReconstructionModels.ExplainabilityRecord;
import in.rithik.resumebuilderapi.dto.ReconstructionModels.PipelineStageTrace;
import in.rithik.resumebuilderapi.dto.ReconstructionModels.QualityGateReport;
import in.rithik.resumebuilderapi.dto.ReconstructionModels.ReconstructionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Preservation-first ingestion path. AI rewriting is not applied to source resume facts. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconstructionPipeline {

    private final List<ReconstructionPlugin> plugins;
    private final DeterministicParser parser;
    private final ReconstructionNormalizer normalizer;

    public ReconstructionResponse execute(InputStream fileStream, String mimeType, String profileName) {
        long startTime = System.currentTimeMillis();
        List<PipelineStageTrace> traces = new ArrayList<>();
        List<ExplainabilityRecord> explainabilityRecords = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> unclassifiedContent;

        long stepStart = System.currentTimeMillis();
        ReconstructionPlugin matchedPlugin = plugins.stream().filter(plugin -> plugin.supports(mimeType)).findFirst().orElse(null);
        if (matchedPlugin == null) throw new IllegalArgumentException("Unsupported file type");

        String rawText;
        try {
            rawText = matchedPlugin.extractText(fileStream);
            if (rawText == null) rawText = "";
            traces.add(PipelineStageTrace.builder().name("Document Ingestion").status(rawText.isBlank() ? "WARNING" : "SUCCESS")
                    .durationMs(System.currentTimeMillis() - stepStart)
                    .detail("Extracted " + rawText.length() + " text characters; source content is not written to application logs.").build());
        } catch (Exception exception) {
            // Avoid exception text here: third-party parser messages can contain document snippets or metadata.
            log.warn("Reconstruction extraction failed; mimeType={}, exceptionType={}", mimeType, exception.getClass().getSimpleName());
            traces.add(PipelineStageTrace.builder().name("Document Ingestion").status("FAILED")
                    .durationMs(System.currentTimeMillis() - stepStart).detail("Document text extraction failed.").build());
            throw new IllegalStateException("Document text extraction failed", exception);
        }

        stepStart = System.currentTimeMillis();
        DeterministicParser.ParseResult parseResult = parser.parseWithDiagnostics(rawText);
        Resume resume = parseResult.getResume();
        unclassifiedContent = parseResult.getUnclassifiedContent();
        warnings.addAll(parseResult.getWarnings());
        traces.add(PipelineStageTrace.builder().name("Deterministic Parsing").status(rawText.isBlank() ? "WARNING" : "SUCCESS")
                .durationMs(System.currentTimeMillis() - stepStart)
                .detail("Parsed supported sections and retained uncertain source lines for human review.").build());

        stepStart = System.currentTimeMillis();
        warnings.addAll(normalizer.normalize(resume));
        traces.add(PipelineStageTrace.builder().name("Normalization & Validation").status("SUCCESS")
                .durationMs(System.currentTimeMillis() - stepStart)
                .detail("Validation only; source dates and repeated content were retained without rewriting.").build());

        stepStart = System.currentTimeMillis();
        Map<String, Double> confidenceReport = calculatePresenceEstimates(resume);
        traces.add(PipelineStageTrace.builder().name("Parse Coverage Estimates").status("SUCCESS")
                .durationMs(System.currentTimeMillis() - stepStart)
                .detail("Presence estimates only; no field-level accuracy ground truth is available.").build());

        // A server-side rewrite call used to receive the full resume text and could overwrite
        // factual statements. Reconstruction now keeps all source facts local and unchanged.
        traces.add(PipelineStageTrace.builder().name("AI Enrichment").status("SKIPPED")
                .durationMs(0).detail("Not applied: factual rewriting requires explicit user review and approval.").build());

        warnings.add("ATS analysis was not run as part of reconstruction; no ATS score is asserted.");
        String detectedRole = resume.getProfileInfo() == null ? null : resume.getProfileInfo().getDesignation();
        String recommendedTemplate = Resume.Template.harvard_ats.name();
        resume.setLastAtsScore(null);
        resume.setLastAtsCategory(detectedRole);
        resume.setTemplate(Resume.Template.harvard_ats);

        stepStart = System.currentTimeMillis();
        boolean hasSections = nonEmpty(resume.getWorkExperience()) || nonEmpty(resume.getEducation())
                || nonEmpty(resume.getProjects()) || nonEmpty(resume.getSkills());
        boolean contactValid = resume.getContactInfo() != null
                && (present(resume.getContactInfo().getEmail()) || present(resume.getContactInfo().getPhone()));
        boolean noRepeatedSection = warnings.stream().noneMatch(warning -> warning.startsWith("Repeated '"));
        if (!hasSections) warnings.add("No supported resume sections were confidently identified; review the extracted document before using it.");
        if (!contactValid) warnings.add("No email address or phone number was detected.");
        QualityGateReport qualityGates = QualityGateReport.builder()
                .requiredSectionsExist(hasSections)
                .contactInfoValid(contactValid)
                .noDuplicateSections(noRepeatedSection)
                .atsCompleted(false)
                .passed(hasSections && contactValid && noRepeatedSection)
                .warnings(new ArrayList<>(warnings))
                .build();
        traces.add(PipelineStageTrace.builder().name("Quality Gate Verification").status("SUCCESS")
                .durationMs(System.currentTimeMillis() - stepStart)
                .detail("Checked section presence, contact presence, and repeated headings; ATS status remains not run.").build());

        return ReconstructionResponse.builder()
                .schemaVersion(2)
                .profile(profileName)
                .totalDurationMs(System.currentTimeMillis() - startTime)
                .resume(resume)
                .detectedRole(detectedRole)
                .recommendedTemplate(recommendedTemplate)
                .unclassifiedContent(unclassifiedContent)
                .warnings(warnings)
                .stages(traces)
                .confidenceReport(confidenceReport)
                .explainabilityRecords(explainabilityRecords)
                .qualityGates(qualityGates)
                .build();
    }

    private Map<String, Double> calculatePresenceEstimates(Resume resume) {
        Map<String, Double> estimates = new HashMap<>();
        estimates.put("Name", resume.getProfileInfo() != null && present(resume.getProfileInfo().getFullName()) ? 1.0 : 0.0);
        estimates.put("Email", resume.getContactInfo() != null && present(resume.getContactInfo().getEmail()) ? 1.0 : 0.0);
        estimates.put("Experience", nonEmpty(resume.getWorkExperience()) ? 1.0 : 0.0);
        estimates.put("Education", nonEmpty(resume.getEducation()) ? 1.0 : 0.0);
        estimates.put("Skills", nonEmpty(resume.getSkills()) ? 1.0 : 0.0);
        return estimates;
    }

    private boolean present(Object value) { return value != null && !value.toString().isBlank(); }
    private boolean nonEmpty(List<?> values) { return values != null && !values.isEmpty(); }
}
