package com.movie.movie_booking_api.controller;

import com.movie.movie_booking_api.entity.Booking;
import com.movie.movie_booking_api.entity.Seat;
import com.movie.movie_booking_api.entity.SeatStatus;
import com.movie.movie_booking_api.entity.SeatType;
import com.movie.movie_booking_api.entity.ShowTime;
import com.movie.movie_booking_api.dto.BookingRequest;
import com.movie.movie_booking_api.repository.BookingRepository;
import com.movie.movie_booking_api.repository.SeatRepository;
import com.movie.movie_booking_api.repository.ShowTimeRepository;
import com.movie.movie_booking_api.repository.UserRepository;
import com.movie.movie_booking_api.service.BookingService;
import com.movie.movie_booking_api.util.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Slf4j
public class BookingController {

    private final SeatRepository seatRepository;
    private final ShowTimeRepository showTimeRepository;
    private final BookingRepository bookingRepository;
    private final RateLimiter rateLimiter;
    private final BookingService bookingService;
    private final UserRepository userRepository;

    @PostMapping("/quote")
    public ResponseEntity<?> quote(@RequestBody Map<String, Object> body) {
        Long showtimeId = readLong(body.get("showtimeId"));
        List<String> codes = readStringList(body.get("seats"));
        if (showtimeId == null || codes.isEmpty()) {
            return ResponseEntity.badRequest().body("showtimeId and seats required");
        }
        ShowTime st = showTimeRepository.findById(showtimeId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        if (Boolean.TRUE.equals(st.getDisabled()) || "CANCELLED".equalsIgnoreCase(st.getStatus()) || "FINISHED".equalsIgnoreCase(st.getStatus())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Showtime disabled");
        }
        if (st.getStartTime() != null) {
            Integer dur = st.getDurationMinutes();
            int minutes = (dur == null || dur <= 0) ? 0 : dur;
            java.time.LocalDateTime end = st.getStartTime().plusMinutes(minutes);
            if (end.isBefore(java.time.LocalDateTime.now())) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Showtime finished");
            }
        }
        List<Seat> seats = seatRepository.findByShowtimeIdAndCodeIn(showtimeId, codes);
        int total = 0;
        List<Map<String, Object>> items = new ArrayList<>();
        for (Seat s : seats) {
            int unit = s.getPrice() == null ? (s.getType() == SeatType.VIP ? (st.getPriceVip() == null ? st.getPrice() + 20000 : st.getPriceVip()) : st.getPrice()) : s.getPrice();
            total += unit;
            Map<String, Object> item = new HashMap<>();
            item.put("code", s.getCode());
            item.put("unitPrice", unit);
            item.put("type", s.getType() == null ? null : s.getType().name());
            items.add(item);
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("totalPrice", total);
        resp.put("currency", st.getCurrency() == null ? "VND" : st.getCurrency());
        resp.put("serviceFee", 0);
        resp.put("discount", 0);
        resp.put("items", items);
        return ResponseEntity.ok(resp);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body,
                                    org.springframework.web.context.request.WebRequest request,
                                    Authentication authentication) {
        String email = authentication == null ? null : authentication.getName();
        String ip = null;
        try {
            if (request instanceof ServletWebRequest swr) {
                jakarta.servlet.http.HttpServletRequest r = swr.getRequest();
                ip = r.getHeader("X-Forwarded-For");
                if (ip != null && ip.contains(",")) ip = ip.split(",")[0].trim();
                if (ip == null || ip.isBlank()) ip = r.getRemoteAddr();
            }
        } catch (Exception ignore) {}

        String key = "bookings:" + (email == null || email.isBlank() ? (ip == null || ip.isBlank() ? "anon" : ip) : email);
        if (!rateLimiter.allow(key, 10, 60_000)) {
            return ResponseEntity.status(429).body("RATE_LIMIT");
        }

        Long showtimeId = readLong(body.get("showtimeId"));
        List<String> codes = readStringList(body.get("seats"));
        if (showtimeId == null || codes.isEmpty()) {
            return ResponseEntity.badRequest().body("showtimeId and seats required");
        }
        BookingRequest req = new BookingRequest();
        req.setShowtimeId(showtimeId);
        req.setSeats(codes);
        req.setHoldId(readHoldId(body.get("holdId")));
        if (email == null || email.isBlank()) {
            return ResponseEntity.status(401).body("UNAUTHORIZED");
        }
        // Pre-check conflict to chuẩn hóa mã lỗi và danh sách ghế
        java.util.List<Seat> seats = seatRepository.findByShowtimeIdAndCodeIn(showtimeId, codes);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String holdId = req.getHoldId();
        java.util.List<String> booked = new java.util.ArrayList<>();
        java.util.List<String> held = new java.util.ArrayList<>();
        for (Seat s : seats) {
            if (s.getStatus() == SeatStatus.BOOKED) booked.add(s.getCode());
            if (s.getStatus() == SeatStatus.HELD && s.getHeldUntil() != null && s.getHeldUntil().isAfter(now)) {
                if (holdId == null || s.getHeldBy() == null || !holdId.equals(s.getHeldBy())) {
                    held.add(s.getCode());
                }
            }
        }
        if (!booked.isEmpty()) {
            return ResponseEntity.status(409).body(java.util.Map.of("error", "SEAT_ALREADY_BOOKED", "seats", booked));
        }
        if (!held.isEmpty()) {
            return ResponseEntity.status(409).body(java.util.Map.of("error", "SEAT_ALREADY_HELD", "seats", held));
        }

        try {
            java.util.Map<String, Object> resp = bookingService.createBooking(email, req);
            Long uid = null;
            try { uid = userRepository.findByEmailIgnoreCase(email).map(com.movie.movie_booking_api.entity.User::getId).orElse(null); } catch(Exception ignore){}
            log.info("Booking created by email={}, userId={}, bookingId={}, showtimeId={}, seats={}", email, uid, resp.get("id"), resp.get("showtimeId"), codes);
            return ResponseEntity.ok(resp);
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            if (ex.getStatusCode() == org.springframework.http.HttpStatus.CONFLICT) {
                // Re-check trạng thái để trả JSON chuẩn cho App
                seats = seatRepository.findByShowtimeIdAndCodeIn(showtimeId, codes);
                now = java.time.LocalDateTime.now();
                booked.clear(); held.clear();
                for (Seat s : seats) {
                    if (s.getStatus() == SeatStatus.BOOKED) booked.add(s.getCode());
                    if (s.getStatus() == SeatStatus.HELD && s.getHeldUntil() != null && s.getHeldUntil().isAfter(now)) {
                        if (holdId == null || s.getHeldBy() == null || !holdId.equals(s.getHeldBy())) {
                            held.add(s.getCode());
                        }
                    }
                }
                if (!booked.isEmpty()) {
                    return ResponseEntity.status(409).body(java.util.Map.of("error", "SEAT_ALREADY_BOOKED", "seats", booked));
                }
                if (!held.isEmpty()) {
                    return ResponseEntity.status(409).body(java.util.Map.of("error", "SEAT_ALREADY_HELD", "seats", held));
                }
                return ResponseEntity.status(409).body(java.util.Map.of("error", "CONFLICT"));
            }
            throw ex;
        }
    }

    private static Long readLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s) || "undefined".equalsIgnoreCase(s)) return null;
        try { return Long.parseLong(s); } catch (Exception ignore) { return null; }
    }

    private static String readHoldId(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s) || "undefined".equalsIgnoreCase(s)) return null;
        return s;
    }

    private static List<String> readStringList(Object v) {
        if (v == null) return List.of();
        if (v instanceof List<?> list) {
            return list.stream()
                    .map(x -> x == null ? "" : String.valueOf(x).trim())
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s) || "undefined".equalsIgnoreCase(s)) return List.of();
        return java.util.Arrays.stream(s.split("[,\\s]+"))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .distinct()
                .toList();
    }

    @GetMapping({"/my","/me"})
    @Transactional(readOnly = true)
    public ResponseEntity<?> myBookings(Authentication authentication) {
        String email = authentication == null ? null : authentication.getName();
        if (email == null || email.isBlank()) {
            return ResponseEntity.status(401).body("UNAUTHORIZED");
        }
        Long uid = userRepository.findByEmailIgnoreCase(email).map(com.movie.movie_booking_api.entity.User::getId)
                .orElse(null);
        if (uid == null) return ResponseEntity.ok(java.util.List.of());
        List<Booking> bookings = bookingRepository.findByUserId(uid);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Booking b : bookings) {
            ShowTime st = showTimeRepository.findById(b.getShowtimeId()).orElse(null);
            Map<String, Object> m = new HashMap<>();
            m.put("id", b.getId());
            m.put("code", b.getCode());
            m.put("showtimeId", b.getShowtimeId());
            m.put("seats", new java.util.ArrayList<>(b.getSeats()));
            m.put("totalPrice", b.getTotalPrice());
            m.put("createdAt", b.getCreatedAt());
            if (st != null) {
                m.put("movieId", st.getMovieId());
                m.put("tmdbId", st.getMovieTmdbId());
                m.put("movieTitle", st.getMovieTitle());
                m.put("cinema", st.getCinema());
                m.put("room", st.getRoom());
                m.put("startTime", st.getStartTime().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime());
                m.put("currency", st.getCurrency() == null ? "VND" : st.getCurrency());
                try {
                    java.util.List<Seat> bs = seatRepository.findByShowtimeIdAndCodeIn(st.getId(), b.getSeats());
                    if (!bs.isEmpty()) {
                        Seat s0 = bs.get(0);
                        int unit = s0.getPrice() != null ? s0.getPrice() : (s0.getType() == SeatType.VIP ? (st.getPriceVip() == null ? st.getPrice() + 20000 : st.getPriceVip()) : st.getPrice());
                        m.put("price", unit);
                    }
                } catch (Exception ignore) {}
            }
            result.add(m);
        }
        result.sort(java.util.Comparator.comparing((Map<String,Object> x) -> (java.time.LocalDateTime) x.get("createdAt")).reversed());
        log.info("Bookings list for email={}, userId={}, count={}", email, uid, result.size());
        return ResponseEntity.ok().header("Cache-Control","no-store").body(result);
    }
}
