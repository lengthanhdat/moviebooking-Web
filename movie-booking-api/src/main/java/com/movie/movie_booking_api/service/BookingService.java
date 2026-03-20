package com.movie.movie_booking_api.service;

import com.movie.movie_booking_api.dto.BookingRequest;
import com.movie.movie_booking_api.entity.Booking;
import com.movie.movie_booking_api.entity.Payment;
import com.movie.movie_booking_api.entity.Seat;
import com.movie.movie_booking_api.entity.SeatStatus;
import com.movie.movie_booking_api.entity.ShowTime;
import com.movie.movie_booking_api.entity.User;
import com.movie.movie_booking_api.repository.BookingRepository;
import com.movie.movie_booking_api.repository.SeatRepository;
import com.movie.movie_booking_api.repository.PaymentRepository;
import com.movie.movie_booking_api.repository.ShowTimeRepository;
import com.movie.movie_booking_api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final ShowTimeRepository showTimeRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final MailService mailService;

    @Transactional
    public Map<String, Object> createBooking(String email, BookingRequest request) {
        ShowTime showTime = showTimeRepository.findById(request.getShowtimeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Showtime not found"));
        java.time.Duration untilStart = java.time.Duration.between(java.time.LocalDateTime.now(), showTime.getStartTime());
        if (Boolean.TRUE.equals(showTime.getDisabled()) || untilStart.toMinutes() < 10) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Showtime disabled");
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        List<Seat> seats = seatRepository.lockByShowtimeIdAndCodeIn(request.getShowtimeId(), request.getSeats());
        if (seats.size() != request.getSeats().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid seat codes");
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String holdId = request.getHoldId();
        if (holdId != null) {
            holdId = holdId.trim();
            if (holdId.isEmpty() || "null".equalsIgnoreCase(holdId) || "undefined".equalsIgnoreCase(holdId)) holdId = null;
        }

        boolean anyBooked = seats.stream().anyMatch(s -> s.getStatus() == SeatStatus.BOOKED);
        if (anyBooked) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SEAT_ALREADY_BOOKED");
        }

        boolean anyHeldByOthers = false;
        for (Seat s : seats) {
            if (s.getStatus() != SeatStatus.HELD) continue;

            if (s.getHeldUntil() != null && s.getHeldUntil().isAfter(now)) {
                if (holdId == null || s.getHeldBy() == null || !holdId.equals(s.getHeldBy())) {
                    anyHeldByOthers = true;
                    break;
                }
            } else {
                s.setStatus(SeatStatus.AVAILABLE);
                s.setHeldBy(null);
                s.setHeldUntil(null);
            }
        }
        if (anyHeldByOthers) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SEAT_ALREADY_HELD");
        }

        seats.forEach(s -> {
            s.setStatus(SeatStatus.BOOKED);
            s.setHeldBy(null);
            s.setHeldUntil(null);
        });
        seatRepository.saveAll(seats);

        Integer normalPriceRaw = showTime.getPrice();
        if (normalPriceRaw == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SHOWTIME_PRICE_MISSING");
        }
        int normalPrice = normalPriceRaw;
        int vipPrice = showTime.getPriceVip() == null ? (normalPrice + 20000) : showTime.getPriceVip();
        java.util.List<java.util.Map<String, Object>> items = seats.stream()
                .map(s -> {
                    int unit = (s.getPrice() != null && s.getPrice() > 0) ? s.getPrice() : (s.getType() == com.movie.movie_booking_api.entity.SeatType.VIP ? vipPrice : normalPrice);
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("seatId", s.getId());
                    m.put("code", s.getCode());
                    m.put("type", s.getType() == null ? "NORMAL" : s.getType().name());
                    m.put("unitPrice", unit);
                    return m;
                })
                .toList();
        int totalPrice = items.stream().mapToInt(i -> ((Number) i.get("unitPrice")).intValue()).sum();

        Booking booking = Booking.builder()
                .showtimeId(showTime.getId())
                .userId(user.getId())
                .seats(request.getSeats())
                .totalPrice(totalPrice)
                .createdAt(LocalDateTime.now())
                .code(java.util.UUID.randomUUID().toString().substring(0,8).toUpperCase())
                .build();

        booking = bookingRepository.save(booking);

        Payment payment = Payment.builder()
                .userId(user.getId())
                .showtimeId(showTime.getId())
                .bookingId(booking.getId())
                .amount(totalPrice)
                .currency(showTime.getCurrency() == null ? "VND" : showTime.getCurrency())
                .method("Card")
                .status("SUCCESS")
                .movieTitle(showTime.getMovieTitle())
                .createdAt(LocalDateTime.now())
                .build();
        paymentRepository.save(payment);
        try { mailService.sendTicketEmail(user.getEmail(), booking, showTime); } catch (Exception ignore) {}

        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("id", booking.getId());
        resp.put("code", booking.getCode());
        resp.put("movieId", showTime.getMovieId());
        resp.put("movieTitle", showTime.getMovieTitle());
        resp.put("showtimeId", booking.getShowtimeId());
        resp.put("cinema", showTime.getCinema());
        resp.put("room", showTime.getRoom());
        resp.put("seats", new java.util.ArrayList<>(booking.getSeats()));
        resp.put("startTime", showTime.getStartTime().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime());
        resp.put("totalPrice", booking.getTotalPrice());
        resp.put("currency", showTime.getCurrency() == null ? "VND" : showTime.getCurrency());
        return resp;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> quoteBooking(Long showtimeId, java.util.List<String> seatCodes) {
        ShowTime showTime = showTimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Showtime not found"));

        List<Seat> seats = seatRepository.findByShowtimeIdAndCodeIn(showtimeId, seatCodes);
        if (seats.size() != seatCodes.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid seat codes");
        }

        Integer normalPriceRaw = showTime.getPrice();
        if (normalPriceRaw == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SHOWTIME_PRICE_MISSING");
        }
        int normalPrice = normalPriceRaw;
        int vipPrice = showTime.getPriceVip() == null ? (normalPrice + 20000) : showTime.getPriceVip();
        java.util.List<java.util.Map<String, Object>> items = seats.stream()
                .map(s -> {
                    int unit = (s.getPrice() != null && s.getPrice() > 0) ? s.getPrice() : (s.getType() == com.movie.movie_booking_api.entity.SeatType.VIP ? vipPrice : normalPrice);
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("seatId", s.getId());
                    m.put("code", s.getCode());
                    m.put("type", s.getType() == null ? "NORMAL" : s.getType().name());
                    m.put("unitPrice", unit);
                    return m;
                })
                .toList();
        int totalPrice = items.stream().mapToInt(i -> ((Number) i.get("unitPrice")).intValue()).sum();

        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("showtimeId", showTime.getId());
        resp.put("items", items);
        resp.put("totalPrice", totalPrice);
        resp.put("currency", showTime.getCurrency() == null ? "VND" : showTime.getCurrency());
        resp.put("startTime", showTime.getStartTime().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime());
        resp.put("cinema", showTime.getCinema());
        resp.put("room", showTime.getRoom());
        resp.put("serviceFee", 0);
        resp.put("discount", 0);
        return resp;
    }

    @Transactional
    public void cancelBooking(String email, Long bookingId, int cutoffMinutes) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập hết hạn"));
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (!booking.getUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }
        ShowTime st = showTimeRepository.findById(booking.getShowtimeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Showtime not found"));
        java.time.Duration d = java.time.Duration.between(java.time.LocalDateTime.now(), st.getStartTime());
        if (d.toMinutes() < cutoffMinutes) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot cancel after cutoff time");
        }
        List<Seat> seats = seatRepository.findByShowtimeIdAndCodeIn(st.getId(), booking.getSeats());
        seats.forEach(s -> s.setStatus(SeatStatus.AVAILABLE));
        seatRepository.saveAll(seats);
        bookingRepository.deleteById(booking.getId());
    }
}
