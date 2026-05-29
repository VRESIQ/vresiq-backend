package in.rithik.resumebuilderapi.dto;

import in.rithik.resumebuilderapi.document.Resume;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ResumeUpdateRequest {
    private String title;
    private String thumbnailLink;
    private String template;
    private String fontPairing;
    private Map<String, String> decoratives;
    private Resume.ProfileInfo profileInfo;
    private Resume.ContactInfo contactInfo;
    private List<Resume.WorkExperience> workExperience;
    private List<Resume.Education> education;
    private List<Resume.Skill> skills;
    private List<Resume.Project> projects;
    private List<Resume.Certification> certifications;
    private List<Resume.Language> languages;
    private List<String> interests;
    private Map<String, List<Resume.CustomSectionEntry>> customSections;
}
