package com.movie.movie_booking_api.repository;

import com.movie.movie_booking_api.entity.Seat;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByShowtimeId(Long showtimeId);
    List<Seat> findByShowtimeIdAndCodeIn(Long showtimeId, List<String> codes);
    void deleteByShowtimeId(Long showtimeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.showtimeId = :showtimeId and s.code in :codes")
    List<Seat> lockByShowtimeIdAndCodeIn(@Param("showtimeId") Long showtimeId, @Param("codes") List<String> codes);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.showtimeId = :showtimeId and s.heldBy = :holdId")
    List<Seat> lockByShowtimeIdAndHoldId(@Param("showtimeId") Long showtimeId, @Param("holdId") String holdId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.status = com.movie.movie_booking_api.entity.SeatStatus.HELD and s.heldUntil is not null and s.heldUntil < :now")
    List<Seat> lockExpiredHeldSeats(@Param("now") LocalDateTime now);
}
