package com.movie.movie_booking_api.service;

import com.movie.movie_booking_api.dto.BookingRequest;
import com.movie.movie_booking_api.entity.Booking;
import com.movie.movie_booking_api.entity.Seat;
import com.movie.movie_booking_api.entity.SeatStatus;
import com.movie.movie_booking_api.entity.ShowTime;
import com.movie.movie_booking_api.entity.User;
import com.movie.movie_booking_api.repository.BookingRepository;
import com.movie.movie_booking_api.repository.SeatRepository;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final ShowTimeRepository showTimeRepository;
    private final UserRepository userRepository;

    @Transactional
    public Map<String, Object> createBooking(String email, BookingRequest request) {
        ShowTime showTime = showTimeRepository.findById(request.getShowtimeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Showtime not found"));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        List<Seat> seats = seatRepository.findByShowtimeIdAndCodeIn(request.getShowtimeId(), request.getSeats());
        if (seats.size() != request.getSeats().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid seat codes");
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        boolean anyBooked = seats.stream().anyMatch(s -> s.getStatus() == SeatStatus.BOOKED);
        boolean anyHeld = seats.stream().anyMatch(s -> s.getStatus() == SeatStatus.HELD && s.getHeldUntil() != null && s.getHeldUntil().isAfter(now));
        if (anyBooked || anyHeld) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ghế đã được đặt");
        }

        seats.forEach(s -> s.setStatus(SeatStatus.BOOKED));
        seatRepository.saveAll(seats);

        int normalPrice = showTime.getPrice();
        int vipPrice = showTime.getPriceVip() == null ? (normalPrice + 20000) : showTime.getPriceVip();
        java.util.List<java.util.Map<String, Object>> items = seats.stream()
                .map(s -> {
                    int unit = s.getPrice() != null ? s.getPrice() : (s.getType() == com.movie.movie_booking_api.entity.SeatType.VIP ? vipPrice : normalPrice);
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
                .build();

        booking = bookingRepository.save(booking);

        return Map.of(
                "id", booking.getId(),
                "showtimeId", booking.getShowtimeId(),
                "items", items,
                "totalPrice", booking.getTotalPrice(),
                "currency", showTime.getCurrency() == null ? "VND" : showTime.getCurrency(),
                "startTime", showTime.getStartTime().atOffset(java.time.ZoneOffset.UTC),
                "cinema", showTime.getCinema(),
                "room", showTime.getRoom()
        );
    }

    @Transactional
    public void cancelBooking(String email, Long bookingId, int cutoffMinutes) {
        User user = userRepository.findByEmail(email)
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