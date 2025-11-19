package com.movie.movie_booking_api.repository;

import com.movie.movie_booking_api.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByShowtimeId(Long showtimeId);
    List<Seat> findByShowtimeIdAndCodeIn(Long showtimeId, List<String> codes);
}