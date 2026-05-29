package in.rithik.resumebuilderapi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerifyService {

    private final RestTemplate restTemplate;

    // Predefined lists for fuzzy matching
    private static final List<String> TOP_INSTITUTIONS = Arrays.asList(
            "Harvard University", "Stanford University", "Massachusetts Institute of Technology",
            "University of Oxford", "University of Cambridge", "ETH Zurich", "University of Toronto",
            "IIT Bombay", "IIT Delhi", "BITS Pilani", "Stanford", "MIT", "Oxford", "Cambridge"
    );

    private static final List<String> TOP_CERTIFICATIONS = Arrays.asList(
            "AWS Certified Solutions Architect", "Google Cloud Professional Data Engineer",
            "Microsoft Certified: Azure Solutions Architect Expert", "CompTIA Security+",
            "Certified Information Systems Security Professional (CISSP)", "PMP", "Scrum Master",
            "Cisco Certified Network Associate (CCNA)", "AWS", "Azure", "GCP", "OCI"
    );

    @Cacheable(value = "verifications", key = "#username")
    public boolean verifyGitHub(String username) {
        log.info("Verifying GitHub username: {}", username);
        try {
            String url = "https://api.github.com/users/" + username;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.warn("GitHub verification failed for user: {}. Error: {}", username, e.getMessage());
            return false;
        }
    }

    public boolean verifyInstitution(String name) {
        if (name == null || name.isBlank()) return false;
        String lowerName = name.toLowerCase();
        return TOP_INSTITUTIONS.stream()
                .anyMatch(inst -> inst.toLowerCase().contains(lowerName) || lowerName.contains(inst.toLowerCase()));
    }

    public boolean verifyCertification(String name) {
        if (name == null || name.isBlank()) return false;
        String lowerName = name.toLowerCase();
        return TOP_CERTIFICATIONS.stream()
                .anyMatch(cert -> cert.toLowerCase().contains(lowerName) || lowerName.contains(cert.toLowerCase()));
    }
}
