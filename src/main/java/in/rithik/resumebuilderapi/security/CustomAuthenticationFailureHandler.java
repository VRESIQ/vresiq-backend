package in.rithik.resumebuilderapi.security;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @org.springframework.beans.factory.annotation.Value("${app.frontend.public-url}")
    private String frontendPublicUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        String baseUrl = "http://localhost:5173";
        if (frontendPublicUrl != null && !frontendPublicUrl.isBlank()) {
            baseUrl = frontendPublicUrl.split(",")[0].trim();
        }
        String targetUrl = UriComponentsBuilder.fromUriString(baseUrl + "/login")
                .queryParam("error", exception.getLocalizedMessage())
                .build().toUriString();
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
