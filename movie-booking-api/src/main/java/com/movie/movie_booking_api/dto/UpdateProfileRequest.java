package com.movie.movie_booking_api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String fullName;
}