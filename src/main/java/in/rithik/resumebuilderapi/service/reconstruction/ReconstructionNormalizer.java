package in.rithik.resumebuilderapi.service.reconstruction;

import in.rithik.resumebuilderapi.document.Resume;
import org.springframework.stereotype.Component;

import java.util.List;

/** Validation-only pass. It deliberately does not rewrite source dates or remove repeated content. */
@Component
public class ReconstructionNormalizer {

    public List<String> normalize(Resume resume) {
        // Reserved for future opt-in normalization; preserve all current source values unchanged.
        return List.of();
    }
}
