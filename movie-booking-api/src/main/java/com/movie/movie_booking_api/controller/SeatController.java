package com.movie.movie_booking_api.controller;

import com.movie.movie_booking_api.repository.SeatRepository;
import com.movie.movie_booking_api.entity.Seat;
import com.movie.movie_booking_api.entity.SeatStatus;
import com.movie.movie_booking_api.entity.SeatType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatRepository seatRepository;

    @GetMapping
    public ResponseEntity<?> getSeats(@RequestParam("showtimeId") Long showtimeId) {
        List<Seat> seats = seatRepository.findByShowtimeId(showtimeId);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        boolean changed = false;
        for (Seat s : seats) {
            if (s.getStatus() == SeatStatus.HELD && s.getHeldUntil() != null && s.getHeldUntil().isBefore(now)) {
                s.setStatus(SeatStatus.AVAILABLE);
                s.setHeldUntil(null);
                changed = true;
            }
        }
        if (changed) {
            seatRepository.saveAll(seats);
        }
        List<Map<String, Object>> result = seats.stream()
                .map(seat -> {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", seat.getId());
                    m.put("code", seat.getCode());
                    m.put("row", seat.getRow());
                    m.put("number", seat.getNumber());
                    m.put("type", seat.getType() == null ? null : seat.getType().name());
                    m.put("price", seat.getPrice());
                    m.put("status", seat.getStatus().name());
                    m.put("rowIndex", seat.getRowIndex());
                    m.put("colIndex", seat.getColIndex());
                    m.put("section", seat.getSection());
                    m.put("heldUntil", seat.getHeldUntil() == null ? null : seat.getHeldUntil().atOffset(java.time.ZoneOffset.UTC));
                    return m;
                })
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/seed")
    public ResponseEntity<?> seedSeats(@RequestBody Map<String, Object> body) {
        Object stIdObj = body.get("showtimeId");
        if (stIdObj == null) {
            return ResponseEntity.badRequest().body("Missing showtimeId");
        }
        Long showtimeId = ((Number) stIdObj).longValue();

        @SuppressWarnings("unchecked")
        List<String> rows = (List<String>) body.getOrDefault("rows", java.util.List.of("A", "B", "C", "D"));
        int cols = ((Number) body.getOrDefault("cols", 8)).intValue();

        Set<String> existing = seatRepository.findByShowtimeId(showtimeId)
                .stream().map(Seat::getCode).collect(Collectors.toSet());

        List<Seat> toCreate = new java.util.ArrayList<>();
        for (int rIdx = 0; rIdx < rows.size(); rIdx++) {
            String row = rows.get(rIdx);
            for (int cIdx = 0; cIdx < cols; cIdx++) {
                int number = cIdx + 1;
                String code = row + number;
                if (!existing.contains(code)) {
                    SeatType type = ("C".equals(row) || "D".equals(row)) ? SeatType.VIP : SeatType.NORMAL;
                    toCreate.add(Seat.builder()
                            .showtimeId(showtimeId)
                            .code(code)
                            .status(SeatStatus.AVAILABLE)
                            .type(type)
                            .row(row)
                            .number(number)
                            .rowIndex(rIdx)
                            .colIndex(cIdx)
                            .build());
                }
            }
        }
        if (!toCreate.isEmpty()) {
            seatRepository.saveAll(toCreate);
        }

        List<Map<String, Object>> result = seatRepository.findByShowtimeId(showtimeId)
                .stream()
                .map(seat -> {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", seat.getId());
                    m.put("code", seat.getCode());
                    m.put("row", seat.getRow());
                    m.put("number", seat.getNumber());
                    m.put("type", seat.getType() == null ? null : seat.getType().name());
                    m.put("price", seat.getPrice());
                    m.put("status", seat.getStatus().name());
                    m.put("rowIndex", seat.getRowIndex());
                    m.put("colIndex", seat.getColIndex());
                    m.put("section", seat.getSection());
                    m.put("heldUntil", seat.getHeldUntil() == null ? null : seat.getHeldUntil().atOffset(java.time.ZoneOffset.UTC));
                    return m;
                })
                .toList();
        return ResponseEntity.status(201).body(result);
    }
}