package in.rithik.resumebuilderapi;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import in.rithik.resumebuilderapi.document.Resume;
import in.rithik.resumebuilderapi.dto.RefineResponse;
import in.rithik.resumebuilderapi.service.RefineService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AtsVerifier {
    public static void main(String[] args) {
        System.out.println("[Java AtsVerifier] Starting ATS Verification...");
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .enable(SerializationFeature.INDENT_OUTPUT);

            // Read the resumes from workspace root
            File resumesFile = new File("../resumes.json");
            if (!resumesFile.exists()) {
                System.err.println("Could not find resumes.json at " + resumesFile.getAbsolutePath());
                System.exit(1);
            }

            Resume[] resumes = mapper.readValue(resumesFile, Resume[].class);
            System.out.println("Loaded " + resumes.length + " resumes from resumes.json");

            RefineService refineService = new RefineService();
            List<RefineResponse> results = new ArrayList<>();

            for (Resume resume : resumes) {
                System.out.println("Running ATS analysis for: " + resume.getTitle());
                RefineResponse response = refineService.analyze(resume);
                results.add(response);
            }

            // Write results to java_results.json in workspace root
            File outputFile = new File("../java_results.json");
            mapper.writeValue(outputFile, results);
            System.out.println("[Java AtsVerifier] Completed. Results written to: " + outputFile.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
