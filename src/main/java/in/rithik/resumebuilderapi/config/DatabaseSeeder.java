package in.rithik.resumebuilderapi.config;

import in.rithik.resumebuilderapi.document.User;
import in.rithik.resumebuilderapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${seed.admin.email:}")
    private String adminEmail;

    @Value("${seed.admin.password:}")
    private String adminPassword;

    @Override
    public void run(String... args) throws Exception {
        seedAdminUser();
    }

    private void seedAdminUser() {
        if (adminEmail == null || adminEmail.trim().isEmpty() ||
            adminPassword == null || adminPassword.trim().isEmpty()) {
            log.info("Database Seeder: SEED_ADMIN_EMAIL or SEED_ADMIN_PASSWORD environment variables are not set. Skipping administrator user seeding.");
            return;
        }

        String targetEmail = adminEmail.trim();
        if (!userRepository.existsByEmail(targetEmail)) {
            User admin = User.builder()
                    .name("System Administrator")
                    .email(targetEmail)
                    .password(passwordEncoder.encode(adminPassword.trim()))
                    .role("ADMIN")
                    .subscriptionPlan("premium")
                    .emailVerified(true)
                    .active(true)
                    .mustResetPassword(true)
                    .build();

            userRepository.save(admin);
            log.info("Database Seeder: Administrator user seeded successfully.");
        } else {
            log.info("Database Seeder: Admin user already exists. Skipping seeding.");
        }
    }
}
