package com.movie.movie_booking_api.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "movies", indexes = {@Index(name = "idx_tmdb_id", columnList = "tmdb_id")})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    private String genre;

    @Column(name = "tmdb_id")
    private Long tmdbId;

    @Column(name = "movie_id", nullable = true, length = 64)
    private String movieIdLegacy;

    @Column(name = "title_vi")
    private String titleVi;

    @Column(name = "title_en")
    private String titleEn;

    @Column(name = "overview_vi", length = 4000)
    private String overviewVi;

    @Column(name = "overview_en", length = 4000)
    private String overviewEn;

    @Column(name = "runtime")
    private Integer runtime;

    

    private Integer durationMinutes;

    private String director;

    @Column(name = "cast_text", length = 2000)
    private String cast;

    private String ageRating;

    private String language;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;

    @Column(name = "backdrop_url", length = 500)
    private String backdropUrl;

    @Convert(converter = MovieStatusConverter.class)
    private MovieStatus status;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;
}
