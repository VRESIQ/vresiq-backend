package in.rithik.resumebuilderapi.service;

import in.rithik.resumebuilderapi.document.User;
import in.rithik.resumebuilderapi.document.Resume;
import in.rithik.resumebuilderapi.document.Payment;
import in.rithik.resumebuilderapi.document.UserAiStats;
import in.rithik.resumebuilderapi.repository.UserRepository;
import in.rithik.resumebuilderapi.repository.ResumeRepository;
import in.rithik.resumebuilderapi.repository.PaymentRepository;
import in.rithik.resumebuilderapi.repository.UserAiStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;
    private final PaymentRepository paymentRepository;
    private final UserAiStatsRepository statsRepository;

    public Map<String, Object> getAnalytics() {
        long totalUsers = userRepository.count();
        long premiumUsers = userRepository.countBySubscriptionPlan("premium");
        long totalResumes = resumeRepository.count();
        long totalPayments = paymentRepository.count();

        long totalRevenue = paymentRepository.findAll().stream()
                .filter(p -> "paid".equalsIgnoreCase(p.getStatus()))
                .mapToLong(Payment::getAmount)
                .sum();

        long totalAiRewrites = statsRepository.findAll().stream()
                .mapToLong(UserAiStats::getDailyRewriteCount)
                .sum();

        Map<String, Object> analytics = new HashMap<>();
        analytics.put("totalUsers", totalUsers);
        analytics.put("premiumUsers", premiumUsers);
        analytics.put("totalResumes", totalResumes);
        analytics.put("totalPayments", totalPayments);
        analytics.put("totalRevenuePaise", totalRevenue);
        analytics.put("totalAiRewrites", totalAiRewrites);

        return analytics;
    }

    public List<User> getUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc();
    }

    public User toggleUserStatus(String userId, Object principal) {
        User currentUser = (User) principal;
        if (currentUser.getId().equals(userId)) {
            throw new RuntimeException("Privilege Escalation Protection: You cannot disable your own admin account.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setActive(!user.isActive());
        return userRepository.save(user);
    }

    public void deleteUser(String userId, Object principal) {
        User currentUser = (User) principal;
        if (currentUser.getId().equals(userId)) {
            throw new RuntimeException("Privilege Escalation Protection: You cannot delete your own admin account.");
        }

        if (!userRepository.existsById(userId)) {
            throw new RuntimeException("User not found");
        }

        // Cascade delete all associated documents
        resumeRepository.deleteByUserId(userId);
        paymentRepository.deleteByUserId(userId);
        statsRepository.deleteByUserId(userId);
        userRepository.deleteById(userId);

        log.info("Admin {} deleted user {}", currentUser.getEmail(), userId);
    }

    public List<Resume> getResumes() {
        return resumeRepository.findAllByOrderByUpdatedAtDesc();
    }

    public void deleteResume(String resumeId) {
        if (!resumeRepository.existsById(resumeId)) {
            throw new RuntimeException("Resume not found");
        }
        resumeRepository.deleteById(resumeId);
    }

    public List<Payment> getPayments() {
        return paymentRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<UserAiStats> getAiStats() {
        return statsRepository.findAll();
    }
}
