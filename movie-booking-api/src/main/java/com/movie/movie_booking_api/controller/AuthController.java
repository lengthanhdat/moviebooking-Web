// src/main/java/com/movie/movie_booking_api/controller/AuthController.java
package com.movie.movie_booking_api.controller;

import com.movie.movie_booking_api.dto.AuthResponse;
import com.movie.movie_booking_api.dto.LoginRequest;
import com.movie.movie_booking_api.dto.RegisterRequest;
import com.movie.movie_booking_api.dto.ChangePasswordRequest;
import com.movie.movie_booking_api.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot")
    public ResponseEntity<?> forgot(@RequestBody java.util.Map<String, String> req) {
        String email = req.getOrDefault("email", "");
        email = email == null ? "" : email.trim();
        if (email.isEmpty()) {
            return ResponseEntity.badRequest().body("Invalid email");
        }
        return ResponseEntity.status(202).body("OK");
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(Authentication authentication,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(authentication.getName(), request);
        return ResponseEntity.ok().build();
    }
}
