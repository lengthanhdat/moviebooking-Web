// src/main/java/com/movie/movie_booking_api/dto/AuthResponse.java
package com.movie.movie_booking_api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String name;
    private String email;
}
