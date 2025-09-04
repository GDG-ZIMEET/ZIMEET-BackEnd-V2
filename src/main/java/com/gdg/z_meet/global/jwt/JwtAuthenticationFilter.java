package com.gdg.z_meet.global.jwt;

import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.RefreshTokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    private static final Set<String> loggedAccessTokens = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> loggedRefreshTokens = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> loggedMissingTokens = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> loggedInvalidTokens = Collections.synchronizedSet(new HashSet<>());

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/health")
                || path.startsWith("/swagger") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")
                || path.startsWith("/resources/static/")
                || path.startsWith("/api/booths") || path.startsWith("/api/event")
                || path.startsWith("/api/user")
                || path.startsWith("/api/ws") || path.startsWith("/api/ws/info")
                || path.startsWith("/ws") || path.startsWith("/ws/info");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        String accessToken = jwtUtil.getAccessToken(request);
        String refreshToken = jwtUtil.getRefreshTokenFromCookie(request);

        log.debug("Request URI: {}, Access Token: {}, Refresh Token: {}", uri, accessToken, refreshToken);

        if (uri.equals("/api/user/signup") || uri.equals("/api/user/login")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (accessToken != null && jwtUtil.validateToken(request, accessToken)) {
                setAuthentication(accessToken, response);
                if (!loggedAccessTokens.contains(accessToken)) {
                    log.info("Access token validated successfully");
                    loggedAccessTokens.add(accessToken);
                }
            } else if (refreshToken != null) {
                if (jwtUtil.validateRefreshToken(refreshToken)) {
                    String userIdStr = refreshTokenRepository.findByToken(refreshToken);
                    if (!loggedRefreshTokens.contains(refreshToken)) {
                        log.info("User ID found in Redis: {}", userIdStr);
                        loggedRefreshTokens.add(refreshToken);
                    }
                    if (userIdStr != null) {
                        Long userId = Long.parseLong(userIdStr);
                        String studentNumber = jwtUtil.getStudentNumberFromToken(refreshToken);
                        String newAccessToken = jwtUtil.getToken(studentNumber, userId, new Date(), jwtUtil.getAccessTokenValidTime());

                        response.setHeader("Authorization", BEARER_PREFIX + newAccessToken);

                        // 요청 객체에 새 토큰 반영
                        HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(request) {
                            @Override
                            public String getHeader(String name) {
                                if ("Authorization".equalsIgnoreCase(name)) {
                                    return BEARER_PREFIX + newAccessToken;
                                }
                                return super.getHeader(name);
                            }
                        };
                        setAuthentication(newAccessToken, response);
                        if (!loggedAccessTokens.contains(newAccessToken)) {
                            log.info("Access token refreshed for userId: {}", userId);
                            loggedAccessTokens.add(newAccessToken);
                        }
                        filterChain.doFilter(wrappedRequest, response);
                        return;
                    } else {
                        if (!loggedMissingTokens.contains(refreshToken)) {
                            log.warn("Refresh token not found in Redis: {}", refreshToken);
                            loggedMissingTokens.add(refreshToken);
                        }
                        sendErrorResponse(response, "Invalid refresh token", HttpStatus.UNAUTHORIZED);
                        return;
                    }
                } else {
                    if (!loggedInvalidTokens.contains(refreshToken)) {
                        log.warn("Refresh token validation failed: {}", refreshToken);
                        loggedInvalidTokens.add(refreshToken);
                    }
                    sendErrorResponse(response, "Invalid refresh token", HttpStatus.UNAUTHORIZED);
                    return;
                }
            } else {
                String clientIdentifier = request.getRemoteAddr();
                if (!loggedInvalidTokens.contains(clientIdentifier)) {
                    log.warn("No valid access or refresh token provided for client: {}", clientIdentifier);
                    loggedInvalidTokens.add(clientIdentifier);
                }
                sendErrorResponse(response, "Authentication required", HttpStatus.UNAUTHORIZED);
                return;
            }
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.error("JWT processing failed: {}", e.getMessage());
            sendErrorResponse(response, "Authentication failed", HttpStatus.UNAUTHORIZED);
        }
    }

    private void setAuthentication(String token, HttpServletResponse response) throws IOException {
        try {
            Authentication authentication = jwtUtil.getAuthentication(token);
            if (authentication != null) {
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authentication set successfully for token: {}", token);
            } else {
                log.warn("Authentication object is null for token: {}", token);
                sendErrorResponse(response, "Authentication failed", HttpStatus.UNAUTHORIZED);
            }
        } catch (UsernameNotFoundException e) {
            log.warn("User authentication failed: {}", e.getMessage());
            sendErrorResponse(response, e.getMessage(), HttpStatus.FORBIDDEN);
        }
    }

    private void sendErrorResponse(HttpServletResponse response, String message, HttpStatus status) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write(String.format("{\"error\": \"%s\"}", message));
    }
}