package in.rithik.resumebuilderapi.service.reconstruction;

import java.io.InputStream;

public interface ReconstructionPlugin {
    boolean supports(String mimeType);
    String extractText(InputStream inputStream) throws Exception;

    /**
     * Best-effort extraction of the source portrait (page 1 for PDFs, first picture for DOCX).
     * Returns null when the format cannot carry an image or none was found. Callers must
     * treat a null result as "unsupported", never as "candidate has no photo".
     */
    default byte[] extractPortrait(InputStream inputStream) {
        return null;
    }
}
