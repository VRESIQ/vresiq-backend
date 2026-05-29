package in.rithik.resumebuilderapi.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import in.rithik.resumebuilderapi.document.Resume;
import in.rithik.resumebuilderapi.dto.AuthResponse;
import in.rithik.resumebuilderapi.repository.ResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadService {

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is empty or not provided.");
        }
        long maxSizeBytes = 2 * 1024 * 1024;
        if (file.getSize() > maxSizeBytes) {
            throw new RuntimeException("File size exceeds the maximum limit of 2MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png") && !contentType.equals("image/webp"))) {
            throw new RuntimeException("Invalid file type. Only JPEG, PNG, and WEBP images are allowed.");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.contains(".")) {
            throw new RuntimeException("Invalid file name.");
        }
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        if (!extension.equals("jpg") && !extension.equals("jpeg") && !extension.equals("png") && !extension.equals("webp")) {
            throw new RuntimeException("Invalid file extension. Only .jpg, .jpeg, .png, and .webp extensions are allowed.");
        }
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(file.getInputStream());
            if (img == null) {
                throw new RuntimeException("Invalid image file.");
            }
            if (img.getWidth() > 2000 || img.getHeight() > 2000) {
                throw new RuntimeException("Image dimensions are too large. Maximum resolution is 2000x2000 pixels.");
            }
        } catch (Exception e) {
            throw new RuntimeException("Image validation failed: " + e.getMessage());
        }
    }

    private final Cloudinary cloudinary;
    private final AuthService authService;
    private final ResumeRepository resumeRepository;

    public Map<String, String> uploadSingleImage(MultipartFile file) throws IOException {
        validateImage(file);
        Map<String, Object> imageUploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap("resource_type", "image"));
        log.info("Inside FileUploadService - uploadSingleImage() {}", imageUploadResult.get("secure_url").toString());
        return Map.of("imageUrl",imageUploadResult.get("secure_url").toString());
    }

    public Map<String, String> uploadResumeImages(String resumeId,
                                                  Object principal,
                                                  MultipartFile thumbnail,
                                                  MultipartFile profileImage) throws IOException {
        // 1. get the current profile
        AuthResponse response = authService.getProfile(principal);
        // 2. get the existing resume
        Resume existingResume = resumeRepository.findByUserIdAndId(response.getId(), resumeId )
                .orElseThrow(()-> new RuntimeException("Resume not found"));
        // 3. upload resume images & set the resume
        Map<String, String> returnValue =  new HashMap<>();
        Map<String, String> uploadResult;
        if(Objects.nonNull(thumbnail)){
            uploadResult = uploadSingleImage(thumbnail);
            existingResume.setThumbnailLink(uploadResult.get("imageUrl"));
            returnValue.put("thumbnailLink", uploadResult.get("imageUrl"));
        }
        if(Objects.nonNull(profileImage)){
            uploadResult = uploadSingleImage(profileImage);
            if(Objects.isNull(existingResume.getProfileInfo())){
                existingResume.setProfileInfo(new Resume.ProfileInfo());
            }
            existingResume.getProfileInfo().setProfilePreviewUrl(uploadResult.get("imageUrl"));
            returnValue.put("ProfilePreviewUrl", uploadResult.get("imageUrl"));
            returnValue.put("profilePreviewUrl", uploadResult.get("imageUrl"));
        }
        // 4. save details into db
        resumeRepository.save(existingResume);
        returnValue.put("message", "Images uploaded sucessfully");
        // 5. return the rsult
        return  returnValue;
    }
}
