package com.movie.movie_booking_api.controller;

import com.movie.movie_booking_api.entity.Booking;
import com.movie.movie_booking_api.entity.ShowTime;
import com.movie.movie_booking_api.entity.User;
import com.movie.movie_booking_api.repository.BookingRepository;
import com.movie.movie_booking_api.repository.ShowTimeRepository;
import com.movie.movie_booking_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/bookings")
@RequiredArgsConstructor
public class AdminBookingController {

    private final BookingRepository bookingRepository;
    private final ShowTimeRepository showTimeRepository;
    private final UserRepository userRepository;
    private final com.movie.movie_booking_api.service.BookingService bookingService;

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(value = "email", required = false) String email) {
        try {
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            List<Booking> bookings;
            if (email != null && !email.isBlank()) {
                String q = email.trim();
                if (q.contains("@")) {
                    bookings = bookingRepository.findByUserEmailIgnoreCase(q);
                } else {
                    bookings = bookingRepository.findByUserEmailLikeIgnoreCase(q);
                }
            } else {
                bookings = bookingRepository.findAll();
            }
            List<Map<String, Object>> result = bookings.stream().map(b -> {
                ShowTime st = showTimeRepository.findById(b.getShowtimeId()).orElse(null);
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("id", b.getId());
                m.put("code", b.getCode());
                m.put("userId", b.getUserId());
                String userEmail = userRepository.findById(b.getUserId()).map(User::getEmail).orElse(null);
                m.put("email", userEmail);
                m.put("showtimeId", b.getShowtimeId());
                m.put("seats", new java.util.ArrayList<>(b.getSeats()==null? java.util.List.of() : b.getSeats()));
                m.put("totalPrice", b.getTotalPrice());
                m.put("createdAt", b.getCreatedAt()==null? null : fmt.format(b.getCreatedAt()));
                if (st != null) {
                    m.put("movieTitle", st.getMovieTitle());
                    m.put("cinema", st.getCinema());
                    m.put("room", st.getRoom());
                    m.put("startTime", st.getStartTime()==null? null : fmt.format(st.getStartTime()));
                }
                return m;
            }).toList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(java.util.List.of());
        }
    }

    @PostMapping("/counter")
    public ResponseEntity<?> createCounter(@RequestBody java.util.Map<String, Object> body) {
        String email = String.valueOf(body.getOrDefault("email", "")).trim();
        Long showtimeId = body.get("showtimeId") == null ? null : ((Number) body.get("showtimeId")).longValue();
        @SuppressWarnings("unchecked")
        java.util.List<String> seats = (java.util.List<String>) body.getOrDefault("seats", java.util.List.of());
        if (email.isEmpty() || showtimeId == null || seats.isEmpty()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "email, showtimeId, seats required"));
        }

        // Ensure user exists, auto-create if missing (counter booking)
        com.movie.movie_booking_api.entity.User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            String displayName = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
            String encoded = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("counter123");
            user = com.movie.movie_booking_api.entity.User.builder()
                    .name(displayName)
                    .email(email)
                    .password(encoded)
                    .fullName(displayName)
                    .legacyName(displayName)
                    .build();
            try { user = userRepository.saveAndFlush(user); } catch (Exception e) {
                // If race condition or duplicate, re-fetch
                user = userRepository.findByEmailIgnoreCase(email).orElse(null);
            }
        }

        com.movie.movie_booking_api.dto.BookingRequest req = new com.movie.movie_booking_api.dto.BookingRequest();
        req.setShowtimeId(showtimeId);
        req.setSeats(seats);
        java.util.Map<String, Object> result = bookingService.createBooking(email, req);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancel(@PathVariable("id") Long id) {
        bookingRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }
}
