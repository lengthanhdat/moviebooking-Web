package com.movie.movie_booking_api.controller;

import com.movie.movie_booking_api.entity.Payment;
import com.movie.movie_booking_api.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentRepository paymentRepository;

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(value = "status", required = false) String status) {
        List<Payment> all = paymentRepository.findAll();
        List<Map<String, Object>> result = all.stream()
                .filter(p -> status == null || p.getStatus().equalsIgnoreCase(status))
                .map(p -> {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", p.getId());
                    m.put("userId", p.getUserId());
                    m.put("showtimeId", p.getShowtimeId());
                    m.put("bookingId", p.getBookingId());
                    m.put("amount", p.getAmount());
                    m.put("currency", p.getCurrency());
                    m.put("method", p.getMethod());
                    m.put("status", p.getStatus());
                    m.put("orderId", p.getOrderId());
                    m.put("movieTitle", p.getMovieTitle());
                    m.put("createdAt", p.getCreatedAt().atOffset(java.time.ZoneOffset.UTC));
                    return m;
                }).toList();
        return ResponseEntity.ok(result);
    }
}