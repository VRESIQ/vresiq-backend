package in.rithik.resumebuilderapi.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
@Slf4j
public class FirebaseConfig {

    @org.springframework.beans.factory.annotation.Value("${app.firebase.credentials:}")
    private String firebaseCredentialsJson;

    @org.springframework.beans.factory.annotation.Value("${app.firebase.credentials-path:}")
    private String firebaseCredentialsPath;

    @PostConstruct
    public void initialize() {
        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                log.info("FirebaseApp already initialized");
                return;
            }

            InputStream credentialsStream = null;

            // 1. Try credentials JSON string from properties/env
            if (firebaseCredentialsJson != null && !firebaseCredentialsJson.isBlank()) {
                log.info("Initializing Firebase using credentials JSON from properties");
                credentialsStream = new ByteArrayInputStream(firebaseCredentialsJson.getBytes(StandardCharsets.UTF_8));
            } 
            // 2. Try credentials path from properties/env
            else if (firebaseCredentialsPath != null && !firebaseCredentialsPath.isBlank()) {
                log.info("Initializing Firebase using credentials file: {}", firebaseCredentialsPath);
                credentialsStream = new FileInputStream(firebaseCredentialsPath);
            } 
            // 3. Fallback to GOOGLE_APPLICATION_CREDENTIALS environment variable file path
            else {
                String envCredentials = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
                if (envCredentials != null && !envCredentials.isBlank()) {
                    log.info("Initializing Firebase using GOOGLE_APPLICATION_CREDENTIALS environment file");
                    credentialsStream = new FileInputStream(envCredentials);
                }
            }

            FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder();

            if (credentialsStream != null) {
                optionsBuilder.setCredentials(GoogleCredentials.fromStream(credentialsStream));
            } else {
                log.warn("No Firebase credentials provided. Trying to initialize with Application Default Credentials.");
                try {
                    optionsBuilder.setCredentials(GoogleCredentials.getApplicationDefault());
                } catch (Exception e) {
                    log.error("Failed to load Application Default Credentials: {}", e.getMessage());
                    // Create dummy/local options to prevent dependency injection failure, or skip initialize
                    return;
                }
            }

            FirebaseApp.initializeApp(optionsBuilder.build());
            log.info("FirebaseApp initialized successfully");

        } catch (Exception e) {
            log.error("Failed to initialize FirebaseApp: {}", e.getMessage());
        }
    }
}
