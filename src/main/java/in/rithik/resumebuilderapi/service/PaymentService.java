package in.rithik.resumebuilderapi.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import in.rithik.resumebuilderapi.document.Payment;
import in.rithik.resumebuilderapi.document.User;
import in.rithik.resumebuilderapi.dto.AuthResponse;
import in.rithik.resumebuilderapi.repository.PaymentRepository;
import in.rithik.resumebuilderapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static in.rithik.resumebuilderapi.util.AppConstants.PREMIUM;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AuthService  authService;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;
    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    public Payment createOrder(Object principal, String planType) throws RazorpayException {
        // initial step
        AuthResponse authResponse = authService.getProfile(principal);
        // 1. initialize razorpay client
        RazorpayClient razorpayClient = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
        // 2. prepare JSON object to pass razorpay
        int amount = 9900; // amount in paise
        String currency = "INR";
        String receipt = PREMIUM+"_"+ UUID.randomUUID().toString().substring(0,8);

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amount);
        orderRequest.put("currency", currency);
        orderRequest.put("receipt", receipt);
        // 3. call the razorpay API to create order
        Order razorpayOrder = razorpayClient.orders.create(orderRequest);
        // 4. save the order details into db
        Payment newPayment= Payment.builder()
                .userId(authResponse.getId())
                .razorpayOrderId(razorpayOrder.get("id"))
                .amount(amount)
                .currency(currency)
                .planType(planType)
                .status("created")
                .receipt(receipt)
                .build();
        // 5. return result
        return paymentRepository.save(newPayment);
    }

    public boolean verifyPayment(Object principal, String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) throws RazorpayException {
        try{
            AuthResponse profile = authService.getProfile(principal);

            Payment payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId)
                    .orElseThrow(() -> new RuntimeException("Payment not found"));
            if (!payment.getUserId().equals(profile.getId())) {
                throw new RuntimeException("Unauthorized payment verification attempt.");
            }

            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", razorpayOrderId);
            attributes.put("razorpay_payment_id", razorpayPaymentId);
            attributes.put("razorpay_signature", razorpaySignature);

            boolean isValidSignature = Utils.verifyPaymentSignature(attributes, razorpayKeySecret);

            if(isValidSignature){
                // update payment status
                payment.setRazorpayPaymentId(razorpayPaymentId);
                payment.setRazorpaySignature(razorpaySignature);
                payment.setStatus("paid");
                paymentRepository.save(payment);

                // upgrade user subscription
                upgradeUserSubscription(payment.getUserId(), payment.getPlanType());
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            log.error("Error verifying the payment", e);
            return false;
        }
    }

    private void upgradeUserSubscription(String userId, String planType) {
        subscriptionService.updateSubscription(userId, planType);
    }

    public List<Payment> getUserPayments(Object principal) {
        // 1. get current profile
        AuthResponse  authResponse = authService.getProfile(principal);
        // 2. call repo finder method
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(authResponse.getId());
    }

    public Payment getPaymentDetails(String orderId, Object principal) {
        AuthResponse profile = authService.getProfile(principal);
        Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                .orElseThrow(()-> new RuntimeException("Payment not found"));

        if (!payment.getUserId().equals(profile.getId())) {
            throw new RuntimeException("Unauthorized access to payment order details.");
        }
        return payment;
    }

    @Value("${razorpay.webhook.secret:}")
    private String webhookSecret;

    public boolean handleWebhook(String payload, String signature) {
        // Validate signature if secret is configured
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            try {
                boolean isValid = Utils.verifyWebhookSignature(payload, signature, webhookSecret);
                if (!isValid) {
                    log.error("Razorpay Webhook: Signature verification failed.");
                    return false;
                }
            } catch (Exception e) {
                log.error("Razorpay Webhook: Error during signature verification: {}", e.getMessage());
                return false;
            }
        } else {
            log.warn("Razorpay Webhook: RAZORPAY_WEBHOOK_SECRET is not configured. Skipping signature verification.");
        }

        try {
            org.json.JSONObject json = new org.json.JSONObject(payload);
            String event = json.optString("event");
            log.info("Processing Razorpay Webhook Event: {}", event);

            if ("order.paid".equalsIgnoreCase(event) || "payment.captured".equalsIgnoreCase(event)) {
                org.json.JSONObject eventPayload = json.getJSONObject("payload");
                org.json.JSONObject paymentEntity = eventPayload.getJSONObject("payment").getJSONObject("entity");
                
                String orderId = paymentEntity.getString("order_id");
                String paymentId = paymentEntity.getString("id");

                Payment payment = paymentRepository.findByRazorpayOrderId(orderId).orElse(null);
                if (payment == null) {
                    log.warn("Razorpay Webhook: Payment record not found for Order ID: {}", orderId);
                    return false;
                }

                if ("paid".equalsIgnoreCase(payment.getStatus())) {
                    log.info("Razorpay Webhook: Payment for Order ID {} is already marked as paid. Skipping duplicate processing.", orderId);
                    return true;
                }

                payment.setStatus("paid");
                payment.setRazorpayPaymentId(paymentId);
                payment.setRazorpaySignature(signature);
                paymentRepository.save(payment);

                upgradeUserSubscription(payment.getUserId(), payment.getPlanType());
                log.info("Razorpay Webhook: Successfully updated payment status and upgraded user {} to {} plan", payment.getUserId(), payment.getPlanType());
                return true;
            }
            return true; // Return true for unhandled events to acknowledge receipt
        } catch (Exception e) {
            log.error("Razorpay Webhook: Error parsing webhook payload", e);
            return false;
        }
    }
}
