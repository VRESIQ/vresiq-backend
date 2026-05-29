package in.rithik.resumebuilderapi.controller;

import in.rithik.resumebuilderapi.service.VerifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/verify")
@RequiredArgsConstructor
public class VerifyController {

    private final VerifyService verifyService;

    @GetMapping("/github")
    public ResponseEntity<?> verifyGitHub(@RequestParam String username) {
        boolean valid = verifyService.verifyGitHub(username);
        return ResponseEntity.ok(Map.of("valid", valid));
    }

    @GetMapping("/institution")
    public ResponseEntity<?> verifyInstitution(@RequestParam String name) {
        boolean valid = verifyService.verifyInstitution(name);
        return ResponseEntity.ok(Map.of("valid", valid));
    }

    @GetMapping("/certification")
    public ResponseEntity<?> verifyCertification(@RequestParam String name) {
        boolean valid = verifyService.verifyCertification(name);
        return ResponseEntity.ok(Map.of("valid", valid));
    }
}
