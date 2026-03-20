// src/main/java/com/movie/movie_booking_api/entity/User.java
package com.movie.movie_booking_api.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_name", nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "fullname", nullable = false)
    private String fullName;

    @Column(name = "name", nullable = false)
    private String legacyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private com.movie.movie_booking_api.entity.UserRole role;

    @Column(name = "locked")
    private Boolean locked;

    @Column(name = "email_verified")
    private Boolean emailVerified;

    @Column(name = "email_verified_at")
    private java.time.LocalDateTime emailVerifiedAt;

    @Column(name = "verification_token", length = 200)
    private String verificationToken;

    @Column(name = "verification_expires_at")
    private java.time.LocalDateTime verificationExpiresAt;
}
