package com.movie.movie_booking_api.controller;

import com.movie.movie_booking_api.entity.Movie;
import com.movie.movie_booking_api.entity.MovieStatus;
import com.movie.movie_booking_api.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/movies")
@RequiredArgsConstructor
@Slf4j
public class MovieController {

    private final MovieRepository movieRepository;
    private static final java.time.Duration TMDB_TIMEOUT = java.time.Duration.ofSeconds(3);
    @org.springframework.beans.factory.annotation.Value("${tmdb.api-key:}")
    private String tmdbApiKey;
    @org.springframework.beans.factory.annotation.Value("${tmdb.read-access-token:}")
    private String tmdbReadAccessToken;

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(value = "q", required = false) String q,
                                  @RequestParam(value = "query", required = false) String query,
                                  @RequestParam(value = "genre", required = false) String genre,
                                  @RequestParam(value = "movieStatus", required = false) String status,
                                  @RequestParam(value = "status", required = false) String status2) {
        try {
            if (q == null || q.isBlank()) q = (query == null ? null : query);
            if (status == null || status.isBlank()) status = status2;
            log.info("Movies list params q={}, genre={}, status={}", q, genre, status);
            List<Movie> result;
            if (q != null && !q.isBlank()) {
                result = movieRepository.findByTitleContainingIgnoreCase(q);
            } else if (genre != null && !genre.isBlank()) {
                result = movieRepository.findByGenreContainingIgnoreCase(genre);
            } else if (status != null && !status.isBlank()) {
                String s = status.trim().toUpperCase();
                if ("PLAYING".equals(s)) s = "NOW_SHOWING";
                if ("UPCOMING".equals(s) || "NOW_SHOWING".equals(s) || "STOPPED".equals(s)) {
                    result = movieRepository.findByStatus(MovieStatus.valueOf(s));
                } else {
                    result = movieRepository.findAll();
                }
            } else {
                result = movieRepository.findAll();
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("List movies failed", e);
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Movies API error: " + e.getMessage());
        }
    }

    @GetMapping(value = "/list", produces = "application/json; charset=utf-8")
    public ResponseEntity<?> listAll(
            @RequestParam(value = "source", defaultValue = "all") String source,
            @RequestParam(value = "status", defaultValue = "ALL") String status
    ) {
        List<Movie> base = movieRepository.findAll();
        boolean hasTmdb = movieRepository.existsByTmdbIdIsNotNull();
        if (!hasTmdb && ((tmdbApiKey != null && !tmdbApiKey.isBlank()) || (tmdbReadAccessToken != null && !tmdbReadAccessToken.isBlank()))) {
            long t0 = System.currentTimeMillis();
            try {
                int imported = 0;
                imported += importFromTmdbInternalAuth("now_playing", "vi-VN", 1);
                imported += importFromTmdbInternalAuth("upcoming", "vi-VN", 1);
                long t1 = System.currentTimeMillis();
                log.info("Auto-import TMDB: imported={}, elapsed={}ms", imported, (t1 - t0));
                base = movieRepository.findAll();
            } catch (Exception e) {
                log.warn("Auto-import TMDB failed: {}", e.getMessage());
            }
        }

        String src = source == null ? "all" : source.trim().toLowerCase();
        String st = status == null ? "ALL" : status.trim().toUpperCase();
        List<Map<String, Object>> list = base.stream()
                .filter(m -> {
                    boolean tmdb = m.getTmdbId() != null;
                    if ("tmdb".equals(src)) return tmdb;
                    if ("internal".equals(src)) return !tmdb;
                    return true;
                })
                .filter(m -> {
                    if ("ALL".equals(st)) return true;
                    if (m.getStatus() == null) return false;
                    if ("ARCHIVED".equals(st)) return m.getStatus() == MovieStatus.STOPPED;
                    try { return m.getStatus() == MovieStatus.valueOf(st); } catch (IllegalArgumentException ex) { return true; }
                })
                .map(m -> {
                    java.util.Map<String, Object> it = new java.util.HashMap<>();
                    it.put("id", m.getId());
                    it.put("tmdbId", m.getTmdbId());
                    it.put("titleVi", m.getTitleVi());
                    it.put("titleEn", m.getTitleEn());
                    it.put("overviewVi", m.getOverviewVi());
                    it.put("runtime", m.getRuntime());
                    it.put("durationMinutes", m.getDurationMinutes());
                    java.util.List<String> gs = m.getGenre() == null ? java.util.List.of() : java.util.Arrays.stream(m.getGenre().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
                    it.put("genres", gs);
                    it.put("genre", m.getGenre());
                    it.put("ageRating", m.getAgeRating());
                    it.put("language", m.getLanguage());
                    it.put("posterUrl", m.getPosterUrl());
                    it.put("backdropUrl", m.getBackdropUrl());
                    it.put("status", m.getStatus());
                    it.put("source", m.getTmdbId() != null ? "TMDB" : "INTERNAL");
                    it.put("createdAt", m.getCreatedAt());
                    it.put("title", m.getTitle());
                    it.put("description", m.getDescription());
                    return it;
                })
                .toList();
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(list);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Movie payload) {
        Movie m = movieRepository.save(payload);
        return ResponseEntity.status(201).body(m);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(@PathVariable("id") Long id) {
        Movie m = movieRepository.findById(id).orElse(null);
        if (m == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body("NOT_FOUND");
        }
        java.util.Map<String, Object> it = new java.util.HashMap<>();
        it.put("id", m.getId());
        it.put("tmdbId", m.getTmdbId());
        it.put("titleVi", m.getTitleVi());
        it.put("titleEn", m.getTitleEn());
        it.put("title", m.getTitle());
        it.put("overviewVi", m.getOverviewVi());
        it.put("overviewEn", m.getOverviewEn());
        it.put("description", m.getDescription());
        it.put("runtime", m.getRuntime());
        it.put("durationMinutes", m.getDurationMinutes());
        it.put("genre", m.getGenre());
        it.put("ageRating", m.getAgeRating());
        it.put("language", m.getLanguage());
        it.put("posterUrl", m.getPosterUrl());
        it.put("backdropUrl", m.getBackdropUrl());
        it.put("cast", m.getCast());
        it.put("director", m.getDirector());
        it.put("status", m.getStatus());
        try {
            Long tmdbId = m.getTmdbId();
            String apiKey = tmdbApiKey;
            if (tmdbId != null) {
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(2))
                        .build();
                if (apiKey != null && !apiKey.isBlank()) {
                    String endpoint = "https://api.themoviedb.org/3/movie/" + tmdbId
                            + "?api_key=" + java.net.URLEncoder.encode(apiKey, java.nio.charset.StandardCharsets.UTF_8)
                            + "&language=vi-VN";
                    java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(endpoint))
                            .timeout(TMDB_TIMEOUT)
                            .GET()
                            .build();
                    java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                        com.fasterxml.jackson.databind.JsonNode root = om.readTree(resp.body());
                        double vote = root.path("vote_average").asDouble(0);
                        String releaseDate = root.path("release_date").asText(null);
                        if (vote > 0) it.put("voteAverage", vote);
                        if (releaseDate != null && !releaseDate.isBlank()) it.put("releaseDate", releaseDate);
                        if (root.path("genres").isArray()) {
                            java.util.List<String> gs = new java.util.ArrayList<>();
                            for (com.fasterxml.jackson.databind.JsonNode g : root.path("genres")) gs.add(g.path("name").asText());
                            it.put("genres", gs);
                        }
                        if (root.path("production_companies").isArray()) {
                            java.util.List<String> comps = new java.util.ArrayList<>();
                            for (com.fasterxml.jackson.databind.JsonNode c : root.path("production_companies")) comps.add(c.path("name").asText());
                            it.put("companies", comps);
                        }
                        if (root.path("production_countries").isArray()) {
                            java.util.List<String> countries = new java.util.ArrayList<>();
                            for (com.fasterxml.jackson.databind.JsonNode c : root.path("production_countries")) countries.add(c.path("name").asText());
                            it.put("countries", countries);
                        }
                    }
                } else if (tmdbReadAccessToken != null && !tmdbReadAccessToken.isBlank()) {
                    String endpoint = "https://api.themoviedb.org/3/movie/" + tmdbId + "?language=vi-VN";
                    java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(endpoint))
                            .header("Authorization", "Bearer " + tmdbReadAccessToken)
                            .timeout(TMDB_TIMEOUT)
                            .GET().build();
                    java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                        com.fasterxml.jackson.databind.JsonNode root = om.readTree(resp.body());
                        double vote = root.path("vote_average").asDouble(0);
                        String releaseDate = root.path("release_date").asText(null);
                        if (vote > 0) it.put("voteAverage", vote);
                        if (releaseDate != null && !releaseDate.isBlank()) it.put("releaseDate", releaseDate);
                        if (root.path("genres").isArray()) {
                            java.util.List<String> gs = new java.util.ArrayList<>();
                            for (com.fasterxml.jackson.databind.JsonNode g : root.path("genres")) gs.add(g.path("name").asText());
                            it.put("genres", gs);
                        }
                        if (root.path("production_companies").isArray()) {
                            java.util.List<String> comps = new java.util.ArrayList<>();
                            for (com.fasterxml.jackson.databind.JsonNode c : root.path("production_companies")) comps.add(c.path("name").asText());
                            it.put("companies", comps);
                        }
                        if (root.path("production_countries").isArray()) {
                            java.util.List<String> countries = new java.util.ArrayList<>();
                            for (com.fasterxml.jackson.databind.JsonNode c : root.path("production_countries")) countries.add(c.path("name").asText());
                            it.put("countries", countries);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        if (m.getTmdbId() != null && (it.get("director") == null || String.valueOf(it.get("director")).isBlank())) {
            try {
                String director = fetchTmdbDirectorName(m.getTmdbId());
                if (director != null && !director.isBlank()) it.put("director", director);
            } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(it);
    }

    private String fetchTmdbDirectorName(Long tmdbId) throws Exception {
        if (tmdbId == null) return null;
        String base = "https://api.themoviedb.org/3/movie/" + tmdbId + "/credits?language=vi-VN";
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(2))
                .build();
        java.net.http.HttpRequest req = null;
        if (tmdbApiKey != null && !tmdbApiKey.isBlank()) {
            String endpoint = base + "&api_key=" + java.net.URLEncoder.encode(tmdbApiKey, java.nio.charset.StandardCharsets.UTF_8);
            req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(endpoint)).timeout(TMDB_TIMEOUT).GET().build();
        } else if (tmdbReadAccessToken != null && !tmdbReadAccessToken.isBlank()) {
            req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base))
                    .header("Authorization", "Bearer " + tmdbReadAccessToken)
                    .timeout(TMDB_TIMEOUT)
                    .GET().build();
        }
        if (req == null) return null;
        java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return null;
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode root = om.readTree(resp.body());
        com.fasterxml.jackson.databind.JsonNode crew = root.get("crew");
        if (crew == null || !crew.isArray()) return null;

        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (com.fasterxml.jackson.databind.JsonNode n : crew) {
            String job = n.path("job").asText("");
            if (job == null || job.isBlank()) continue;
            String jobLc = job.toLowerCase(java.util.Locale.ROOT);
            if (!(jobLc.equals("director") || jobLc.contains("director"))) continue;
            String name = n.path("name").asText("");
            if (name != null && !name.isBlank()) names.add(name);
        }
        if (names.isEmpty()) return null;
        return String.join(", ", names);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable("id") Long id, @RequestBody Movie payload) {
        Movie m = movieRepository.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        m.setTitle(payload.getTitle());
        m.setDescription(payload.getDescription());
        m.setGenre(payload.getGenre());
        m.setDurationMinutes(payload.getDurationMinutes());
        m.setDirector(payload.getDirector());
        m.setCast(payload.getCast());
        m.setAgeRating(payload.getAgeRating());
        m.setLanguage(payload.getLanguage());
        m.setStatus(payload.getStatus());
        m = movieRepository.save(m);
        return ResponseEntity.ok(m);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") Long id) {
        movieRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    @PostMapping("/import-tmdb")
    public ResponseEntity<?> importFromTmdb(
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body
    ) {
        try {
            Long tmdbId = body.get("tmdbId") == null ? null : ((Number) body.get("tmdbId")).longValue();
            String statusStr = String.valueOf(body.getOrDefault("status", "NOW_SHOWING"));
            if (tmdbId == null) {
                return ResponseEntity.badRequest().body("tmdbId required");
            }
            if (movieRepository.findByTmdbId(tmdbId).isPresent()) {
                return ResponseEntity.status(409).body(java.util.Map.of("error", "MOVIE_ALREADY_EXISTS", "tmdbId", tmdbId));
            }
            String apiKey = (tmdbApiKey == null || tmdbApiKey.isBlank()) ? String.valueOf(body.getOrDefault("apiKey", "")) : tmdbApiKey;
            if (apiKey == null || apiKey.isBlank()) {
                return ResponseEntity.badRequest().body("TMDB apiKey missing");
            }

            java.util.Map<String, Object> m = fetchTmdbMovie(apiKey, tmdbId, "vi-VN", "en-US");
            MovieStatus st;
            String s = statusStr == null ? "" : statusStr.trim().toUpperCase();
            if ("ARCHIVED".equals(s)) s = "STOPPED";
            try { st = MovieStatus.valueOf(s); } catch (IllegalArgumentException ex) { st = MovieStatus.NOW_SHOWING; }
            Movie movie = movieRepository.findByTmdbId(tmdbId).orElse(Movie.builder().createdAt(java.time.LocalDateTime.now()).build());
            movie.setTmdbId(tmdbId);
            if (movie.getMovieIdLegacy() == null) movie.setMovieIdLegacy("0");
            movie.setTitleVi((String) m.get("titleVi"));
            movie.setTitleEn((String) m.get("titleEn"));
            movie.setOverviewVi((String) m.get("overviewVi"));
            movie.setOverviewEn((String) m.get("overviewEn"));
            movie.setRuntime((Integer) m.get("runtime"));
            movie.setLanguage((String) m.get("language"));
            movie.setPosterUrl((String) m.get("posterUrl"));
            movie.setBackdropUrl((String) m.get("backdropUrl"));
            @SuppressWarnings("unchecked")
            java.util.List<String> gs0 = (java.util.List<String>) m.get("genres");
            movie.setGenre(gs0 == null ? null : String.join(", ", gs0));
            movie.setStatus(st);
            movie.setTitle(movie.getTitleVi() == null ? movie.getTitleEn() : movie.getTitleVi());
            movie.setDescription(movie.getOverviewVi() == null ? movie.getOverviewEn() : movie.getOverviewVi());
            movie.setUpdatedAt(java.time.LocalDateTime.now());
            movie = movieRepository.save(movie);
            return ResponseEntity.status(201).body(movie);
        } catch (Exception e) {
            log.error("Import TMDB failed", e);
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Import TMDB error: " + e.getMessage());
        }
    }

    private int importFromTmdbInternal(String apiKey, String type, String lang, int page) throws Exception {
        String endpoint = "https://api.themoviedb.org/3/movie/" + type
                + "?api_key=" + java.net.URLEncoder.encode(apiKey, java.nio.charset.StandardCharsets.UTF_8)
                + "&language=" + java.net.URLEncoder.encode(lang, java.nio.charset.StandardCharsets.UTF_8)
                + "&page=" + page;

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(endpoint))
                .GET()
                .build();
        java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("TMDB error: " + resp.body());
        }

        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode root = om.readTree(resp.body());
        com.fasterxml.jackson.databind.JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            return 0;
        }

        java.util.Set<Long> existingTmdbIds = movieRepository.findAll().stream()
                .map(m -> m.getTmdbId() == null ? 0L : m.getTmdbId())
                .filter(id -> id != 0L)
                .collect(java.util.stream.Collectors.toSet());

        java.util.List<Movie> toSave = new java.util.ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode n : results) {
            Long tmdbId = n.path("id").asLong(0L);
            if (tmdbId == null || tmdbId == 0L) continue;
            if (existingTmdbIds.contains(tmdbId) || movieRepository.findByTmdbId(tmdbId).isPresent()) continue;
            String title = n.path("title").asText(null);
            String overview = n.path("overview").asText(null);
            String originalLanguage = n.path("original_language").asText(null);
            String posterPath = n.path("poster_path").asText(null);
            MovieStatus st;
            String t = type.toLowerCase();
            if ("now_playing".equals(t)) st = MovieStatus.NOW_SHOWING;
            else if ("upcoming".equals(t)) st = MovieStatus.UPCOMING;
            else st = MovieStatus.NOW_SHOWING;

            Movie m = Movie.builder()
                    .title(title)
                    .description(overview)
                    .language(originalLanguage)
                    .tmdbId(tmdbId)
                    .posterUrl(posterPath == null ? null : ("https://image.tmdb.org/t/p/w500" + posterPath))
                    .status(st)
                    .movieIdLegacy("0")
                    .createdAt(java.time.LocalDateTime.now())
                    .updatedAt(java.time.LocalDateTime.now())
                    .build();
            toSave.add(m);
            existingTmdbIds.add(tmdbId);
        }

        if (!toSave.isEmpty()) movieRepository.saveAll(toSave);
        return toSave.size();
    }

    private int importFromTmdbInternalAuth(String type, String lang, int page) throws Exception {
        String cleanedType = type == null ? "" : type.trim();
        String cleanedLang = (lang == null || lang.isBlank()) ? "vi-VN" : lang.trim();
        int cleanedPage = Math.max(1, page);

        String base = "https://api.themoviedb.org/3/movie/" + cleanedType
                + "?language=" + java.net.URLEncoder.encode(cleanedLang, java.nio.charset.StandardCharsets.UTF_8)
                + "&page=" + cleanedPage;

        java.net.http.HttpRequest req;
        if (tmdbApiKey != null && !tmdbApiKey.isBlank()) {
            String endpoint = base + "&api_key=" + java.net.URLEncoder.encode(tmdbApiKey, java.nio.charset.StandardCharsets.UTF_8);
            req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(endpoint))
                    .GET()
                    .build();
        } else if (tmdbReadAccessToken != null && !tmdbReadAccessToken.isBlank()) {
            req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(base))
                    .header("Authorization", "Bearer " + tmdbReadAccessToken)
                    .GET()
                    .build();
        } else {
            return 0;
        }

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("TMDB error: " + resp.body());
        }

        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode root = om.readTree(resp.body());
        com.fasterxml.jackson.databind.JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            return 0;
        }

        java.util.Set<Long> existingTmdbIds = movieRepository.findAll().stream()
                .map(m -> m.getTmdbId() == null ? 0L : m.getTmdbId())
                .filter(id -> id != 0L)
                .collect(java.util.stream.Collectors.toSet());

        java.util.List<Movie> toSave = new java.util.ArrayList<>();
        for (com.fasterxml.jackson.databind.JsonNode n : results) {
            Long tmdbId = n.path("id").asLong(0L);
            if (tmdbId == null || tmdbId == 0L) continue;
            if (existingTmdbIds.contains(tmdbId) || movieRepository.findByTmdbId(tmdbId).isPresent()) continue;
            String title = n.path("title").asText(null);
            String overview = n.path("overview").asText(null);
            String originalLanguage = n.path("original_language").asText(null);
            String posterPath = n.path("poster_path").asText(null);
            MovieStatus st;
            String t = cleanedType.toLowerCase();
            if ("now_playing".equals(t)) st = MovieStatus.NOW_SHOWING;
            else if ("upcoming".equals(t)) st = MovieStatus.UPCOMING;
            else st = MovieStatus.NOW_SHOWING;

            Movie m = Movie.builder()
                    .title(title)
                    .description(overview)
                    .language(originalLanguage)
                    .tmdbId(tmdbId)
                    .posterUrl(posterPath == null ? null : ("https://image.tmdb.org/t/p/w500" + posterPath))
                    .status(st)
                    .movieIdLegacy("0")
                    .createdAt(java.time.LocalDateTime.now())
                    .updatedAt(java.time.LocalDateTime.now())
                    .build();
            toSave.add(m);
            existingTmdbIds.add(tmdbId);
        }

        if (!toSave.isEmpty()) movieRepository.saveAll(toSave);
        return toSave.size();
    }

    private java.util.Map<String, Object> fetchTmdbMovie(String apiKey, Long tmdbId, String langVi, String langEn) throws Exception {
        String base = "https://api.themoviedb.org/3/movie/" + tmdbId;
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest reqVi = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(base + "?api_key=" + java.net.URLEncoder.encode(apiKey, java.nio.charset.StandardCharsets.UTF_8) + "&language=" + java.net.URLEncoder.encode(langVi, java.nio.charset.StandardCharsets.UTF_8)))
                .GET().build();
        java.net.http.HttpRequest reqEn = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(base + "?api_key=" + java.net.URLEncoder.encode(apiKey, java.nio.charset.StandardCharsets.UTF_8) + "&language=" + java.net.URLEncoder.encode(langEn, java.nio.charset.StandardCharsets.UTF_8)))
                .GET().build();
        java.net.http.HttpResponse<String> respVi = client.send(reqVi, java.net.http.HttpResponse.BodyHandlers.ofString());
        java.net.http.HttpResponse<String> respEn = client.send(reqEn, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (respVi.statusCode() != 200) throw new IllegalStateException("TMDB vi error: " + respVi.body());
        if (respEn.statusCode() != 200) throw new IllegalStateException("TMDB en error: " + respEn.body());
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode vi = om.readTree(respVi.body());
        com.fasterxml.jackson.databind.JsonNode en = om.readTree(respEn.body());
        java.util.List<String> genres = new java.util.ArrayList<>();
        if (vi.path("genres").isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode g : vi.path("genres")) genres.add(g.path("name").asText());
        }
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("titleVi", vi.path("title").asText());
        m.put("overviewVi", vi.path("overview").asText());
        m.put("titleEn", en.path("title").asText());
        m.put("overviewEn", en.path("overview").asText());
        m.put("runtime", vi.path("runtime").asInt(0));
        m.put("language", vi.path("original_language").asText());
        String posterPath = vi.path("poster_path").asText(null);
        String backdropPath = vi.path("backdrop_path").asText(null);
        m.put("posterUrl", posterPath == null ? null : ("https://image.tmdb.org/t/p/w500" + posterPath));
        m.put("backdropUrl", backdropPath == null ? null : ("https://image.tmdb.org/t/p/w780" + backdropPath));
        m.put("genres", genres);
        return m;
    }

    @GetMapping("/tmdb")
    public ResponseEntity<?> tmdbList(
            @RequestParam("apiKey") String apiKey,
            @RequestParam(name = "type", defaultValue = "popular") String type,
            @RequestParam(name = "lang", defaultValue = "vi-VN") String lang,
            @RequestParam(name = "page", defaultValue = "1") int page
    ) {
        try {
            String endpoint = "https://api.themoviedb.org/3/movie/" + type
                    + "?api_key=" + java.net.URLEncoder.encode(apiKey, java.nio.charset.StandardCharsets.UTF_8)
                    + "&language=" + java.net.URLEncoder.encode(lang, java.nio.charset.StandardCharsets.UTF_8)
                    + "&page=" + page;
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(endpoint))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return ResponseEntity.status(resp.statusCode()).body(resp.body());
            }
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = om.readTree(resp.body());
            com.fasterxml.jackson.databind.JsonNode results = root.get("results");
            java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
            if (results != null && results.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode n : results) {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", n.path("id").asLong());
                    m.put("title", n.path("title").asText());
                    m.put("description", n.path("overview").asText());
                    m.put("language", n.path("original_language").asText());
                    m.put("releaseDate", n.path("release_date").asText());
                    m.put("poster", n.path("poster_path").asText());
                    m.put("source", "TMDB");
                    list.add(m);
                }
            }
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            log.error("TMDB list failed", e);
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "TMDB list error: " + e.getMessage());
        }
    }

    @PostMapping("/import-batch")
    public ResponseEntity<?> importBatch(@org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body) {
        String apiKey = (tmdbApiKey == null || tmdbApiKey.isBlank()) ? String.valueOf(body.getOrDefault("apiKey", "")) : tmdbApiKey;
        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.badRequest().body("TMDB apiKey missing");
        }
        @SuppressWarnings("unchecked")
        java.util.List<Number> ids = (java.util.List<Number>) body.getOrDefault("tmdbIds", java.util.List.of());
        java.util.List<java.util.Map<String, Object>> created = new java.util.ArrayList<>();
        for (Number n : ids) {
            try {
                Long tmdbId = n.longValue();
                java.util.Map<String, Object> m = fetchTmdbMovie(apiKey, tmdbId, "vi-VN", "en-US");
            Movie movie = movieRepository.findByTmdbId(tmdbId).orElse(Movie.builder().createdAt(java.time.LocalDateTime.now()).movieIdLegacy("0").build());
            movie.setTmdbId(tmdbId);
            movie.setTitleVi((String) m.get("titleVi"));
            movie.setTitleEn((String) m.get("titleEn"));
            movie.setOverviewVi((String) m.get("overviewVi"));
            movie.setOverviewEn((String) m.get("overviewEn"));
                movie.setRuntime((Integer) m.get("runtime"));
                movie.setLanguage((String) m.get("language"));
                movie.setPosterUrl((String) m.get("posterUrl"));
                movie.setBackdropUrl((String) m.get("backdropUrl"));
                @SuppressWarnings("unchecked")
                java.util.List<String> gs = (java.util.List<String>) m.get("genres");
                movie.setGenre(gs == null ? null : String.join(", ", gs));
                movie.setStatus(MovieStatus.NOW_SHOWING);
                movie.setTitle(movie.getTitleVi() == null ? movie.getTitleEn() : movie.getTitleVi());
                movie.setDescription(movie.getOverviewVi() == null ? movie.getOverviewEn() : movie.getOverviewVi());
                movie.setUpdatedAt(java.time.LocalDateTime.now());
                movie = movieRepository.save(movie);
                java.util.Map<String, Object> x = java.util.Map.of("id", movie.getId(), "tmdbId", tmdbId, "title", movie.getTitle());
                created.add(x);
            } catch (Exception ignored) {}
        }
        return ResponseEntity.status(201).body(java.util.Map.of("count", created.size(), "created", created));
    }

    @GetMapping("/tmdb/search")
    public ResponseEntity<?> tmdbSearch(
            @RequestParam("apiKey") String apiKey,
            @RequestParam(name = "q") String q,
            @RequestParam(name = "lang", defaultValue = "vi-VN") String lang,
            @RequestParam(name = "page", defaultValue = "1") int page
    ) {
        try {
            String endpoint = "https://api.themoviedb.org/3/search/movie"
                    + "?api_key=" + java.net.URLEncoder.encode(apiKey, java.nio.charset.StandardCharsets.UTF_8)
                    + "&language=" + java.net.URLEncoder.encode(lang, java.nio.charset.StandardCharsets.UTF_8)
                    + "&page=" + page
                    + "&query=" + java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8);
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(endpoint))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return ResponseEntity.status(resp.statusCode()).body(resp.body());
            }
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = om.readTree(resp.body());
            com.fasterxml.jackson.databind.JsonNode results = root.get("results");
            java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
            if (results != null && results.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode n : results) {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", n.path("id").asLong());
                    m.put("title", n.path("title").asText());
                    m.put("description", n.path("overview").asText());
                    m.put("language", n.path("original_language").asText());
                    m.put("releaseDate", n.path("release_date").asText());
                    m.put("poster", n.path("poster_path").asText());
                    m.put("source", "TMDB");
                    list.add(m);
                }
            }
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            log.error("TMDB search failed", e);
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "TMDB search error: " + e.getMessage());
        }
    }

    @PostMapping("/sync-tmdb")
    public ResponseEntity<?> syncTmdb(
            @RequestParam(value = "pages", defaultValue = "2") int pages,
            @RequestParam(value = "lang", defaultValue = "vi-VN") String lang
    ) {
        if ((tmdbApiKey == null || tmdbApiKey.isBlank()) && (tmdbReadAccessToken == null || tmdbReadAccessToken.isBlank())) {
            return ResponseEntity.badRequest().body("TMDB credentials missing");
        }
        int total = 0;
        try {
            for (int p = 1; p <= Math.max(1, pages); p++) {
                total += importFromTmdbInternalAuth("now_playing", lang, p);
                total += importFromTmdbInternalAuth("upcoming", lang, p);
            }
        } catch (Exception e) {
            log.error("Sync TMDB failed", e);
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Sync TMDB error: " + e.getMessage());
        }
        return ResponseEntity.ok(java.util.Map.of("imported", total));
    }

    @GetMapping("/{id}/trailer")
    public ResponseEntity<?> trailer(@PathVariable("id") Long id) {
        Movie m = movieRepository.findById(id).orElse(null);
        if (m == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body("NOT_FOUND");
        }
        Long tmdbId = m.getTmdbId();
        String apiKey = tmdbApiKey;
        if (tmdbId == null || apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.ok(java.util.Map.of());
        }
        try {
            String endpoint = "https://api.themoviedb.org/3/movie/" + tmdbId + "/videos"
                    + "?api_key=" + java.net.URLEncoder.encode(apiKey, java.nio.charset.StandardCharsets.UTF_8)
                    + "&language=vi-VN";
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(endpoint))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return ResponseEntity.status(resp.statusCode()).body(resp.body());
            }
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = om.readTree(resp.body());
            com.fasterxml.jackson.databind.JsonNode results = root.get("results");
            String key = null; String site = null; String url = null;
            if (results != null && results.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode n : results) {
                    String type = n.path("type").asText("");
                    String s = n.path("site").asText("");
                    if ("Trailer".equalsIgnoreCase(type)) {
                        key = n.path("key").asText(null);
                        site = s;
                        break;
                    }
                }
            }
            if (key != null && site != null) {
                if ("YouTube".equalsIgnoreCase(site)) {
                    url = "https://www.youtube.com/watch?v=" + key;
                } else if ("Vimeo".equalsIgnoreCase(site)) {
                    url = "https://vimeo.com/" + key;
                }
                return ResponseEntity.ok(java.util.Map.of("key", key, "site", site, "url", url));
            }
            return ResponseEntity.ok(java.util.Map.of());
        } catch (Exception e) {
            log.warn("Fetch trailer failed: {}", e.getMessage());
            return ResponseEntity.ok(java.util.Map.of());
        }
    }

    @GetMapping("/{id}/trailers/fallback")
    public ResponseEntity<?> trailerFallback(@PathVariable("id") Long id) {
        Movie m = movieRepository.findById(id).orElse(null);
        if (m == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body("NOT_FOUND");
        }
        String title = m.getTitle() == null ? (m.getTitleVi() == null ? m.getTitleEn() : m.getTitleVi()) : m.getTitle();
        if (title == null || title.isBlank()) {
            return ResponseEntity.ok(java.util.Map.of());
        }
        try {
            String q = java.net.URLEncoder.encode(title + " trailer", java.nio.charset.StandardCharsets.UTF_8);
            String url = "https://www.youtube.com/results?hl=vi&search_query=" + q;
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder().uri(java.net.URI.create(url)).GET().build();
            java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return ResponseEntity.status(resp.statusCode()).body(resp.body());
            }
            String body = resp.body();
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"videoId\":\"([a-zA-Z0-9_-]{11})\"");
            java.util.regex.Matcher mth = p.matcher(body);
            java.util.List<String> keys = new java.util.ArrayList<>();
            while (mth.find()) {
                String vid = mth.group(1);
                if (!keys.contains(vid)) keys.add(vid);
                if (keys.size() >= 5) break;
            }
            if (!keys.isEmpty()) {
                String key = keys.get(0);
                java.util.Map<String, Object> respMap = new java.util.HashMap<>();
                respMap.put("key", key);
                respMap.put("site", "YouTube");
                respMap.put("url", "https://www.youtube.com/watch?v=" + key);
                respMap.put("keys", keys);
                return ResponseEntity.ok(respMap);
            }
            return ResponseEntity.ok(java.util.Map.of());
        } catch (Exception e) {
            log.warn("Fallback trailer search failed: {}", e.getMessage());
            return ResponseEntity.ok(java.util.Map.of());
        }
    }

    @GetMapping("/{id}/credits")
    public ResponseEntity<?> credits(@PathVariable("id") Long id) {
        Movie m = movieRepository.findById(id).orElse(null);
        if (m == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body("NOT_FOUND");
        }
        Long tmdbId = m.getTmdbId();
        String apiKey = tmdbApiKey;
        if (tmdbId == null || apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.ok(java.util.List.of());
        }
        try {
            String endpoint = "https://api.themoviedb.org/3/movie/" + tmdbId + "/credits"
                    + "?api_key=" + java.net.URLEncoder.encode(apiKey, java.nio.charset.StandardCharsets.UTF_8)
                    + "&language=vi-VN";
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(endpoint))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return ResponseEntity.status(resp.statusCode()).body(resp.body());
            }
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = om.readTree(resp.body());
            com.fasterxml.jackson.databind.JsonNode cast = root.get("cast");
            java.util.List<java.util.Map<String,Object>> list = new java.util.ArrayList<>();
            if (cast != null && cast.isArray()) {
                int count = 0;
                for (com.fasterxml.jackson.databind.JsonNode n : cast) {
                    java.util.Map<String,Object> it = new java.util.HashMap<>();
                    it.put("name", n.path("name").asText());
                    it.put("character", n.path("character").asText());
                    String profile = n.path("profile_path").asText(null);
                    it.put("photo", profile == null ? null : ("https://image.tmdb.org/t/p/w185" + profile));
                    list.add(it);
                    count++;
                    if (count >= 12) break;
                }
            }
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            log.warn("Fetch credits failed: {}", e.getMessage());
            return ResponseEntity.ok(java.util.List.of());
        }
    }
}
