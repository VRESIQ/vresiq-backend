package in.rithik.resumebuilderapi.dto;

import in.rithik.resumebuilderapi.document.Resume;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

public class ReconstructionModels {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ReconstructionResponse {
        private int schemaVersion;
        private String profile;
        private long totalDurationMs;
        private Resume resume;
        private String detectedRole;
        private String recommendedTemplate;
        private List<String> unclassifiedContent;
        private List<PipelineStageTrace> stages;
        private Map<String, Double> confidenceReport;
        private List<ExplainabilityRecord> explainabilityRecords;
        private QualityGateReport qualityGates;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class PipelineStageTrace {
        private String name;
        private String status;
        private long durationMs;
        private String detail;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ExplainabilityRecord {
        private String field;
        private String original;
        private String improved;
        private String reason;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class QualityGateReport {
        private boolean requiredSectionsExist;
        private boolean contactInfoValid;
        private boolean noDuplicateSections;
        private boolean atsCompleted;
        private boolean passed;
        private List<String> warnings;
    }
}
