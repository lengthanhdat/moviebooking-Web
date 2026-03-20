package com.movie.movie_booking_api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "showtime_id", nullable = false)
    private Long showtimeId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "method", nullable = false)
    private String method;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "order_id", unique = true)
    private String orderId;

    @Column(name = "movie_title")
    private String movieTitle;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}