package in.rithik.resumebuilderapi.security;

import in.rithik.resumebuilderapi.document.User;
import in.rithik.resumebuilderapi.repository.UserRepository;
import in.rithik.resumebuilderapi.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/*
Purpose: Intercepts requests to extract and validate Bearer JWT access tokens.
Used By: Spring Security filter pipeline
Request Flow: Frontend -> Security filter chain -> Controller
Data Flow: HTTP Authorization Header -> JwtAuthenticationFilter -> SecurityContextHolder
Learn: OncePerRequestFilter, SecurityContextHolder, UsernamePasswordAuthenticationToken
*/
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {

            String authHeader = request.getHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {

                String token = authHeader.substring(7);
                
                String tokenType = jwtUtil.getTokenTypeFromToken(token);
                if (tokenType == null || !tokenType.equalsIgnoreCase("access")) {
                    log.warn("Auth: Attempted access with non-access token type: {}", tokenType);
                    filterChain.doFilter(request, response);
                    return;
                }

                String userId = jwtUtil.getUserIdFromToken(token);

                if (userId != null &&
                        SecurityContextHolder.getContext().getAuthentication() == null &&
                        jwtUtil.validateToken(token) &&
                        !jwtUtil.isTokenExpired(token)) {

                    // DUAL-LOOKUP: Optimize lookup by checking type
                    User user;
                    if (userId.contains("@")) {
                        user = userRepository.findByEmail(userId).orElse(null);
                    } else {
                        user = userRepository.findById(userId)
                                .orElseGet(() -> userRepository.findByEmail(userId).orElse(null));
                    }

                    if (user == null) {
                        log.warn("Auth: User {} not found in database.", userId);
                        filterChain.doFilter(request, response);
                        return;
                    }

                    java.util.List<org.springframework.security.core.GrantedAuthority> authorities =
                            java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + user.getRole()));

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    user,
                                    null,
                                    authorities
                            );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request)
                    );

                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);
                }
            }

        } catch (Exception e) {
            log.error("JWT Authentication failed: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
