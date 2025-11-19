// src/main/java/com/movie/movie_booking_api/dto/LoginRequest.java
package com.movie.movie_booking_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;
}
