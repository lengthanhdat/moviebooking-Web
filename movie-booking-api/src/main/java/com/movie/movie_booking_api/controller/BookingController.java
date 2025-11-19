package com.movie.movie_booking_api.controller;

import com.movie.movie_booking_api.dto.BookingRequest;
import com.movie.movie_booking_api.service.BookingService;
import com.movie.movie_booking_api.repository.BookingRepository;
import com.movie.movie_booking_api.repository.UserRepository;
import com.movie.movie_booking_api.repository.ShowTimeRepository;
import com.movie.movie_booking_api.entity.Booking;
import com.movie.movie_booking_api.entity.User;
import com.movie.movie_booking_api.entity.ShowTime;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ShowTimeRepository showTimeRepository;

    @PostMapping
    public ResponseEntity<?> createBooking(Authentication authentication,
                                           @Valid @RequestBody BookingRequest request) {
        String email = authentication.getName();
        return ResponseEntity.ok(bookingService.createBooking(email, request));
    }

    @GetMapping("/me")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<?> myBookings(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Phiên đăng nhập hết hạn"));

        java.util.List<java.util.Map<String, Object>> result = bookingRepository.findByUserId(user.getId())
                .stream()
                .map(b -> {
                    ShowTime st = showTimeRepository.findById(b.getShowtimeId()).orElse(null);
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", b.getId());
                    m.put("showtimeId", b.getShowtimeId());
                    m.put("seats", new java.util.ArrayList<>(b.getSeats()));
                    m.put("totalPrice", b.getTotalPrice());
                    if (st != null) {
                        m.put("startTime", st.getStartTime().atOffset(java.time.ZoneOffset.UTC));
                        m.put("cinema", st.getCinema());
                        m.put("room", st.getRoom());
                        m.put("movieTitle", st.getMovieTitle());
                        m.put("price", st.getPrice());
                        m.put("movieId", st.getMovieId());
                    }
                    return m;
                })
                .toList();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancel(Authentication authentication, @PathVariable("id") Long id) {
        String email = authentication.getName();
        bookingService.cancelBooking(email, id, 60);
        return ResponseEntity.ok().build();
    }
}