package in.rithik.resumebuilderapi.controller;

import in.rithik.resumebuilderapi.document.User;
import in.rithik.resumebuilderapi.dto.AuthResponse;
import in.rithik.resumebuilderapi.dto.LoginRequest;
import in.rithik.resumebuilderapi.dto.RegisterRequest;
import in.rithik.resumebuilderapi.service.AuthService;
import in.rithik.resumebuilderapi.service.FileUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import static in.rithik.resumebuilderapi.util.AppConstants.*;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping(AUTH_CONTROLLER)
public class AuthController {

    private final AuthService authService;
    private final FileUploadService fileUploadService;

    @PostMapping(REGISTER)
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request){
        log.info("Inside AuthController - register(): {}", request);
        AuthResponse response = authService.register(request);
        log.info("Response from service: {}", response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @GetMapping(VERIFY_EMAIL)
    public ResponseEntity<?> verifyEmail(@RequestParam String token, @RequestParam(required = false) String email){
        log.info("Inside AuthController - verifyEmail() called");
        String status = authService.verifyEmail(token, email);
        String message;
        HttpStatus httpStatus = HttpStatus.OK;

        switch (status) {
            case "verified":
                message = "Email verified successfully";
                break;
            case "already_verified":
                message = "Your email is already verified. Please sign in.";
                break;
            case "expired":
                message = "Verification link expired";
                httpStatus = HttpStatus.BAD_REQUEST;
                break;
            default:
                message = "Invalid verification link";
                httpStatus = HttpStatus.BAD_REQUEST;
                break;
        }

        return ResponseEntity.status(httpStatus).body(Map.of("status", status, "message", message));
    }
    @PostMapping(UPLOAD_IMAGE)
    public ResponseEntity<?> uploadImage(@RequestPart("image")MultipartFile file) throws IOException {
        log.info("Inside AuthController - uploadImage()");
        Map<String, String> response= fileUploadService.uploadSingleImage(file);
        return ResponseEntity.ok(response);
    }

    @PostMapping(LOGIN)
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request){
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(REFRESH)
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body){
        String refreshToken = body.get("refreshToken");
        AuthResponse response = authService.refreshAccessToken(refreshToken);
        return ResponseEntity.ok(response);
    }

    @PostMapping(LOGOUT)
    public ResponseEntity<?> logout(Authentication authentication){
        return ResponseEntity.ok(authService.logout(authentication.getPrincipal()));
    }

    @PostMapping(RESEND_VERIFICATION)
    public ResponseEntity<?> resendVerification(@RequestBody Map<String, String> body){
        //Step1: get the email from the request
        String email = body.get("email");
        //Step2: add the validations
        if(Objects.isNull(email)){
            return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
        }
        //Step3: call the service method to resend the verification link
        authService.resendVerification(email);

        //Step4: return response
        return ResponseEntity.ok(Map.of("success", "true", "message", "Verification email sent"));
    }
    @GetMapping(PROFILE)
    public ResponseEntity<?> getProfile(Authentication authentication){
        Object principalObject = authentication.getPrincipal();
        AuthResponse currentProfile = authService.getProfile(principalObject);
        return ResponseEntity.ok(currentProfile);
    }

    @PutMapping(PROFILE)
    public ResponseEntity<?> updateProfile(Authentication authentication, @RequestBody Map<String, String> updates){
        Object principalObject = authentication.getPrincipal();
        AuthResponse updatedProfile = authService.updateProfile(principalObject, updates);
        return ResponseEntity.ok(updatedProfile);
    }

}
