package in.rithik.resumebuilderapi.document;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "resumes")
@org.springframework.data.mongodb.core.index.CompoundIndexes({
    @org.springframework.data.mongodb.core.index.CompoundIndex(name = "user_id_updated_at", def = "{'userId': 1, 'updatedAt': -1}")
})
public class Resume {
    @Id
    @JsonProperty("_id")
    private String id;

    @org.springframework.data.mongodb.core.index.Indexed
    private String userId;

    private String title;

    private String thumbnailLink;

    private Template template;

    private String fontPairing;

    private Map<String, String> decoratives;

    private ProfileInfo profileInfo;

    private ContactInfo contactInfo;

    private List<WorkExperience> workExperience;

    private List<Education> education;

    private List<Skill> skills;

    private List<Project> projects;

    private List<Certification> certifications;

    private List<Language> languages;

    private List<String> interests;

    private Map<String, List<CustomSectionEntry>> customSections;

    /**
     * Last authoritative ATS score confirmed by the backend RefineService.
     * Populated whenever POST /api/ai/refine/{id} succeeds. Sent to the
     * frontend inside the resume payload so the badge can hydrate instantly
     * on page load without a separate network call.
     */
    private Integer lastAtsScore;

    /**
     * Job category detected from the resume designation at the last ATS run.
     * E.g. "software engineer", "frontend developer". Used by the badge for
     * role display before the user clicks Run ATS Check.
     */
    private String lastAtsCategory;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum Template {
        template1,
        template2,
        template3,
        premium1,
        premium2,
        premium3,
        premium4,
        premium5,
        premium6,
        premium7,
        premium8,
        premium9,
        premium10,
        ats_classic,
        ats_entry,
        ats_senior,
        ats_lead,
        ats_intern,
        ats_experienced,
        academic_cv,
        // Added: newer frontend templates not previously in the enum.
        // Missing entries caused normalizeTemplate() to fall back to template1
        // on every save, permanently overwriting the user's chosen template.
        engineer_ats,       // Frame
        consulting_bcg,     // Summit
        tech_faang,         // Atlas
        harvard_ats,        // Stone
        swiss_minimal       // Metro
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ProfileInfo{
        @JsonProperty("ProfilePreviewUrl")
        @JsonAlias("profilePreviewUrl")
        private String ProfilePreviewUrl;
        private String fullName;
        private String designation;
        private String summary;
        private String targetRole;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ContactInfo{
        private Object email;
        private Object phone;
        private Object location;
        private Object linkedIn;
        private Object github;
        private Object website;
        private Object leetCode;
        private Object hackerRank;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class WorkExperience{
        private String company;
        private String role;
        private String startDate;
        private String endDate;
        private String description;
        private String location;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Education{

        private String degree;
        private String institution;
        private String startDate;
        private String endDate;
        private String location;
        private String gpa;
        private String description;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Skill{

        private String name;
        private Integer progress;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Project{

        private String title;
        private String description;
        private String github;
        private String liveDemo;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Certification{

        private String title;
        private String issuer;
        private String year;
        private String certificateUrl;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Language{

        private String name;
        private Integer progress;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CustomSectionEntry {
        private String title;
        private String subtitle;
        private String date;
        private String description;
        private String authors;
        @JsonProperty("abstract")
        private String abstractText;
        private String paperUrl;
        private String abstractAuthors;
    }
}
