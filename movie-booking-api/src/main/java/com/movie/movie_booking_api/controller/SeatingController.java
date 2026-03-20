package com.movie.movie_booking_api.controller;

import com.movie.movie_booking_api.entity.Seat;
import com.movie.movie_booking_api.entity.SeatStatus;
import com.movie.movie_booking_api.entity.ShowTime;
import com.movie.movie_booking_api.realtime.SeatRealtimeService;
import com.movie.movie_booking_api.repository.SeatRepository;
import com.movie.movie_booking_api.repository.ShowTimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
    private final SeatRealtimeService seatRealtimeService;
    private final ShowTimeRepository showTimeRepository;

    @Transactional
    @PostMapping("/hold")
    public ResponseEntity<?> hold(@RequestBody Map<String, Object> body) {
        Object showtimeRaw = body.get("showtimeId");
        if (!(showtimeRaw instanceof Number)) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SHOWTIME_ID"));
        }
        Long showtimeId = ((Number) showtimeRaw).longValue();

        ShowTime st = showTimeRepository.findById(showtimeId).orElse(null);
        if (st != null) {
            if (Boolean.TRUE.equals(st.getDisabled()) || "CANCELLED".equalsIgnoreCase(st.getStatus()) || "FINISHED".equalsIgnoreCase(st.getStatus())) {
                return ResponseEntity.status(409).body(Map.of("error", "SHOWTIME_DISABLED"));
            }
            if (st.getStartTime() != null) {
                Integer dur = st.getDurationMinutes();
                int minutes = (dur == null || dur <= 0) ? 0 : dur;
                java.time.LocalDateTime end = st.getStartTime().plusMinutes(minutes);
                if (end.isBefore(LocalDateTime.now())) {
                    return ResponseEntity.status(409).body(Map.of("error", "SHOWTIME_FINISHED"));
                }
            }
            if (st.getStartTime() != null) {
                java.time.Duration untilStart = java.time.Duration.between(LocalDateTime.now(), st.getStartTime());
                if (untilStart.toMinutes() < 10) {
                    return ResponseEntity.status(409).body(Map.of("error", "SHOWTIME_TOO_SOON"));
                }
            }
        }

        Object seatsRaw = body.get("seats");
        if (!(seatsRaw instanceof List<?> rawList) || rawList.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SEAT_CODES"));
        }
        List<String> codes = rawList.stream().map(String::valueOf).toList();
        int ttlSeconds = ((Number) body.getOrDefault("ttlSeconds", 120)).intValue();
        List<Seat> seats = seatRepository.lockByShowtimeIdAndCodeIn(showtimeId, codes);
        if (seats.size() != codes.size()) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SEAT_CODES"));
        }
        LocalDateTime now = LocalDateTime.now();
        for (Seat s : seats) {
            if (s.getStatus() == SeatStatus.BOOKED) {
                return ResponseEntity.status(409).body(Map.of("error", "SEAT_ALREADY_BOOKED"));
            }
            if (s.getStatus() == SeatStatus.HELD) {
                if (s.getHeldUntil() != null && s.getHeldUntil().isAfter(now)) {
                    return ResponseEntity.status(409).body(Map.of("error", "SEAT_ALREADY_HELD"));
                }
                s.setStatus(SeatStatus.AVAILABLE);
                s.setHeldUntil(null);
                s.setHeldBy(null);
            }
        }
        LocalDateTime expires = now.plusSeconds(ttlSeconds);
        String holdId = UUID.randomUUID().toString();
        seats.forEach(s -> {
            s.setStatus(SeatStatus.HELD);
            s.setHeldUntil(expires);
            s.setHeldBy(holdId);
        });
        seatRepository.saveAll(seats);
        java.time.OffsetDateTime expiresAt = expires.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime();
        seatRealtimeService.broadcastHeld(showtimeId, codes, expiresAt);
        return ResponseEntity.ok(Map.of("holdId", holdId, "expiresAt", expiresAt));
    }

    @Transactional
    @PostMapping("/release")
    public ResponseEntity<?> release(@RequestBody Map<String, Object> body) {
        Object showtimeRaw = body.get("showtimeId");
        if (!(showtimeRaw instanceof Number)) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SHOWTIME_ID"));
        }
        Long showtimeId = ((Number) showtimeRaw).longValue();

        String holdId = body.get("holdId") == null ? null : String.valueOf(body.get("holdId")).trim();
        if (holdId == null || holdId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_HOLD_ID"));
        }

        List<Seat> seats = seatRepository.lockByShowtimeIdAndHoldId(showtimeId, holdId);
        if (seats.isEmpty()) {
            return ResponseEntity.ok(Map.of("released", 0));
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        List<String> codes = new java.util.ArrayList<>();
        for (Seat s : seats) {
            if (s.getStatus() != SeatStatus.HELD) continue;
            if (s.getHeldUntil() != null && s.getHeldUntil().isBefore(now)) {
                s.setStatus(SeatStatus.AVAILABLE);
                s.setHeldUntil(null);
                s.setHeldBy(null);
                codes.add(s.getCode());
                continue;
            }
            s.setStatus(SeatStatus.AVAILABLE);
            s.setHeldUntil(null);
            s.setHeldBy(null);
            codes.add(s.getCode());
        }
        seatRepository.saveAll(seats);
        if (!codes.isEmpty()) {
            seatRealtimeService.broadcastAvailable(showtimeId, codes);
        }
        return ResponseEntity.ok(Map.of("released", codes.size()));
    }
}
