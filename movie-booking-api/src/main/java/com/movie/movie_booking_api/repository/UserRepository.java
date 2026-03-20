// src/main/java/com/movie/movie_booking_api/repository/UserRepository.java
package com.movie.movie_booking_api.repository;

import com.movie.movie_booking_api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);
    Optional<User> findByVerificationToken(String verificationToken);
}
