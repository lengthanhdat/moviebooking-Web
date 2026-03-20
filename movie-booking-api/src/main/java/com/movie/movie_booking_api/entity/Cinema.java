package com.movie.movie_booking_api.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cinemas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cinema {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String location;

    @Column(name = "status")
    private String status; // ACTIVE, MAINTENANCE
}