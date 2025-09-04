package com.gdg.z_meet.global.jwt;

import com.gdg.z_meet.domain.user.dto.Token;
import com.gdg.z_meet.domain.user.repository.RefreshTokenRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.*;

@Component
@RequiredArgsConstructor
public class JwtUtil {
    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final long ACCESS_TOKEN_VALID_TIME = 7 * 24 * 60 * 60 * 1000L;  // 7일
    private static final long REFRESH_TOKEN_VALID_TIME = 31 * 24 * 60 * 60 * 1000L; // 31일

    private final UserDetailsService userDetailsService;
    private final RefreshTokenRepository refreshTokenRepository;

    private static final Set<String> loggedRefreshTokens = Collections.synchronizedSet(new HashSet<>());

    @Value("${jwt.secret}")
    private String secretKey;
    private Key key;
    private JwtParser jwtParser;

    @PostConstruct
    protected void init() {
        byte[] bytes = Base64.getDecoder().decode(secretKey);
        key = Keys.hmacShaKeyFor(bytes);
        jwtParser = Jwts.parserBuilder().setSigningKey(key).build();
    }

    public Token createToken(String studentNumber, Long id) {
        Date now = new Date();
        String accessToken = getToken(studentNumber, id, now, ACCESS_TOKEN_VALID_TIME);
        String refreshToken = getToken(studentNumber, id, now, REFRESH_TOKEN_VALID_TIME);
        return Token.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .key(studentNumber)
                .userId(id)
                .build();
    }

    public String getToken(String studentNumber, Long id, Date currentTime, long validTime) {
        return Jwts.builder()
                .setSubject(studentNumber)
                .claim("user_id", id)
                .setIssuedAt(currentTime)
                .setExpiration(new Date(currentTime.getTime() + validTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public void createCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(REFRESH_TOKEN_VALID_TIME / 1000)
                .sameSite("None")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        log.info("Refresh token set in cookie: name={}, value={}, maxAge={}",
                REFRESH_TOKEN_COOKIE, refreshToken, cookie.getMaxAge());
    }

    public String getRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            if (!loggedRefreshTokens.contains("NO_COOKIES")) {
                log.warn("No cookies found in request");
                loggedRefreshTokens.add("NO_COOKIES");
            }
            return null;
        }

        String refreshToken = Arrays.stream(request.getCookies())
                .filter(cookie -> REFRESH_TOKEN_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);

        if (refreshToken != null && !loggedRefreshTokens.contains(refreshToken)) {
            log.info("Refresh token from cookie: {}", refreshToken);
            loggedRefreshTokens.add(refreshToken);
        } else if (refreshToken == null && !loggedRefreshTokens.contains("NO_REFRESH")) {
            log.info("Refresh token from cookie: Not found");
            loggedRefreshTokens.add("NO_REFRESH");
        }

        return refreshToken;
    }

    public Authentication getAuthentication(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                log.warn("Token is null or empty");
                return null;
            }
            String availableToken = extractTokenFromHeader(token);
            log.debug("Extracted token: {}", availableToken);
            if (availableToken == null || availableToken.trim().isEmpty()) {
                log.warn("Extracted token is null or empty after processing");
                return null;
            }
            String studentNumber = getStudentNumberFromToken(availableToken);
            log.debug("Extracted studentNumber: {}", studentNumber);
            UserDetails userDetails = userDetailsService.loadUserByUsername(studentNumber);
            log.debug("Loaded userDetails: {}", userDetails.getUsername());
            return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
        } catch (Exception e) {
            log.error("Failed to get authentication: {}", e.getMessage());
            return null;
        }
    }

    public String getStudentNumberFromToken(String token) {
        return jwtParser.parseClaimsJws(token).getBody().getSubject();
    }

    public Long getUserIdFromToken(String token) {
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();      // user_id 파싱을 위한 Bearer 제거
        }

        return jwtParser.parseClaimsJws(token).getBody().get("user_id", Long.class);
    }

    public String getAccessToken(HttpServletRequest request) {
        return request.getHeader("Authorization");
    }

    // 이것도 뜸
    public String extractTokenFromHeader(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring("Bearer ".length());
        }
        return token;
    }

    public boolean validateRefreshToken(String refreshToken) {
        try {
            Jws<Claims> claims = jwtParser.parseClaimsJws(refreshToken);
            if (claims.getBody().getExpiration().before(new Date())) {
                log.warn("Refresh token expired: {}", refreshToken);
                return false;
            }
            String userId = refreshTokenRepository.findByToken(refreshToken);
            if (userId == null) {
                log.warn("Refresh token not found in Redis: {}", refreshToken);
                return false;
            }
            // 여기에 걸림
            log.info("Refresh token validated successfully: {}", refreshToken);
            return true;
        } catch (JwtException e) {
            log.error("Invalid refresh token format: {}", e.getMessage());
            return false;
        }
    }

    public boolean validateToken(ServletRequest request, String jwtToken) {
        try {
            if (jwtToken == null || jwtToken.trim().isEmpty()) {
                log.warn("JWT token is null or empty");
                return false;
            }
            String token = extractTokenFromHeader(jwtToken);
            if (token == null || token.trim().isEmpty()) {
                log.warn("Extracted token is null or empty");
                return false;
            }
            Claims claims = jwtParser.parseClaimsJws(token).getBody();
            return !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            request.setAttribute("exception", e.getClass().getSimpleName());
            log.warn("Invalid token: {}", e.getMessage());
            return false;
        }
    }

    public Long extractUserIdFromToken(String token) {
        String availableToken = extractTokenFromHeader(token);
        return getUserIdFromToken(availableToken);
    }

    public Long extractUserIdFromRequest(HttpServletRequest request) {
        String token = getAccessToken(request);
        if (token == null || token.trim().isEmpty()) {
            log.warn("No Authorization header found in request");
            return null;
        }
        String availableToken = extractTokenFromHeader(token);
        if (availableToken == null || availableToken.trim().isEmpty()) {
            return null;
        }
        return getUserIdFromToken(availableToken);
    }

    public long getAccessTokenValidTime() {
        return ACCESS_TOKEN_VALID_TIME;
    }

    public long getRefreshTokenValidTime() {
        return REFRESH_TOKEN_VALID_TIME;
    }
}