package in.rithik.resumebuilderapi.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/resumes")
@Slf4j
public class ResumeExportController {

    @PostMapping("/{id}/export-pdf")
    public ResponseEntity<byte[]> exportPdf(
            @PathVariable("id") String id,
            @RequestBody Map<String, String> payload
    ) {
        if (payload == null) {
            log.error("PDF Export Pipeline ERROR: Received null request payload for resume ID: {}", id);
            return ResponseEntity.badRequest().body("Request payload is required".getBytes());
        }

        String htmlContent = payload.get("htmlContent");
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            log.error("PDF Export Pipeline ERROR: htmlContent is null or empty for resume ID: {}", id);
            return ResponseEntity.badRequest().body("htmlContent is required".getBytes());
        }

        log.info("PDF Export Pipeline - Starting PDF generation for resume ID: {}. HTML payload length: {} characters", id, htmlContent.length());

        File tempHtmlFile = null;
        File tempPdfFile = null;

        try {
            // Create safe temporary files in standard systems temp folder
            tempHtmlFile = File.createTempFile("resume-export-" + id + "-", ".html");
            tempPdfFile = File.createTempFile("resume-export-" + id + "-", ".pdf");
            log.info("PDF Export Pipeline - Temporary files created successfully:\nHTML path: {}\nPDF path: {}", 
                     tempHtmlFile.getAbsolutePath(), tempPdfFile.getAbsolutePath());

            // Write HTML content to the temporary HTML file
            Files.writeString(tempHtmlFile.toPath(), htmlContent);
            log.info("PDF Export Pipeline - HTML content written to temp file successfully.");

            // Configure ProcessBuilder to call pdf-generator.js script via Node
            String workingDir = System.getProperty("user.dir");
            File scriptFile = new File(workingDir, "pdf-generator.js");
            log.info("PDF Export Pipeline - Exporter script resolution path: {}", scriptFile.getAbsolutePath());

            if (!scriptFile.exists()) {
                log.error("PDF Export Pipeline ERROR: Puppeteer script not found at path: {}", scriptFile.getAbsolutePath());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(("Exporter script not configured on server. Checked path: " + scriptFile.getAbsolutePath()).getBytes());
            }

            // Resolve node executable path
            String nodeCommand = resolveNodeExecutable();
            log.info("PDF Export Pipeline - Resolved node executable command: {}", nodeCommand);

            // Execute "node -v" to verify node works and log its version
            verifyNodeExecutable(nodeCommand);

            log.info("PDF Export Pipeline - Configuring ProcessBuilder for pdf-generator.js execution...");
            log.info("PDF Export Pipeline - ProcessBuilder command: [{}, {}, {}, {}]", 
                     nodeCommand, scriptFile.getAbsolutePath(), tempHtmlFile.getAbsolutePath(), tempPdfFile.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(
                    nodeCommand,
                    scriptFile.getAbsolutePath(),
                    tempHtmlFile.getAbsolutePath(),
                    tempPdfFile.getAbsolutePath()
            );

            // Merge error stream with standard stream to read all outputs (stdout and stderr)
            pb.redirectErrorStream(true);
            log.info("PDF Export Pipeline - Redirected stderr to stdout stream to prevent buffer blockages.");

            Process process;
            try {
                process = pb.start();
                log.info("PDF Export Pipeline - Node process started successfully.");
            } catch (IOException e) {
                String stacktrace = getStackTraceAsString(e);
                log.error("PDF Export Pipeline CRITICAL ERROR: Failed to start node process.\nCommand: {}\nPATH Env: {}\nException: {}\nStacktrace:\n{}", 
                          nodeCommand, System.getenv("PATH"), e.getMessage(), stacktrace);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(("Failed to start Node process on server. Error: " + e.getMessage() + "\nStacktrace:\n" + stacktrace).getBytes());
            }

            // Read the process stdout/stderr
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (IOException e) {
                log.warn("PDF Export Pipeline - Warning during reading process output: {}", e.getMessage());
            }

            // Wait for node script execution (timeout of 45 seconds to prevent hanging)
            log.info("PDF Export Pipeline - Waiting for process to complete with a 45 second timeout...");
            boolean finished = process.waitFor(45, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                log.error("PDF Export Pipeline ERROR: Puppeteer script timed out after 45 seconds. Process was forcibly destroyed.");
                log.error("PDF Export Pipeline - Captured Process Output before timeout:\n{}", output.toString());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(("PDF export timed out on the server after 45 seconds. Output:\n" + output.toString()).getBytes());
            }

            int exitCode = process.exitValue();
            log.info("PDF Export Pipeline - pdf-generator.js execution completed. Exit code: {}", exitCode);
            log.info("PDF Export Pipeline - Complete Process Output:\n{}", output.toString());

            if (exitCode != 0) {
                log.error("PDF Export Pipeline ERROR: Puppeteer script execution failed with exit code {}.", exitCode);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(("Failed to generate PDF on server. Exit code: " + exitCode + ".\nProcess Output:\n" + output.toString()).getBytes());
            }

            // Verify output PDF file exists and has content
            if (!tempPdfFile.exists() || tempPdfFile.length() == 0) {
                log.error("PDF Export Pipeline ERROR: Output PDF file is missing or empty at path: {}", tempPdfFile.getAbsolutePath());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to generate PDF on server. Output file is missing or empty.".getBytes());
            }

            // Read output PDF file bytes
            byte[] pdfBytes = Files.readAllBytes(tempPdfFile.toPath());
            log.info("PDF Export Pipeline - Read generated PDF file ({} bytes)", pdfBytes.length);

            // Prepare HTTP response headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "resume-" + id + ".pdf");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            log.info("PDF Export Pipeline - PDF exported successfully for resume ID {}.", id);
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

        } catch (Throwable e) {
            String stacktrace = getStackTraceAsString(e);
            log.error("PDF Export Pipeline CRITICAL EXCEPTION for resume ID {}: {}\nStacktrace:\n{}", id, e.getMessage(), stacktrace, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Error during PDF export: " + e.getMessage() + ".\nStacktrace:\n" + stacktrace).getBytes());
        } finally {
            // Clean up temporary files safely to avoid disk leak
            log.info("PDF Export Pipeline - Starting temporary files cleanup...");
            if (tempHtmlFile != null && tempHtmlFile.exists()) {
                if (tempHtmlFile.delete()) {
                    log.info("PDF Export Pipeline - Successfully deleted temporary HTML file: {}", tempHtmlFile.getAbsolutePath());
                } else {
                    log.warn("PDF Export Pipeline WARNING: Failed to delete temporary HTML file: {}", tempHtmlFile.getAbsolutePath());
                }
            }
            if (tempPdfFile != null && tempPdfFile.exists()) {
                if (tempPdfFile.delete()) {
                    log.info("PDF Export Pipeline - Successfully deleted temporary PDF file: {}", tempPdfFile.getAbsolutePath());
                } else {
                    log.warn("PDF Export Pipeline WARNING: Failed to delete temporary PDF file: {}", tempPdfFile.getAbsolutePath());
                }
            }
        }
    }

    private String resolveNodeExecutable() {
        log.info("PDF Export Pipeline - Resolving 'node' executable location...");
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.trim().isEmpty()) {
            log.warn("PDF Export Pipeline WARNING: PATH environment variable is null or empty. Falling back to default 'node' command.");
            return "node";
        }

        String pathSeparator = File.pathSeparator;
        String[] paths = pathEnv.split(pathSeparator);

        String osName = System.getProperty("os.name").toLowerCase();
        boolean isWindows = osName.contains("win");
        String[] executables = isWindows 
                ? new String[]{"node.exe", "node.cmd", "node.bat"} 
                : new String[]{"node"};

        for (String dir : paths) {
            for (String exec : executables) {
                File file = new File(dir, exec);
                if (file.exists() && file.isFile() && file.canExecute()) {
                    log.info("PDF Export Pipeline - Found node executable at: {}", file.getAbsolutePath());
                    return file.getAbsolutePath();
                }
            }
        }

        log.warn("PDF Export Pipeline WARNING: 'node' executable not found in system PATH directories. Falling back to default 'node' command.");
        return "node";
    }

    private void verifyNodeExecutable(String nodePath) {
        log.info("PDF Export Pipeline - Verifying node version by running '{} -v'...", nodePath);
        try {
            Process p = new ProcessBuilder(nodePath, "-v").start();
            StringBuilder versionOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    versionOutput.append(line);
                }
            }
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (finished && p.exitValue() == 0) {
                log.info("PDF Export Pipeline - Node version check success. Version: {}", versionOutput.toString().trim());
            } else {
                log.warn("PDF Export Pipeline WARNING: Node version check returned non-zero exit code: {}", 
                         finished ? p.exitValue() : "Timed out");
            }
        } catch (Exception e) {
            log.warn("PDF Export Pipeline WARNING: Failed to verify node version. Error: {}", e.getMessage());
        }
    }

    private String getStackTraceAsString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
