package com.movie.movie_booking_api.controller;

import com.movie.movie_booking_api.entity.Seat;
import com.movie.movie_booking_api.entity.SeatStatus;
import com.movie.movie_booking_api.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/seatings")
@RequiredArgsConstructor
public class SeatingController {

    private final SeatRepository seatRepository;

    @PostMapping("/hold")
    public ResponseEntity<?> hold(@RequestBody Map<String, Object> body) {
        Long showtimeId = ((Number) body.get("showtimeId")).longValue();
        @SuppressWarnings("unchecked")
        List<String> codes = (List<String>) body.get("seats");
        int ttlSeconds = ((Number) body.getOrDefault("ttlSeconds", 120)).intValue();
        List<Seat> seats = seatRepository.findByShowtimeIdAndCodeIn(showtimeId, codes);
        LocalDateTime now = LocalDateTime.now();
        for (Seat s : seats) {
            if (s.getStatus() == SeatStatus.BOOKED) {
                return ResponseEntity.status(409).body(Map.of("error", "SEAT_ALREADY_BOOKED"));
            }
            if (s.getStatus() == SeatStatus.HELD && s.getHeldUntil() != null && s.getHeldUntil().isAfter(now)) {
                return ResponseEntity.status(409).body(Map.of("error", "SEAT_ALREADY_HELD"));
            }
        }
        LocalDateTime expires = now.plusSeconds(ttlSeconds);
        seats.forEach(s -> { s.setStatus(SeatStatus.HELD); s.setHeldUntil(expires); });
        seatRepository.saveAll(seats);
        String holdId = UUID.randomUUID().toString();
        return ResponseEntity.ok(Map.of("holdId", holdId, "expiresAt", expires.atOffset(java.time.ZoneOffset.UTC)));
    }
}