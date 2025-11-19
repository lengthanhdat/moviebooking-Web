package com.movie.movie_booking_api.repository;

import com.movie.movie_booking_api.entity.ShowTime;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShowTimeRepository extends JpaRepository<ShowTime, Long> {
    List<ShowTime> findByMovieId(Long movieId);
}