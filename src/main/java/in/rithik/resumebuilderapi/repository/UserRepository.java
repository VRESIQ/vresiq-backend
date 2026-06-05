package in.rithik.resumebuilderapi.repository;

import in.rithik.resumebuilderapi.document.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import javax.swing.text.html.Option;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User,String> {
    Optional<User> findByEmail(String email);
    Boolean existsByEmail(String email);
    Optional<User> findByVerificationToken(String VerificationToken);
    Optional<User> findByPasswordResetToken(String passwordResetToken);
    long countBySubscriptionPlan(String subscriptionPlan);
    java.util.List<User> findAllByOrderByCreatedAtDesc();

}
