package in.rithik.resumebuilderapi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateResumeRequest {

    @NotBlank(message = "title is required")
    private String title;

    private String template;
}
