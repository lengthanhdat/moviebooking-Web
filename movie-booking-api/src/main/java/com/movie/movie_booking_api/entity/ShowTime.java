package com.movie.movie_booking_api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "show_times")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "movie_id", nullable = false)
    private Long movieId;

    @Column(name = "movie_tmdb_id")
    private Long movieTmdbId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "cinema", nullable = false)
    private String cinema;

    @Column(name = "room", nullable = false)
    private String room;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "movie_title")
    private String movieTitle;

    @Column(name = "format")
    private String format;

    @Column(name = "price_vip")
    private Integer priceVip;

    @Column(name = "currency")
    private String currency;

    @Column(name = "status")
    private String status;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "disabled")
    private Boolean disabled;
}