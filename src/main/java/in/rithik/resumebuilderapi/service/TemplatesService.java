package in.rithik.resumebuilderapi.service;

import in.rithik.resumebuilderapi.dto.AuthResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static in.rithik.resumebuilderapi.util.AppConstants.PREMIUM;

@Service
@RequiredArgsConstructor
@Slf4j

public class TemplatesService {

    private final AuthService authService;
    public Map<String, Object> getTemplates(Object principal) {
        // 1. get the current profile
        AuthResponse authResponse = authService.getProfile(principal);
        // 2. get available templates based on subscription
        List<String> availableTemplates;

        Boolean isPremium = PREMIUM.equalsIgnoreCase(authResponse.getSubscriptionPlan());

        if(isPremium==true){
            availableTemplates = List.of("01", "02","03");
        }
        else{
            availableTemplates = List.of("01");
        }
        // 3. add data into map
        Map<String, Object> restrictions = new HashMap<>();
        restrictions.put("availableTemplates", availableTemplates);
        restrictions.put("allTemplates", List.of("01", "02", "03"));
        restrictions.put("subscriptionPlan", authResponse.getSubscriptionPlan());
        restrictions.put("isPremium", isPremium);
        // 4. return result
        return restrictions;
    }
}
