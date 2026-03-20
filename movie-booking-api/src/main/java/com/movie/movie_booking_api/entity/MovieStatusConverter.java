package com.movie.movie_booking_api.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class MovieStatusConverter implements AttributeConverter<MovieStatus, String> {

    @Override
    public String convertToDatabaseColumn(MovieStatus status) {
        return status == null ? null : status.name();
    }

    @Override
    public MovieStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        String s = dbData.trim().toUpperCase();
        if ("PLAYING".equals(s)) s = "NOW_SHOWING";
        if ("ARCHIVED".equals(s)) s = "STOPPED";
        if ("UPCOMING".equals(s)) return MovieStatus.UPCOMING;
        if ("NOW_SHOWING".equals(s)) return MovieStatus.NOW_SHOWING;
        if ("STOPPED".equals(s)) return MovieStatus.STOPPED;
        try {
            return MovieStatus.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return MovieStatus.UPCOMING;
        }
    }
}