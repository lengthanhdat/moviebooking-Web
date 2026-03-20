package com.movie.movie_booking_api.repository;

import com.movie.movie_booking_api.entity.ShowTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShowTimeRepository extends JpaRepository<ShowTime, Long> {
    List<ShowTime> findByMovieId(Long movieId);
    List<ShowTime> findByMovieTmdbId(Long movieTmdbId);
    List<ShowTime> findByCinemaOrderByStartTimeDesc(String cinema);
    java.util.List<ShowTime> findAllByOrderByStartTimeDesc();

    @Query(value = "SELECT COUNT(*) FROM show_times WHERE DATE(start_time)=CURRENT_DATE AND start_time>NOW()", nativeQuery = true)
    Long countActiveShowtimesToday();

    @Query(value = "SELECT * FROM show_times WHERE cinema = :cinema AND room = :room AND ((start_time <= :end AND DATE_ADD(start_time, INTERVAL duration_minutes MINUTE) >= :start))", nativeQuery = true)
    List<ShowTime> findConflicts(@Param("cinema") String cinema, @Param("room") String room, @Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query(value = "SELECT DISTINCT movie_id FROM show_times WHERE (disabled IS NULL OR disabled = FALSE) AND start_time BETWEEN :start AND :end", nativeQuery = true)
    java.util.List<Long> findDistinctMovieIdsBetween(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query(value = "SELECT DISTINCT movie_id FROM show_times WHERE (disabled IS NULL OR disabled = FALSE) AND start_time > :start", nativeQuery = true)
    java.util.List<Long> findDistinctMovieIdsAfter(@Param("start") java.time.LocalDateTime start);

    @Modifying
    @Query(value = "UPDATE show_times SET status = 'FINISHED' WHERE (disabled IS NULL OR disabled = FALSE) AND status <> 'CANCELLED' AND DATE_ADD(start_time, INTERVAL duration_minutes MINUTE) < NOW() AND status <> 'FINISHED'", nativeQuery = true)
    int markFinished();

    @Modifying
    @Query(value = "UPDATE show_times SET status = 'ACTIVE' WHERE (disabled IS NULL OR disabled = FALSE) AND status <> 'CANCELLED' AND start_time <= NOW() AND DATE_ADD(start_time, INTERVAL duration_minutes MINUTE) >= NOW() AND status <> 'ACTIVE'", nativeQuery = true)
    int markActive();

    @Modifying
    @Query(value = "UPDATE show_times SET status = 'UPCOMING' WHERE (disabled IS NULL OR disabled = FALSE) AND status <> 'CANCELLED' AND start_time > NOW() AND status <> 'UPCOMING'", nativeQuery = true)
    int markUpcoming();
}
