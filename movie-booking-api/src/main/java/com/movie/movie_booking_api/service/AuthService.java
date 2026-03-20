// src/main/java/com/movie/movie_booking_api/service/AuthService.java
package com.movie.movie_booking_api.service;

import com.movie.movie_booking_api.config.JwtUtil;
import com.movie.movie_booking_api.dto.AuthResponse;
import com.movie.movie_booking_api.dto.LoginRequest;
import com.movie.movie_booking_api.dto.RegisterRequest;
import com.movie.movie_booking_api.dto.ChangePasswordRequest;
import com.movie.movie_booking_api.entity.User;
import com.movie.movie_booking_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final com.movie.movie_booking_api.service.MailService mailService;
    @org.springframework.beans.factory.annotation.Value("${verify.base-url:http://localhost:8081/api/auth/verify}")
    private String verifyBaseUrl;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        // check email trùng
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        String displayName = request.getName();
        if (displayName == null || displayName.trim().isEmpty()) {
            int at = email.indexOf('@');
            displayName = at > 0 ? email.substring(0, at) : email;
        }

        String token = java.util.UUID.randomUUID().toString().replace("-", "") + java.util.UUID.randomUUID().toString().replace("-", "");
        java.time.LocalDateTime expires = java.time.LocalDateTime.now().plusDays(2);
        
        // Auto assign ADMIN if first user, else CUSTOMER
        com.movie.movie_booking_api.entity.UserRole role = com.movie.movie_booking_api.entity.UserRole.CUSTOMER;
        if (userRepository.count() == 0) {
            role = com.movie.movie_booking_api.entity.UserRole.ADMIN;
        }
        
        User user = User.builder()
                .name(displayName)
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(displayName)
                .legacyName(displayName)
                .role(role)
                .emailVerified(Boolean.FALSE)
                .verificationToken(token)
                .verificationExpiresAt(expires)
                .build();

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        try {
            String link = verifyBaseUrl + "?token=" + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);
            mailService.sendVerificationEmail(user.getEmail(), link);
        } catch (Exception ignored) {}
        return new AuthResponse(null, user.getName(), user.getEmail());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản hoặc mật khẩu chưa chính xác"));

        if (Boolean.TRUE.equals(user.getLocked())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản của bạn đã bị khóa");
        }

        boolean isAdminAccount = user.getRole() == com.movie.movie_booking_api.entity.UserRole.ADMIN
                || user.getRole() == com.movie.movie_booking_api.entity.UserRole.STAFF;
        if (!isAdminAccount && !Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản chưa xác minh email. Vui lòng kiểm tra hộp thư hoặc gửi lại xác minh.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản hoặc mật khẩu chưa chính xác");
        }

        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthResponse(token, user.getName(), user.getEmail());
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid current password");
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Weak password");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token");
        }
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token"));
        if (user.getVerificationExpiresAt() != null && user.getVerificationExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token expired");
        }
        user.setEmailVerified(Boolean.TRUE);
        user.setEmailVerifiedAt(java.time.LocalDateTime.now());
        user.setVerificationToken(null);
        user.setVerificationExpiresAt(null);
        userRepository.save(user);
    }

    @Transactional
    public void resendVerification(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return;
        if (Boolean.TRUE.equals(user.getEmailVerified())) return;
        String token = user.getVerificationToken();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (token == null || user.getVerificationExpiresAt() == null || user.getVerificationExpiresAt().isBefore(now)) {
            token = java.util.UUID.randomUUID().toString().replace("-", "") + java.util.UUID.randomUUID().toString().replace("-", "");
            user.setVerificationToken(token);
            user.setVerificationExpiresAt(now.plusDays(2));
            userRepository.save(user);
        }
        try {
            String link = verifyBaseUrl + "?token=" + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);
            mailService.sendVerificationEmail(user.getEmail(), link);
        } catch (Exception ignored) {}
    }
}
