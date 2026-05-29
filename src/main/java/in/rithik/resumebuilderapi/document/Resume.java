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
        academic_cv
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
        private String email;
        private String phone;
        private String location;
        private String linkedIn;
        private String github;
        private String website;

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
    }
}
