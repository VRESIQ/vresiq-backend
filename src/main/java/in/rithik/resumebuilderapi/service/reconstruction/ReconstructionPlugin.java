package in.rithik.resumebuilderapi.service.reconstruction;

import java.io.InputStream;

public interface ReconstructionPlugin {
    boolean supports(String mimeType);
    String extractText(InputStream inputStream) throws Exception;
}
