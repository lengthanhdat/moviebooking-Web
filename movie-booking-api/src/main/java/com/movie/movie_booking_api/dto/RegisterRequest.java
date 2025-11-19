// src/main/java/com/movie/movie_booking_api/dto/RegisterRequest.java
package com.movie.movie_booking_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {

    private String name;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;
}
