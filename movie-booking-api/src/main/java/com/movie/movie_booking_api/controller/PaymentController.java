package com.movie.movie_booking_api.controller;

import com.movie.movie_booking_api.repository.PaymentRepository;
import com.movie.movie_booking_api.entity.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentRepository paymentRepository;
    private final com.movie.movie_booking_api.repository.UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<?> myPayments(Authentication authentication) {
        String email = authentication == null ? null : authentication.getName();
        if (email == null) return ResponseEntity.status(401).build();
        Long userId = userRepository.findByEmail(email).map(u -> u.getId()).orElse(null);
        if (userId == null) return ResponseEntity.ok(List.of());
        List<Payment> list = paymentRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<Map<String, Object>> result = list.stream().map(p -> {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", p.getId());
            m.put("bookingId", p.getBookingId());
            m.put("showtimeId", p.getShowtimeId());
            m.put("amount", p.getAmount());
            m.put("currency", p.getCurrency());
            m.put("createdAt", p.getCreatedAt().atOffset(java.time.ZoneOffset.UTC));
            m.put("movieTitle", p.getMovieTitle());
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }
}