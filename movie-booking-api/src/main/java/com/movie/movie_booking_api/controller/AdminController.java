package com.movie.movie_booking_api.controller;

import com.movie.movie_booking_api.repository.PaymentRepository;
import com.movie.movie_booking_api.repository.BookingRepository;
import com.movie.movie_booking_api.repository.ShowTimeRepository;
import com.movie.movie_booking_api.entity.Movie;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final BookingRepository bookingRepository;
    private final ShowTimeRepository showTimeRepository;
    private final com.movie.movie_booking_api.repository.MovieRepository movieRepository;

    @GetMapping("/dashboard/overview")
    public ResponseEntity<?> overview() {
        Long todayRevenue = bookingRepository.sumTodayRevenueByBookings();
        Long monthRevenue = bookingRepository.sumCurrentMonthRevenueByBookings();
        Long ticketsSoldToday = bookingRepository.countTicketsSoldToday();
        Long activeShowtimesToday = showTimeRepository.countActiveShowtimesToday();
        List<Object[]> series = bookingRepository.sumRevenueByDayBookings(30);
        List<Map<String, Object>> revenueByDay = series.stream().map(row -> {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("date", ((java.sql.Date) row[0]).toLocalDate());
            m.put("amount", ((Number) row[1]).longValue());
            return m;
        }).toList();
        Map<String, Object> resp = Map.of(
                "todayRevenue", todayRevenue == null ? 0 : todayRevenue,
                "monthRevenue", monthRevenue == null ? 0 : monthRevenue,
                "ticketsSoldToday", ticketsSoldToday == null ? 0 : ticketsSoldToday,
                "activeShowtimesToday", activeShowtimesToday == null ? 0 : activeShowtimesToday,
                "revenueByDay", revenueByDay
        );
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/revenue/daily")
    public ResponseEntity<?> revenueDaily(@RequestParam(name = "days", defaultValue = "30") int days,
                                          @RequestParam(name = "mode", defaultValue = "daily") String mode) {
        List<Object[]> series;
        if ("weekly".equalsIgnoreCase(mode)) {
            series = bookingRepository.sumRevenueByWeekBookings(Math.max(1, days/7));
            List<Map<String, Object>> result = series.stream().map(row -> {
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("date", String.valueOf(((Number) row[0]).intValue()));
                m.put("amount", ((Number) row[1]).longValue());
                return m;
            }).toList();
            return ResponseEntity.ok(result);
        } else if ("monthly".equalsIgnoreCase(mode)) {
            series = bookingRepository.sumRevenueByMonthBookings(Math.max(1, days/30));
            List<Map<String, Object>> result = series.stream().map(row -> {
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("date", String.valueOf(row[0]));
                m.put("amount", ((Number) row[1]).longValue());
                return m;
            }).toList();
            return ResponseEntity.ok(result);
        } else {
            series = bookingRepository.sumRevenueByDayBookings(days);
            List<Map<String, Object>> result = series.stream().map(row -> {
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("date", ((java.sql.Date) row[0]).toLocalDate());
                m.put("amount", ((Number) row[1]).longValue());
                return m;
            }).toList();
            return ResponseEntity.ok(result);
        }
    }

    @GetMapping("/top-movies")
    public ResponseEntity<?> topMovies(@RequestParam(name = "range", defaultValue = "today") String range,
                                       @RequestParam(name = "limit", defaultValue = "5") int limit) {
        java.util.List<Object[]> rows = "month".equalsIgnoreCase(range) ? movieRepository.topMoviesMonthByRevenue() : movieRepository.topMoviesTodayByRevenue();
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        int count = 0;
        for (Object[] row : rows) {
            if (count++ >= limit) break;
            Long movieId = ((Number) row[0]).longValue();
            Long amount = ((Number) row[1]).longValue();
            String title = movieRepository.findById(movieId).map(Movie::getTitle).orElse("#" + movieId);
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("movieId", movieId);
            m.put("title", title);
            m.put("amount", amount);
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }
}