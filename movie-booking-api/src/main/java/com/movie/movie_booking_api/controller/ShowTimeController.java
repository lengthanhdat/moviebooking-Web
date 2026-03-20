package com.movie.movie_booking_api.controller;

import com.movie.movie_booking_api.entity.ShowTime;
import com.movie.movie_booking_api.repository.ShowTimeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/showtimes")
@RequiredArgsConstructor
@Slf4j
public class ShowTimeController {

    private final ShowTimeRepository showTimeRepository;
    private final com.movie.movie_booking_api.repository.MovieRepository movieRepository;
    private static final java.time.ZoneId ZONE = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
    private static final java.time.format.DateTimeFormatter ISO_OFFSET = java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private static boolean isPublicVisible(ShowTime st) {
        if (st == null) return false;
        if (Boolean.TRUE.equals(st.getDisabled())) return false;
        if (st.getStartTime() == null) return false;
        String status = st.getStatus();
        if (status != null) {
            if ("CANCELLED".equalsIgnoreCase(status)) return false;
            if ("FINISHED".equalsIgnoreCase(status)) return false;
        }
        Integer dur = st.getDurationMinutes();
        int minutes = (dur == null || dur <= 0) ? 0 : dur;
        java.time.LocalDateTime end = st.getStartTime().plusMinutes(minutes);
        java.time.LocalDateTime now = java.time.LocalDateTime.now(ZONE);
        return !end.isBefore(now);
    }

