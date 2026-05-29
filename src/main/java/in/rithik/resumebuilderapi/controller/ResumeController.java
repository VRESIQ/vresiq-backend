package in.rithik.resumebuilderapi.controller;

import in.rithik.resumebuilderapi.document.Resume;
import in.rithik.resumebuilderapi.dto.CreateResumeRequest;
import in.rithik.resumebuilderapi.dto.ResumeUpdateRequest;
import in.rithik.resumebuilderapi.service.FileUploadService;
import in.rithik.resumebuilderapi.service.ResumeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static in.rithik.resumebuilderapi.util.AppConstants.*;

@RestController
@RequestMapping(RESUME)
@RequiredArgsConstructor
@Slf4j
public class ResumeController {

    private final ResumeService resumeService;
    private final FileUploadService fileUploadService;

    @PostMapping
    public ResponseEntity<?> createResume(@Valid @RequestBody CreateResumeRequest request,
                                          Authentication authentication) {
        try {
            //Step1: call the service method
            Resume newResume = resumeService.createResume(request, authentication.getPrincipal());
            //Step 2: return response
            return ResponseEntity.status(HttpStatus.CREATED).body(newResume);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getUserResumes(Authentication authentication){
        // 1. call the service method
        List<Resume> resumes = resumeService.getUserResumes(authentication.getPrincipal());
        // 2. return response
        return ResponseEntity.ok(resumes);
    }

    @GetMapping(ID)
    public ResponseEntity<?> getResumeById(@PathVariable String id, Authentication authentication){
        // 1. call service method
        Resume existingResume = resumeService.getResumeById(id , authentication.getPrincipal());
        // 2. return the response
        return ResponseEntity.ok(existingResume);
    }

    @PutMapping(ID)
    public ResponseEntity<?> updateResume(@PathVariable String id,
                                          @RequestBody ResumeUpdateRequest updatedData,
                                          Authentication authentication){
        // 1. call service method
        Resume updatedResume = resumeService.updateResume(id, updatedData, authentication.getPrincipal());

        // 2. return the response
        return ResponseEntity.ok(updatedResume);
    }

    @PutMapping(UPLOAD_IMAGES)
    public ResponseEntity<?> uploadResumeImages(@PathVariable String id,
                                                @RequestPart(value = "thumbnail", required = true)MultipartFile thumbnail,
                                                @RequestPart(value ="profileImage", required = false)MultipartFile profileImage,
                                                HttpServletRequest request,
                                                Authentication authentication) throws IOException {
        // 1. call service method
        Map<String , String> response = fileUploadService.uploadResumeImages(id, authentication.getPrincipal(), thumbnail, profileImage);
        // 2. return response
        return ResponseEntity.ok(response);
    }
    @DeleteMapping(ID)
    public ResponseEntity<?> deleteResume(@PathVariable String id,
                                          Authentication authentication){
        // 1. call the service method
        resumeService.deleteResume(id, authentication.getPrincipal());
        // 2. return response
        return ResponseEntity.ok(Map.of("message", "Resume deleted successfully"));
    }
}
