package in.rithik.resumebuilderapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthResponse {
    private String id;
    private String name;
    private String email;
    private String profileImageUrl;
    private String subscriptionPlan;
    private Boolean emailVerified;
    private String token;
    private String refreshToken;
    private String role;
    private Boolean mustResetPassword;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
