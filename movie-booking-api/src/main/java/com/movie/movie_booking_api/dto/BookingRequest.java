package com.movie.movie_booking_api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BookingRequest {
    @NotNull
    private Long showtimeId;
    @NotEmpty
    private List<String> seats;

    private String holdId;
}
