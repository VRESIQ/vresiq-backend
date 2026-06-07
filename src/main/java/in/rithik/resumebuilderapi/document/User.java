package in.rithik.resumebuilderapi.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "users")
public class User {
    @org.springframework.data.annotation.Id
    @com.fasterxml.jackson.annotation.JsonProperty("_id")
    private String id;
    private String name;
    @org.springframework.data.mongodb.core.index.Indexed(unique = true)
    private String email;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String password;
    private String profileImageUrl;
    @lombok.Builder.Default
    private String subscriptionPlan = "basic";
    @lombok.Builder.Default
    private boolean emailVerified = false;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @org.springframework.data.mongodb.core.index.Indexed(sparse = true)
    private String verificationToken;
    private LocalDateTime verificationExpires;
    private LocalDateTime lastVerificationSent;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @org.springframework.data.mongodb.core.index.Indexed(sparse = true)
    private String passwordResetToken;
    private LocalDateTime passwordResetExpires;
    private LocalDateTime lastPasswordResetRequest;

    @lombok.Builder.Default
    private String role = "USER";
    @lombok.Builder.Default
    private boolean active = true;
    @lombok.Builder.Default
    private boolean mustResetPassword = false;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String refreshTokenHash;
    private LocalDateTime refreshTokenExpiresAt;
    private LocalDateTime refreshTokenIssuedAt;

    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;

}
