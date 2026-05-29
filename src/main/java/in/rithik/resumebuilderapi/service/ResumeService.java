package in.rithik.resumebuilderapi.service;

import in.rithik.resumebuilderapi.document.Resume;
import in.rithik.resumebuilderapi.dto.AuthResponse;
import in.rithik.resumebuilderapi.dto.CreateResumeRequest;
import in.rithik.resumebuilderapi.dto.ResumeUpdateRequest;
import in.rithik.resumebuilderapi.repository.ResumeRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final AuthService authService;

    public Resume createResume(CreateResumeRequest request, Object principalObject) {
        // Get who's making the request first — we need their ID and plan
        AuthResponse profile = authService.getProfile(principalObject);

        // Free users are capped at 1 resume. Check before creating anything.
        // Using equalsIgnoreCase so "basic", "Basic", "BASIC" all match — DB had inconsistency before
        if ("basic".equalsIgnoreCase(profile.getSubscriptionPlan()) || profile.getSubscriptionPlan() == null) {
            List<Resume> existing = resumeRepository.findByUserIdOrderByUpdatedAtDesc(profile.getId());
            if (!existing.isEmpty()) {
                throw new RuntimeException("Upgrade your plan to create more resumes.");
            }
        }

        Resume resume = new Resume();
        resume.setUserId(profile.getId());
        resume.setTitle(request.getTitle());

        // Resolve and validate premium template privileges
        resume.setTemplate(getValidatedTemplate(request.getTemplate(), profile));

        // Initialize all sections as empty lists so the frontend doesn't get null
        initEmptySections(resume, profile.getProfileImageUrl(), profile.getName());

        return resumeRepository.save(resume);
    }

    // Sets every section to an empty list. Saves the frontend from null-check hell.
    private void initEmptySections(Resume resume, String defaultProfileImageUrl, String defaultFullName) {
        Resume.ProfileInfo profileInfo = new Resume.ProfileInfo();
        profileInfo.setProfilePreviewUrl(defaultProfileImageUrl);
        profileInfo.setFullName(defaultFullName);
        resume.setProfileInfo(profileInfo);
        resume.setContactInfo(new Resume.ContactInfo());
        resume.setWorkExperience(new ArrayList<>());
        resume.setEducation(new ArrayList<>());
        resume.setSkills(new ArrayList<>());
        resume.setProjects(new ArrayList<>());
        resume.setCertifications(new ArrayList<>());
        resume.setLanguages(new ArrayList<>());
        resume.setInterests(new ArrayList<>());
        resume.setCustomSections(new HashMap<>());
    }

    public List<Resume> getUserResumes(Object principal) {
        AuthResponse profile = authService.getProfile(principal);
        // Returns newest first — dashboard shows most-recently-edited at top
        return resumeRepository.findByUserIdOrderByUpdatedAtDesc(profile.getId());
    }

    public Resume getResumeById(String resumeId, Object principal) {
        AuthResponse profile = authService.getProfile(principal);
        // findByUserIdAndId ensures a user can only read their OWN resumes
        // (can't just change the ID in the URL and get someone else's)
        return resumeRepository.findByUserIdAndId(profile.getId(), resumeId)
                .orElseThrow(() -> new RuntimeException("Resume not found"));
    }

    public Resume updateResume(String resumeId, ResumeUpdateRequest incoming, Object principal) {
        AuthResponse profile = authService.getProfile(principal);

        Resume existing = resumeRepository.findByUserIdAndId(profile.getId(), resumeId)
                .orElseThrow(() -> new RuntimeException("Resume not found"));

        // Copy each field from the incoming payload into the existing document.
        // We don't just replace the whole document — that would lose fields we don't expose in the API.
        // Also note: we're not re-checking the subscription plan here, because updating a resume
        // you already own is always allowed regardless of plan.
        existing.setTitle(incoming.getTitle());
        existing.setThumbnailLink(incoming.getThumbnailLink());
        existing.setTemplate(getValidatedTemplate(incoming.getTemplate(), profile));
        existing.setFontPairing(incoming.getFontPairing());
        existing.setDecoratives(incoming.getDecoratives());
        existing.setProfileInfo(incoming.getProfileInfo());
        existing.setContactInfo(incoming.getContactInfo());
        existing.setWorkExperience(incoming.getWorkExperience());
        existing.setEducation(incoming.getEducation());
        existing.setSkills(incoming.getSkills());
        existing.setProjects(incoming.getProjects());
        existing.setCertifications(incoming.getCertifications());
        existing.setLanguages(incoming.getLanguages());
        existing.setInterests(incoming.getInterests());
        existing.setCustomSections(incoming.getCustomSections());

        resumeRepository.save(existing);
        return existing;
    }

    private Resume.Template getValidatedTemplate(String rawTemplate, AuthResponse profile) {
        Resume.Template template = normalizeTemplate(rawTemplate);
        if (template != Resume.Template.template1 && !"premium".equalsIgnoreCase(profile.getSubscriptionPlan())) {
            throw new RuntimeException("Upgrade your plan to use premium templates.");
        }
        return template;
    }

    private Resume.Template normalizeTemplate(String rawTemplate) {
        String normalized = normalizeTemplateIdentifier(rawTemplate);
        try {
            return Resume.Template.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown template '{}' requested, defaulting to template1", rawTemplate);
            return Resume.Template.template1;
        }
    }

    private String normalizeTemplateIdentifier(String rawTemplate) {
        if (rawTemplate == null || rawTemplate.isBlank()) return "template1";
        String clean = rawTemplate.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (clean) {
            case "classic" -> "template1";
            case "sidebar" -> "template2";
            case "header" -> "template3";
            case "minimal" -> "premium4";
            case "timeline" -> "premium1";
            case "executive" -> "premium2";
            case "compact" -> "premium3";
            case "accent_lines" -> "premium5";
            case "split_dark" -> "premium6";
            case "card_sections" -> "premium7";
            case "infographic" -> "premium8";
            case "centered" -> "premium9";
            case "tech" -> "premium10";
            case "ats_classic_software_eng", "ats_software_eng", "ats_software_engineer" -> "ats_classic";
            case "ats_entry_level" -> "ats_entry";
            case "ats_senior_professional" -> "ats_senior";
            case "ats_technical_lead" -> "ats_lead";
            case "ats_intern_academic" -> "ats_intern";
            case "ats_experienced_modern" -> "ats_experienced";
            default -> clean;
        };
    }

    public void deleteResume(String resumeId, Object principal) {
        AuthResponse profile = authService.getProfile(principal);
        // Again using findByUserIdAndId so users can't delete other people's resumes
        Resume existing = resumeRepository.findByUserIdAndId(profile.getId(), resumeId)
                .orElseThrow(() -> new RuntimeException("Resume not found"));
        resumeRepository.delete(existing);
    }
}
