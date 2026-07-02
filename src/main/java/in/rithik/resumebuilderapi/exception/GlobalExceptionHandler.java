package in.rithik.resumebuilderapi.exception;

import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,Object>> handleValidationException(MethodArgumentNotValidException ex){
        log.info("InsideGlobalExceptionhandler - handleValidationException()");
        recordException(ex);
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError)error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Validation failed");
        response.put("errors", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    @ExceptionHandler(ResourceExistsException.class)
    public ResponseEntity<Map<String, Object>> handleResourceExistsException(ResourceExistsException ex){
        log.info("InsideGlobalExceptionhandler - handleResourceExistsException()");
        recordException(ex);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Email already exists");
        response.put("errors", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUsernameNotFoundException(UsernameNotFoundException ex){
        log.info("InsideGlobalExceptionhandler - handleUsernameNotFoundException()");
        recordException(ex);
        Map<String, Object> response = new HashMap<>();
        response.put("message", ex.getMessage());
        response.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<Map<String, Object>> handleEmailNotVerifiedException(EmailNotVerifiedException ex){
        log.info("InsideGlobalExceptionHandler - handleEmailNotVerifiedException()");
        // Do NOT call recordException(ex) here to avoid reporting to Sentry
        Map<String, Object> response = new HashMap<>();
        response.put("message", ex.getMessage());
        response.put("error", "EMAIL_NOT_VERIFIED");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxSizeException(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        log.info("InsideGlobalExceptionHandler - handleMaxSizeException()");
        // Do NOT call recordException(ex) as it is a user/validation constraint error
        Map<String, Object> response = new HashMap<>();
        response.put("message", "File upload failed");
        response.put("error", "The uploaded file exceeds the maximum allowed size limit of 2MB.");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    @ExceptionHandler(AccountSuspendedException.class)
    public ResponseEntity<Map<String, Object>> handleAccountSuspendedException(AccountSuspendedException ex) {
        log.info("InsideGlobalExceptionHandler - handleAccountSuspendedException()");
        Map<String, Object> response = new HashMap<>();
        response.put("message", ex.getMessage());
        response.put("error", "ACCOUNT_SUSPENDED");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex){
        log.error("InsideGlobalExceptionhandler - handleRuntimeException() exception occurred: ", ex);
        recordException(ex);
        Map<String, Object> response = new HashMap<>();
        response.put("message", ex.getMessage());
        response.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex){
        log.error("InsideGlobalExceptionhandler - handleGenericException() exception occurred: ", ex);
        recordException(ex);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Something went wrong. Contact the administrator");
        response.put("errors", ex.getMessage());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private void recordException(Throwable ex) {
        if (Sentry.isEnabled()) {
            // Filter out expected user action exceptions
            if (ex instanceof MethodArgumentNotValidException ||
                ex instanceof ResourceExistsException ||
                ex instanceof UsernameNotFoundException ||
                ex instanceof EmailNotVerifiedException ||
                ex instanceof org.springframework.web.multipart.MaxUploadSizeExceededException) {
                return;
            }

            // Inspect RuntimeException messages for expected business-rule rejections
            if (ex instanceof RuntimeException) {
                String msg = ex.getMessage();
                if (msg != null) {
                    String lower = msg.toLowerCase();
                    if (lower.contains("upgrade your plan") ||
                        lower.contains("cooldown") ||
                        lower.contains("unauthorized") ||
                        lower.contains("not found") ||
                        lower.contains("invalid") ||
                        lower.contains("quota reached")) {
                        return;
                    }
                }
            }
            Sentry.captureException(ex);
        }
    }
}
