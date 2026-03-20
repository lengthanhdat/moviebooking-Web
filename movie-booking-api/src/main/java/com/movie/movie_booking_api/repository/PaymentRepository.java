package com.movie.movie_booking_api.repository;

import com.movie.movie_booking_api.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query(value = "SELECT COALESCE(SUM(amount),0) FROM payments WHERE status='SUCCESS' AND DATE(created_at)=CURRENT_DATE", nativeQuery = true)
    Long sumTodayRevenue();

    @Query(value = "SELECT COALESCE(SUM(amount),0) FROM payments WHERE status='SUCCESS' AND YEAR(created_at)=YEAR(CURRENT_DATE) AND MONTH(created_at)=MONTH(CURRENT_DATE)", nativeQuery = true)
    Long sumCurrentMonthRevenue();

    @Query(value = "SELECT DATE(created_at) AS d, COALESCE(SUM(amount),0) AS total FROM payments WHERE status='SUCCESS' AND created_at >= DATE_SUB(CURRENT_DATE, INTERVAL :days DAY) GROUP BY DATE(created_at) ORDER BY d", nativeQuery = true)
    List<Object[]> sumRevenueByDay(@Param("days") int days);

    @Query(value = "SELECT YEARWEEK(created_at, 1) AS wk, COALESCE(SUM(amount),0) AS total FROM payments WHERE status='SUCCESS' AND created_at >= DATE_SUB(CURRENT_DATE, INTERVAL :weeks WEEK) GROUP BY YEARWEEK(created_at, 1) ORDER BY wk", nativeQuery = true)
    List<Object[]> sumRevenueByWeek(@Param("weeks") int weeks);

    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m-01') AS m, COALESCE(SUM(amount),0) AS total FROM payments WHERE status='SUCCESS' AND created_at >= DATE_SUB(CURRENT_DATE, INTERVAL :months MONTH) GROUP BY DATE_FORMAT(created_at, '%Y-%m-01') ORDER BY m", nativeQuery = true)
    List<Object[]> sumRevenueByMonth(@Param("months") int months);
}