package com.movie.movie_booking_api.repository;

import com.movie.movie_booking_api.entity.Cinema;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CinemaRepository extends JpaRepository<Cinema, Long> {
}