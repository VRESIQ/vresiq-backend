package in.rithik.resumebuilderapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import jakarta.annotation.PostConstruct;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@SpringBootApplication
@EnableCaching
@EnableScheduling
public class ResumebuilderapiApplication {

	public static void main(String[] args) {
		loadDotEnv();
		SpringApplication.run(ResumebuilderapiApplication.class, args);
	}

	private static void loadDotEnv() {
		Path envPath = Paths.get(".env");
		if (!Files.exists(envPath)) {
			envPath = Paths.get("resume-builder-backend", ".env");
		}

		if (Files.exists(envPath)) {
			try {
				List<String> lines = Files.readAllLines(envPath);
				for (String line : lines) {
					line = line.trim();
					if (line.isEmpty() || line.startsWith("#")) {
						continue;
					}
					int eqIdx = line.indexOf('=');
					if (eqIdx > 0) {
						String key = line.substring(0, eqIdx).trim();
						String value = line.substring(eqIdx + 1).trim();
						if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
							value = value.substring(1, value.length() - 1);
						} else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
							value = value.substring(1, value.length() - 1);
						}
						if (System.getProperty(key) == null) {
							System.setProperty(key, value);
						}
					}
				}
				System.out.println("Loaded environment variables from: " + envPath.toAbsolutePath());
			} catch (IOException e) {
				System.err.println("Failed to load local .env file: " + e.getMessage());
			}
		} else {
			System.out.println("No local .env file found at standard paths. Relying on system environment variables.");
		}
	}

	@Autowired
	private MongoTemplate mongoTemplate;

	@PostConstruct
	public void printDb() {
		System.out.println("======== CONNECTED DATABASE: "
				+ mongoTemplate.getDb().getName() + " ========");
	}

}
