package com.movie.movie_booking_api.repository;

import com.movie.movie_booking_api.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    java.util.List<Booking> findByUserId(Long userId);
    Long countByShowtimeId(Long showtimeId);

    @org.springframework.data.jpa.repository.Query(value = "SELECT b.* FROM bookings b JOIN users u ON b.user_id = u.id WHERE LOWER(u.email) = LOWER(:email) ORDER BY b.created_at DESC", nativeQuery = true)
    java.util.List<Booking> findByUserEmailIgnoreCase(@org.springframework.data.repository.query.Param("email") String email);

    @org.springframework.data.jpa.repository.Query(value = "SELECT b.* FROM bookings b JOIN users u ON b.user_id = u.id WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%')) ORDER BY b.created_at DESC", nativeQuery = true)
    java.util.List<Booking> findByUserEmailLikeIgnoreCase(@org.springframework.data.repository.query.Param("email") String email);

    @Query(value = "SELECT COUNT(*) FROM booking_seats bs JOIN bookings b ON bs.booking_id = b.id WHERE DATE(b.created_at) = CURRENT_DATE", nativeQuery = true)
    Long countTicketsSoldToday();

    @Query(value = "SELECT COALESCE(SUM(total_price),0) FROM bookings WHERE DATE(created_at)=CURRENT_DATE", nativeQuery = true)
    Long sumTodayRevenueByBookings();

    @Query(value = "SELECT COALESCE(SUM(total_price),0) FROM bookings WHERE YEAR(created_at)=YEAR(CURRENT_DATE) AND MONTH(created_at)=MONTH(CURRENT_DATE)", nativeQuery = true)
    Long sumCurrentMonthRevenueByBookings();

    @Query(value = "SELECT DATE(created_at) AS d, COALESCE(SUM(total_price),0) AS total FROM bookings WHERE created_at >= DATE_SUB(CURRENT_DATE, INTERVAL :days DAY) GROUP BY DATE(created_at) ORDER BY d", nativeQuery = true)
    java.util.List<Object[]> sumRevenueByDayBookings(@org.springframework.data.repository.query.Param("days") int days);

    @Query(value = "SELECT YEARWEEK(created_at, 1) AS wk, COALESCE(SUM(total_price),0) AS total FROM bookings WHERE created_at >= DATE_SUB(CURRENT_DATE, INTERVAL :weeks WEEK) GROUP BY YEARWEEK(created_at, 1) ORDER BY wk", nativeQuery = true)
    java.util.List<Object[]> sumRevenueByWeekBookings(@org.springframework.data.repository.query.Param("weeks") int weeks);

    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m-01') AS m, COALESCE(SUM(total_price),0) AS total FROM bookings WHERE created_at >= DATE_SUB(CURRENT_DATE, INTERVAL :months MONTH) GROUP BY DATE_FORMAT(created_at, '%Y-%m-01') ORDER BY m", nativeQuery = true)
    java.util.List<Object[]> sumRevenueByMonthBookings(@org.springframework.data.repository.query.Param("months") int months);
}
