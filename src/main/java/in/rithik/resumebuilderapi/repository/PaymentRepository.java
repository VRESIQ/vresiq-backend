package in.rithik.resumebuilderapi.repository;

import in.rithik.resumebuilderapi.document.Payment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends MongoRepository<Payment, String> {
    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);

    List<Payment> findByUserIdOrderByCreatedAtDesc(String UserId);

    List<Payment> findByStatus(String Status);
    List<Payment> findAllByOrderByCreatedAtDesc();
    void deleteByUserId(String userId);
}
