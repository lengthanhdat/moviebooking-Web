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

    @GetMapping
    public ResponseEntity<?> getShowTimes(@RequestParam("movieId") Long movieId) {
        log.info("GET /api/showtimes movieId={}", movieId);
        List<Map<String, Object>> result = showTimeRepository.findByMovieId(movieId)
                .stream()
                .map(st -> {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", st.getId());
                    m.put("movieId", st.getMovieId());
                    m.put("startTime", st.getStartTime().atOffset(java.time.ZoneOffset.UTC));
                    m.put("cinema", st.getCinema());
                    m.put("room", st.getRoom());
                    m.put("price", st.getPrice());
                    java.util.Map<String, Object> pricing = new java.util.HashMap<>();
                    pricing.put("NORMAL", st.getPrice());
                    pricing.put("VIP", st.getPriceVip() == null ? (st.getPrice() + 20000) : st.getPriceVip());
                    m.put("pricing", pricing);
                    m.put("currency", st.getCurrency() == null ? "VND" : st.getCurrency());
                    m.put("movieTitle", st.getMovieTitle());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<?> createShowTime(@org.springframework.web.bind.annotation.RequestBody ShowTime payload) {
        ShowTime st = ShowTime.builder()
                .movieId(payload.getMovieId())
                .startTime(payload.getStartTime())
                .cinema(payload.getCinema())
                .room(payload.getRoom())
                .price(payload.getPrice())
                .movieTitle(payload.getMovieTitle())
                .build();
        st = showTimeRepository.save(st);
        java.util.Map<String, Object> resp = java.util.Map.of(
                "id", st.getId(),
                "movieId", st.getMovieId(),
                "startTime", st.getStartTime().atOffset(java.time.ZoneOffset.UTC),
                "cinema", st.getCinema(),
                "room", st.getRoom(),
                "price", st.getPrice(),
                "movieTitle", st.getMovieTitle()
        );
        return ResponseEntity.status(201).body(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getShowTimeById(@PathVariable("id") Long id) {
        ShowTime st = showTimeRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        java.util.Map<String, Object> resp = java.util.Map.of(
                "id", st.getId(),
                "startTime", st.getStartTime().atOffset(java.time.ZoneOffset.UTC),
                "cinema", st.getCinema(),
                "room", st.getRoom(),
                "price", st.getPrice(),
                "movieTitle", st.getMovieTitle()
        );
        return ResponseEntity.ok(resp);
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}/title")
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
}