    @GetMapping
    public ResponseEntity<?> getShowTimes(@RequestParam("movieId") Long movieId) {
        log.info("GET /api/showtimes movieId={}", movieId);
        List<ShowTime> list = showTimeRepository.findByMovieTmdbId(movieId);
        if (list.isEmpty()) {
            list = showTimeRepository.findByMovieId(movieId);
        }
        list = list.stream().filter(ShowTimeController::isPublicVisible).toList();
        List<Map<String, Object>> result = list
                .stream()
                .map(st -> {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", st.getId());
                    m.put("movieId", st.getMovieId());
                    m.put("movieTmdbId", st.getMovieTmdbId());
                    m.put("startTime", st.getStartTime() == null ? null : st.getStartTime().atZone(ZONE).toOffsetDateTime().format(ISO_OFFSET));
                    m.put("cinema", st.getCinema());
                    m.put("room", st.getRoom());
                    m.put("durationMinutes", st.getDurationMinutes());
                    m.put("price", st.getPrice());
                    java.util.Map<String, Object> pricing = new java.util.HashMap<>();
                    pricing.put("NORMAL", st.getPrice());
                    pricing.put("VIP", st.getPriceVip() == null ? (st.getPrice() + 20000) : st.getPriceVip());
                    m.put("pricing", pricing);
                    m.put("currency", st.getCurrency() == null ? "VND" : st.getCurrency());
                    m.put("movieTitle", st.getMovieTitle());
                    m.put("format", st.getFormat());
                    m.put("status", st.getStatus());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    @org.springframework.cache.annotation.Caching(evict = {
            @org.springframework.cache.annotation.CacheEvict(value = "showtimes", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "public_now_playing", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "public_upcoming", allEntries = true)
    })
    public ResponseEntity<?> createShowTime(@org.springframework.web.bind.annotation.RequestBody ShowTime payload) {
        if (payload.getDurationMinutes() == null || payload.getDurationMinutes() <= 0) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "durationMinutes required");
        }
        if (payload.getMovieId() == null && payload.getMovieTmdbId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "movieId or movieTmdbId required");
        }
        if (payload.getMovieId() == null && payload.getMovieTmdbId() != null) {
            movieRepository.findByTmdbId(payload.getMovieTmdbId()).ifPresent(m -> {
                payload.setMovieId(m.getId());
                if (payload.getMovieTitle() == null || payload.getMovieTitle().isBlank()) {
                    payload.setMovieTitle(m.getTitle());
                }
            });
        } else if (payload.getMovieId() != null && (payload.getMovieTitle() == null || payload.getMovieTitle().isBlank())) {
            movieRepository.findById(payload.getMovieId()).ifPresent(m -> payload.setMovieTitle(m.getTitle()));
        }
        java.time.LocalDateTime start = payload.getStartTime();
        java.time.LocalDateTime end = start.plusMinutes(payload.getDurationMinutes());
        java.util.List<ShowTime> conflicts = showTimeRepository.findConflicts(payload.getCinema(), payload.getRoom(), start, end);
        if (!conflicts.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Showtime conflict");
        }
        ShowTime st = ShowTime.builder()
                .movieId(payload.getMovieId())
                .movieTmdbId(payload.getMovieTmdbId())
                .startTime(payload.getStartTime())
                .cinema(payload.getCinema())
                .room(payload.getRoom())
                .price(payload.getPrice())
                .movieTitle(payload.getMovieTitle())
                .format(payload.getFormat())
                .durationMinutes(payload.getDurationMinutes())
                .status(payload.getStatus() == null ? "ACTIVE" : payload.getStatus())
                .disabled(Boolean.FALSE)
                .build();
        st = showTimeRepository.save(st);
        java.util.Map<String, Object> resp = java.util.Map.of(
                "id", st.getId(),
                "movieId", st.getMovieId(),
                "startTime", st.getStartTime().atOffset(java.time.ZoneOffset.UTC),
                "cinema", st.getCinema(),
                "room", st.getRoom(),
                "price", st.getPrice(),
                "movieTitle", st.getMovieTitle(),
                "durationMinutes", st.getDurationMinutes(),
                "disabled", st.getDisabled()
        );
        return ResponseEntity.status(201).body(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getShowTimeById(@PathVariable("id") Long id) {
        ShowTime st = showTimeRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        if (!isPublicVisible(st)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        }
        java.util.Map<String, Object> resp = java.util.Map.of(
                "id", st.getId(),
                "startTime", st.getStartTime().atOffset(java.time.ZoneOffset.UTC),
                "cinema", st.getCinema(),
                "room", st.getRoom(),
                "price", st.getPrice(),
                "movieTitle", st.getMovieTitle(),
                "durationMinutes", st.getDurationMinutes(),
                "disabled", st.getDisabled()
        );
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/admin/showtimes")
    public ResponseEntity<?> createShowTimeAdmin(@org.springframework.web.bind.annotation.RequestBody ShowTime payload) {
        return createShowTime(payload);
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}/title")
    @org.springframework.cache.annotation.Caching(evict = {
            @org.springframework.cache.annotation.CacheEvict(value = "showtimes", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "public_now_playing", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "public_upcoming", allEntries = true)
    })
    public ResponseEntity<?> updateTitle(@PathVariable("id") Long id,
                                         @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, String> body) {
        String title = body.get("movieTitle");
        ShowTime st = showTimeRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        st.setMovieTitle(title);
        st = showTimeRepository.save(st);
        java.util.Map<String, Object> resp = java.util.Map.of(
                "id", st.getId(),
                "movieId", st.getMovieId(),
                "movieTitle", st.getMovieTitle()
        );
        return ResponseEntity.ok(resp);
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}/disable")
    @org.springframework.cache.annotation.Caching(evict = {
            @org.springframework.cache.annotation.CacheEvict(value = "showtimes", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "public_now_playing", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "public_upcoming", allEntries = true)
    })
    public ResponseEntity<?> disable(@PathVariable("id") Long id, @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Boolean> body) {
        Boolean disabled = body.getOrDefault("disabled", Boolean.TRUE);
        ShowTime st = showTimeRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        st.setDisabled(disabled);
        st = showTimeRepository.save(st);
        return ResponseEntity.ok(java.util.Map.of("id", st.getId(), "disabled", st.getDisabled()));
    }
}
