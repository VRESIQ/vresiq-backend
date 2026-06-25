package in.rithik.resumebuilderapi.config;

import in.rithik.resumebuilderapi.security.JwtAuthenticationEntryPoint;
import in.rithik.resumebuilderapi.security.JwtAuthenticationFilter;
import in.rithik.resumebuilderapi.security.RateLimitingFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/*
Purpose: Configures Spring Security chain, stateless sessions, password encoder, CORS permissions, and filters.
Used By: Spring Boot context initialization
Request Flow: Web Request -> SecurityFilterChain -> Controller
Data Flow: HTTP Client Context -> SecurityConfig Rules -> Application Context
Learn: EnableWebSecurity, SecurityFilterChain, CORS Configurations
*/
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final RateLimitingFilter rateLimitingFilter;
    private final in.rithik.resumebuilderapi.security.CustomOAuth2UserService customOAuth2UserService;
    private final in.rithik.resumebuilderapi.security.CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
    private final in.rithik.resumebuilderapi.security.CustomAuthenticationFailureHandler customAuthenticationFailureHandler;

    @org.springframework.beans.factory.annotation.Value("${app.frontend.cors-origins}")
    private String frontendCorsOrigins;


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/verify-email",
                                "/api/auth/resend-verification",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/auth/refresh",
                                "/api/auth/oauth/**",
                                "/api/auth/phone/verify",
                                "/api/auth/providers",
                                "/api/verify/**",
                                "/api/payment/webhook",
                                "/actuator/**",
                                "/login/oauth2/code/**",
                                "/oauth2/authorization/**"
                        ).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                        .successHandler(customAuthenticationSuccessHandler)
                        .failureHandler(customAuthenticationFailureHandler)
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(jwtAuthenticationEntryPoint)
                )
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitingFilter,
                        JwtAuthenticationFilter.class);

        return http.build();
    }


    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> exactOrigins;
        List<String> originPatterns;

        if (frontendCorsOrigins != null && !frontendCorsOrigins.isBlank()) {
            List<String> configuredOrigins = Arrays.stream(frontendCorsOrigins.split(","))
                    .map(String::trim)
                    .map(s -> s.endsWith("/") ? s.substring(0, s.length() - 1) : s)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            exactOrigins = configuredOrigins.stream()
                    .filter(s -> !s.contains("*"))
                    .collect(Collectors.toList());
            originPatterns = configuredOrigins.stream()
                    .filter(s -> s.contains("*"))
                    .collect(Collectors.toList());
        } else {
            exactOrigins = Arrays.asList("http://localhost:5173");
            originPatterns = Arrays.asList();
        }

        if (!exactOrigins.isEmpty()) {
            configuration.setAllowedOrigins(exactOrigins);
        }
        if (!originPatterns.isEmpty()) {
            configuration.setAllowedOriginPatterns(originPatterns);
        }

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
