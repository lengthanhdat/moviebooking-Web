package com.movie.movie_booking_api.repository;

import com.movie.movie_booking_api.entity.Movie;
import com.movie.movie_booking_api.entity.MovieStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MovieRepository extends JpaRepository<Movie, Long> {
    List<Movie> findByTitleContainingIgnoreCase(String title);
    List<Movie> findByGenreContainingIgnoreCase(String genre);
    List<Movie> findByStatus(MovieStatus status);
    java.util.Optional<Movie> findByTmdbId(Long tmdbId);
    boolean existsByTmdbIdIsNotNull();

    @Query(value = "SELECT m.id, COALESCE(SUM(b.total_price),0) AS total FROM bookings b JOIN show_times s ON b.showtime_id = s.id JOIN movies m ON s.movie_id = m.id WHERE DATE(b.created_at)=CURRENT_DATE GROUP BY m.id ORDER BY total DESC LIMIT 10", nativeQuery = true)
    List<Object[]> topMoviesTodayByRevenue();

    @Query(value = "SELECT m.id, COALESCE(SUM(b.total_price),0) AS total FROM bookings b JOIN show_times s ON b.showtime_id = s.id JOIN movies m ON s.movie_id = m.id WHERE YEAR(b.created_at)=YEAR(CURRENT_DATE) AND MONTH(b.created_at)=MONTH(CURRENT_DATE) GROUP BY m.id ORDER BY total DESC LIMIT 10", nativeQuery = true)
    List<Object[]> topMoviesMonthByRevenue();
}