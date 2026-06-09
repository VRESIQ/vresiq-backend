package in.rithik.resumebuilderapi.controller;

import com.razorpay.RazorpayException;
import in.rithik.resumebuilderapi.document.Payment;
import in.rithik.resumebuilderapi.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static in.rithik.resumebuilderapi.util.AppConstants.PREMIUM;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payment")
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, String> request,
                                               Authentication authentication) throws RazorpayException {
        // 1. validate request
        String planType = request.get("planType");
        if(!PREMIUM.equalsIgnoreCase(planType)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid plan type"));
        }
        // 2. call service method
        Payment payment = paymentService.createOrder(authentication.getPrincipal(), planType);
        // 3. prepare response object
        Map<String, Object> response = Map.of(
                "orderId", payment.getRazorpayOrderId(),
                "amount" , payment.getAmount(),
                "currency", payment.getCurrency(),
                "receipt", payment.getReceipt()
        );
        // 4. return response
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(@RequestBody Map<String, String> request,
                                           Authentication authentication) throws RazorpayException {
        // 1. validate the request
        String razorpayOrderId = request.get("razorpay_order_id");
        String razorpayPaymentId = request.get("razorpay_payment_id");
        String razorpaySignature = request.get("razorpay_signature");

        if(Objects.isNull(razorpayOrderId) || Objects.isNull(razorpayPaymentId) || Objects.isNull(razorpaySignature)) {
            return ResponseEntity.badRequest().body(Map.of("message", "missing required payment parameters"));
        }
        // 2. call service method
        boolean isValid = paymentService.verifyPayment(authentication.getPrincipal(), razorpayOrderId, razorpayPaymentId, razorpaySignature);
        // 3. return response
        if(isValid) {
            return ResponseEntity.ok(Map.of(
                    "message" , "payment verified successfully",
                    "status" , "success"
            ));
        }
        else{
            return ResponseEntity.badRequest().body(Map.of("message", "payment verification failed"));
        }

    }

    @GetMapping("/history")
    public ResponseEntity<?> getPaymentHistory(Authentication authentication){
        // 1. call service
        List<Payment> payments = paymentService.getUserPayments(authentication.getPrincipal());
        // 2. return response
        return ResponseEntity.ok(payments);
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<?> getOrderDetails(@PathVariable String orderId, Authentication authentication){
        // 1. call service method
        Payment paymentDetails = paymentService.getPaymentDetails(orderId, authentication.getPrincipal());
        // 2. return response
        return ResponseEntity.ok(paymentDetails);
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature
    ) {
        log.info("Received Razorpay Webhook request. Signature: {}", signature);
        try {
            boolean success = paymentService.handleWebhook(payload, signature);
            if (success) {
                return ResponseEntity.ok(Map.of("status", "success", "message", "Webhook processed successfully"));
            } else {
                return ResponseEntity.badRequest().body(Map.of("status", "failed", "message", "Webhook processing failed or signature invalid"));
            }
        } catch (Exception e) {
            log.error("Error processing Razorpay Webhook", e);
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
