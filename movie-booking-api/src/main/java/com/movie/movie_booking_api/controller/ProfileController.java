package com.movie.movie_booking_api.controller;

import com.movie.movie_booking_api.dto.UpdateProfileRequest;
import com.movie.movie_booking_api.entity.User;
import com.movie.movie_booking_api.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<?> getProfile(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(Map.of(
                "email", user.getEmail(),
                "name", user.getLegacyName(),
                "fullName", user.getFullName()
        ));
    }

    @PostMapping
    public ResponseEntity<?> updateProfile(Authentication authentication,
                                           @Valid @RequestBody UpdateProfileRequest request) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setLegacyName(request.getName());
        user.setFullName(request.getFullName());
        try {
            userRepository.save(user);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return ResponseEntity.status(409).body("Conflict");
        }
        return ResponseEntity.ok(Map.of(
                "email", user.getEmail(),
                "name", user.getLegacyName(),
                "fullName", user.getFullName()
        ));
    }
}