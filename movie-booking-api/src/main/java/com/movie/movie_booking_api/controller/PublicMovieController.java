package com.movie.movie_booking_api.controller;

import com.movie.movie_booking_api.entity.Movie;
import com.movie.movie_booking_api.repository.MovieRepository;
import com.movie.movie_booking_api.repository.ShowTimeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/movies")
public class PublicMovieController {
    private static final Logger log = LoggerFactory.getLogger(PublicMovieController.class);
    private final MovieRepository movieRepository;
    private final ShowTimeRepository showTimeRepository;
    private static final java.time.ZoneId ZONE = java.time.ZoneId.of("Asia/Ho_Chi_Minh");
    private static final java.time.format.DateTimeFormatter ISO_OFFSET = java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final java.time.Duration TMDB_TIMEOUT = java.time.Duration.ofSeconds(3);

    @Value("${tmdb.api-key:}")
    private String tmdbApiKey;

    @Value("${tmdb.read-access-token:}")
    private String tmdbReadAccessToken;

    public PublicMovieController(MovieRepository movieRepository, ShowTimeRepository showTimeRepository) {
        this.movieRepository = movieRepository;
        this.showTimeRepository = showTimeRepository;
    }

    private static class CacheEntry {
        final long expiresAt;
        final java.util.Map<String, Object> data;
        CacheEntry(long expiresAt, java.util.Map<String, Object> data){ this.expiresAt = expiresAt; this.data = data; }
    }
    private final java.util.Map<String, CacheEntry> cache = new java.util.concurrent.ConcurrentHashMap<>();

    @GetMapping("/{id}/cast")
    public ResponseEntity<?> cast(@PathVariable("id") Long id) {
        Movie m = movieRepository.findById(id).orElse(null);
        if (m == null) return ResponseEntity.status(404).body("NOT_FOUND");
        Long tmdbId = m.getTmdbId();
        List<Map<String, Object>> result = new ArrayList<>();
        result.addAll(fetchTmdbCastCamel(tmdbId));
        if (result.isEmpty()) {
            String castStr = m.getCast();
            if (castStr != null && !castStr.isBlank()) {
                String[] names = castStr.split(",");
                for (String raw : names) {
                    String name = raw.trim();
                    if (name.isEmpty()) continue;
                    Map<String, Object> it = new HashMap<>();
                    it.put("name", name);
                    it.put("character", "");
                    it.put("profilePath", null);
                    result.add(it);
                    if (result.size() >= 15) break;
                }
            }
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/config/imageBaseUrl")
    public ResponseEntity<?> imageBaseUrl() {
        String base = "https://image.tmdb.org/t/p/w500";
        return ResponseEntity.ok(base);
    }

    @GetMapping("/public/cast")
    public ResponseEntity<?> publicCast(@RequestParam("movieId") Long tmdbId) {
        List<Map<String, Object>> result = fetchTmdbCastSnake(tmdbId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/public/image-base")
    public ResponseEntity<?> publicImageBase() {
        return ResponseEntity.ok(Collections.singletonMap("baseUrl", "https://image.tmdb.org/t/p/w500"));
    }

    @GetMapping("/public/img")
    public ResponseEntity<?> proxyImage(@RequestParam("path") String path,
                                        @RequestParam(value = "size", defaultValue = "w500") String size) {
        try {
            if (path == null || path.isBlank()) return ResponseEntity.status(400).body("path required");
            String p = path.startsWith("/") ? path : ("/" + path);
            String base = "https://image.tmdb.org/t/p/" + (size == null || size.isBlank() ? "w500" : size);
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + p)).GET().build();
            java.net.http.HttpResponse<byte[]> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200) {
                String ct = resp.headers().firstValue("content-type").orElse("image/jpeg");
                return ResponseEntity.ok().header("Content-Type", ct).body(resp.body());
            } else {
                log.warn("Image proxy status={} path={}", resp.statusCode(), p);
                return ResponseEntity.status(404).build();
            }
        } catch (Exception e) {
            log.warn("Image proxy error: {}", e.getMessage());
            return ResponseEntity.status(502).build();
        }
    }

    @GetMapping("/public/detail")
    public ResponseEntity<?> publicDetail(@RequestParam("movieId") Long tmdbId) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        if (tmdbId == null) return ResponseEntity.ok(result);
        String cacheKey = "detail:" + tmdbId;
        long now = System.currentTimeMillis();
        CacheEntry ce = cache.get(cacheKey);
        if (ce != null && ce.expiresAt > now) return ResponseEntity.ok(ce.data);
        result.put("id", tmdbId);
        result.put("tmdbId", tmdbId);
        String base = "https://api.themoviedb.org/3/movie/" + tmdbId + "?language=vi-VN";
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(2))
                .build();

        double vote = 0.0;
        long voteCount = 0L;
        String releaseDate = null;
        Integer runtime = null;
        java.util.List<java.util.Map<String, Object>> genres = new java.util.ArrayList<>();
        java.util.List<String> companies = new java.util.ArrayList<>();
        java.util.List<String> countries = new java.util.ArrayList<>();
        String director = null;
        String posterPath = null;
        String backdropPath = null;
        String title = null;
        String overview = null;

        try {
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
            if (req != null) {
                java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                    com.fasterxml.jackson.databind.JsonNode root = om.readTree(resp.body());
                    vote = root.path("vote_average").asDouble(0);
                    voteCount = root.path("vote_count").asLong(0);
                    releaseDate = root.path("release_date").asText(null);
                    runtime = root.path("runtime").isNumber() ? root.path("runtime").asInt() : null;
                    posterPath = root.path("poster_path").asText(null);
                    backdropPath = root.path("backdrop_path").asText(null);
                    title = root.path("title").asText("");
                    overview = root.path("overview").asText("");
                    if (root.path("genres").isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode g : root.path("genres")) {
                            java.util.Map<String, Object> it = new java.util.HashMap<>();
                            it.put("id", g.path("id").asInt());
                            it.put("name", g.path("name").asText(""));
                            genres.add(it);
                        }
                    }
                    if (root.path("production_companies").isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode c : root.path("production_companies")) {
                            String name = c.path("name").asText("");
                            if (name != null && !name.isBlank()) companies.add(name);
                        }
                    }
                    if (root.path("production_countries").isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode c : root.path("production_countries")) {
                            String name = c.path("name").asText("");
                            if (name != null && !name.isBlank()) countries.add(name);
                        }
                    }
                } else {
                    log.warn("TMDB detail status={} body={}", resp.statusCode(), resp.body());
                }
            } else {
                log.warn("TMDB credentials missing for detail");
            }
        } catch (Exception e) {
            log.warn("TMDB detail error: {}", e.getMessage());
        }

        com.movie.movie_booking_api.entity.Movie m = movieRepository.findByTmdbId(tmdbId).orElse(null);
        if (director == null || director.isBlank()) {
            try {
                director = fetchTmdbDirectorName(tmdbId);
            } catch (Exception e) {
                log.warn("TMDB director error tmdbId={} msg={}", tmdbId, e.getMessage());
            }
        }
        if ((director == null || director.isBlank()) && m != null) {
            String d = m.getDirector();
            if (d != null && !d.isBlank()) director = d;
        }
        if ((title == null || title.isBlank()) && m != null) {
            String t = m.getTitleVi();
            if (t == null || t.isBlank()) t = m.getTitle();
            if (t == null || t.isBlank()) t = m.getTitleEn();
            if (t != null && !t.isBlank()) title = t;
        }
        if ((overview == null || overview.isBlank()) && m != null) {
            String o = m.getOverviewVi();
            if (o == null || o.isBlank()) o = m.getDescription();
            if (o == null || o.isBlank()) o = m.getOverviewEn();
            if (o != null && !o.isBlank()) overview = o;
        }
        if ((posterPath == null || posterPath.isBlank()) && m != null) {
            String pu = m.getPosterUrl();
            if (pu != null && !pu.isBlank()) {
                try {
                    java.net.URI u = java.net.URI.create(pu);
                    String s = u.getPath();
                    int idx = s.indexOf("/t/p/");
                    if (idx >= 0) {
                        int start = s.indexOf('/', idx + 5);
                        if (start >= 0 && start + 1 < s.length()) {
                            String part = s.substring(start + 1);
                            posterPath = part.startsWith("/") ? part : ("/" + part);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        if ((backdropPath == null || backdropPath.isBlank()) && m != null) {
            String bu = m.getBackdropUrl();
            if (bu != null && !bu.isBlank()) {
                try {
                    java.net.URI u = java.net.URI.create(bu);
                    String s = u.getPath();
                    int idx = s.indexOf("/t/p/");
                    if (idx >= 0) {
                        int start = s.indexOf('/', idx + 5);
                        if (start >= 0 && start + 1 < s.length()) {
                            String part = s.substring(start + 1);
                            backdropPath = part.startsWith("/") ? part : ("/" + part);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        if (runtime == null || runtime <= 0) {
            Integer rt = null;
            if (m != null) {
                if (m.getRuntime() != null && m.getRuntime() > 0) rt = m.getRuntime();
                else if (m.getDurationMinutes() != null && m.getDurationMinutes() > 0) rt = m.getDurationMinutes();
                if (rt == null) {
                    java.util.List<com.movie.movie_booking_api.entity.ShowTime> sts = showTimeRepository.findByMovieId(m.getId());
                    if (!sts.isEmpty() && sts.get(0).getDurationMinutes() != null && sts.get(0).getDurationMinutes() > 0) {
                        rt = sts.get(0).getDurationMinutes();
                    }
                }
            } else {
                java.util.List<com.movie.movie_booking_api.entity.ShowTime> sts = showTimeRepository.findByMovieTmdbId(tmdbId);
                if (!sts.isEmpty() && sts.get(0).getDurationMinutes() != null && sts.get(0).getDurationMinutes() > 0) {
                    rt = sts.get(0).getDurationMinutes();
                }
            }
            if (rt != null && rt > 0) runtime = rt;
        }
        if (genres.isEmpty() && m != null) {
            java.util.List<String> gs = m.getGenre() == null ? java.util.List.of() : java.util.Arrays.stream(m.getGenre().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            for (String name : gs) { java.util.Map<String, Object> it = new java.util.HashMap<>(); it.put("id", 0); it.put("name", name); genres.add(it); }
        }

        if (vote > 0) { result.put("vote_average", vote); result.put("vote", vote); } else { result.put("vote", 0.0); }
        if (voteCount > 0) { result.put("vote_count", voteCount); }
        if (releaseDate != null && !releaseDate.isBlank()) { result.put("release_date", releaseDate); result.put("date", releaseDate); }
        if (runtime != null && runtime > 0) result.put("runtime", runtime);
        result.put("genres", genres);
        if (!companies.isEmpty()) result.put("companies", companies);
        if (!countries.isEmpty()) result.put("countries", countries);
        if (director != null && !director.isBlank()) result.put("director", director);
        if (posterPath != null && !posterPath.isBlank()) result.put("poster_path", posterPath);
        if (backdropPath != null && !backdropPath.isBlank()) result.put("backdrop_path", backdropPath);
        if (title != null && !title.isBlank()) result.put("title", title);
        if (overview != null && !overview.isBlank()) result.put("overview", overview);
        if (result.size() > 2) {
            cache.put(cacheKey, new CacheEntry(now + 10 * 60_000L, result));
        }
        return ResponseEntity.ok(result);
    }

    private String fetchTmdbDirectorName(Long tmdbId) throws Exception {
        if (tmdbId == null) return null;
        String urlKey = "https://api.themoviedb.org/3/movie/" + tmdbId + "/credits?language=vi-VN";
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(2))
                .build();
        java.net.http.HttpRequest req = null;
        if (tmdbApiKey != null && !tmdbApiKey.isBlank()) {
            String endpoint = urlKey + "&api_key=" + URLEncoder.encode(tmdbApiKey, StandardCharsets.UTF_8);
            req = java.net.http.HttpRequest.newBuilder(URI.create(endpoint)).timeout(TMDB_TIMEOUT).GET().build();
        } else if (tmdbReadAccessToken != null && !tmdbReadAccessToken.isBlank()) {
            req = java.net.http.HttpRequest.newBuilder(URI.create(urlKey))
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
            if (!job.toLowerCase(Locale.ROOT).contains("director")) continue;
            String name = n.path("name").asText("");
            if (name != null && !name.isBlank()) names.add(name);
        }
        if (names.isEmpty()) return null;
        return String.join(", ", names);
    }

    @GetMapping("/public/videos")
    public ResponseEntity<?> publicVideos(@RequestParam("movieId") Long tmdbId) {
        java.util.Map<String, Object> out = new java.util.HashMap<>();
        if (tmdbId == null) return ResponseEntity.ok(out);
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(2))
                .build();
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        java.util.List<String> endpoints = java.util.List.of(
                "https://api.themoviedb.org/3/movie/" + tmdbId + "/videos?language=vi-VN",
                "https://api.themoviedb.org/3/movie/" + tmdbId + "/videos?language=en-US"
        );
        for (String base : endpoints) {
            try {
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
                if (req == null) continue;
                java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                    com.fasterxml.jackson.databind.JsonNode root = om.readTree(resp.body());
                    com.fasterxml.jackson.databind.JsonNode results = root.get("results");
                    if (results != null && results.isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode n : results) {
                            String site = n.path("site").asText("");
                            String key = n.path("key").asText(null);
                            String type = n.path("type").asText("");
                            boolean official = n.path("official").asBoolean(false);
                            String published = n.path("published_at").asText("");
                            if ("YouTube".equalsIgnoreCase(site) && key != null && !key.isBlank()) {
                                java.util.Map<String, Object> it = new java.util.HashMap<>();
                                it.put("key", key);
                                it.put("type", type);
                                it.put("official", official);
                                it.put("published_at", published);
                                list.add(it);
                            }
                        }
                    }
                    if (!list.isEmpty()) break;
                } else {
                    log.warn("TMDB videos non-200 status={} tmdbId={}", resp.statusCode(), tmdbId);
                }
            } catch (Exception e) {
                log.warn("TMDB videos error tmdbId={} msg={}", tmdbId, e.getMessage());
            }
        }
        list.sort(java.util.Comparator
                .comparing((java.util.Map<String, Object> it) -> !"Trailer".equalsIgnoreCase(String.valueOf(it.get("type"))))
                .thenComparing(it -> !(java.lang.Boolean.TRUE.equals(it.get("official"))))
                .thenComparing((java.util.Map<String, Object> it) -> String.valueOf(it.get("published_at")), java.util.Comparator.reverseOrder())
        );
        if (!list.isEmpty()) {
            out.put("key", list.get(0).get("key"));
            java.util.List<String> keys = new java.util.ArrayList<>();
            for (java.util.Map<String, Object> it : list) keys.add(String.valueOf(it.get("key")));
            out.put("keys", keys);
        }
        return ResponseEntity.ok(out);
    }

    @GetMapping("/public/tmdb/now-playing")
    public ResponseEntity<?> tmdbNowPlaying(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "language", defaultValue = "vi-VN") String language,
            @RequestParam(value = "region", required = false) String region
    ) {
        String lang = (language == null || language.isBlank()) ? "vi-VN" : language.trim();
        String reg = region == null ? "" : region.trim();
        String key = "now_playing:" + lang + ":" + reg + ":" + page;
        long now = System.currentTimeMillis();
        CacheEntry ce = cache.get(key);
        if (ce != null && ce.expiresAt > now) return ResponseEntity.ok(ce.data);
        java.util.Map<String, Object> result = fetchTmdbListObj("now_playing", page, lang, reg);
        java.util.List<?> results = (java.util.List<?>) result.getOrDefault("results", java.util.List.of());
        if (!results.isEmpty()) {
            cache.put(key, new CacheEntry(now + 10 * 60_000L, result));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/public/tmdb/upcoming")
    public ResponseEntity<?> tmdbUpcoming(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "language", defaultValue = "vi-VN") String language,
            @RequestParam(value = "region", required = false) String region
    ) {
        String lang = (language == null || language.isBlank()) ? "vi-VN" : language.trim();
        String reg = region == null ? "" : region.trim();
        String key = "upcoming:" + lang + ":" + reg + ":" + page;
        long now = System.currentTimeMillis();
        CacheEntry ce = cache.get(key);
        if (ce != null && ce.expiresAt > now) return ResponseEntity.ok(ce.data);
        java.util.Map<String, Object> result = fetchTmdbListObj("upcoming", page, lang, reg);
        java.util.List<?> results = (java.util.List<?>) result.getOrDefault("results", java.util.List.of());
        if (!results.isEmpty()) {
            cache.put(key, new CacheEntry(now + 10 * 60_000L, result));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/public/resolve")
    public ResponseEntity<?> resolve(@RequestParam("tmdbId") Long tmdbId) {
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        if (tmdbId == null) return ResponseEntity.ok(resp);
        java.util.Optional<com.movie.movie_booking_api.entity.Movie> opt = movieRepository.findByTmdbId(tmdbId);
        if (opt.isPresent()) {
            Long internalId = opt.get().getId();
            resp.put("internalId", internalId);
            java.util.List<com.movie.movie_booking_api.entity.ShowTime> sts = showTimeRepository.findByMovieId(internalId);
            long count = sts.stream().filter(st -> st.getDisabled() == null || !st.getDisabled()).count();
            resp.put("showtimes", count);
        }
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/public/showtimes")
    public ResponseEntity<?> publicShowtimes(@RequestParam("movieId") Long internalId) {
        java.util.List<com.movie.movie_booking_api.entity.ShowTime> sts = showTimeRepository.findByMovieId(internalId);
        java.util.List<java.util.Map<String, Object>> arr = new java.util.ArrayList<>();
        for (com.movie.movie_booking_api.entity.ShowTime st : sts) {
            if (st.getDisabled() != null && st.getDisabled()) continue;
            java.util.Map<String, Object> it = new java.util.HashMap<>();
            it.put("id", st.getId());
            it.put("movieId", st.getMovieId());
            it.put("movieTitle", st.getMovieTitle());
            it.put("startTime", st.getStartTime() == null ? null : st.getStartTime().atZone(ZONE).toOffsetDateTime().format(ISO_OFFSET));
            it.put("durationMinutes", st.getDurationMinutes());
            it.put("price", st.getPrice());
            it.put("currency", st.getCurrency());
            it.put("cinema", st.getCinema());
            it.put("room", st.getRoom());
            arr.add(it);
        }
        return ResponseEntity.ok(arr);
    }

    private java.util.Map<String, Object> fetchTmdbListObj(String type, int page, String language, String region) {
        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.put("page", page);
        out.put("results", new java.util.ArrayList<java.util.Map<String, Object>>());
        out.put("total_pages", 0);
        out.put("total_results", 0);
        String lang = (language == null || language.isBlank()) ? "vi-VN" : language.trim();
        String reg = region == null ? "" : region.trim();
        String base = "https://api.themoviedb.org/3/movie/" + type
                + "?language=" + URLEncoder.encode(lang, StandardCharsets.UTF_8)
                + "&page=" + page
                + (reg.isBlank() ? "" : ("&region=" + URLEncoder.encode(reg, StandardCharsets.UTF_8)));
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(2))
                .build();
        try {
            java.net.http.HttpRequest req;
            if (tmdbApiKey != null && !tmdbApiKey.isBlank()) {
                String endpoint = base + "&api_key=" + java.net.URLEncoder.encode(tmdbApiKey, java.nio.charset.StandardCharsets.UTF_8);
                req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(endpoint)).timeout(TMDB_TIMEOUT).GET().build();
            } else if (tmdbReadAccessToken != null && !tmdbReadAccessToken.isBlank()) {
                req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base)).header("Authorization", "Bearer " + tmdbReadAccessToken).timeout(TMDB_TIMEOUT).GET().build();
            } else {
                return out;
            }
            java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = om.readTree(resp.body());
                out.put("total_pages", root.path("total_pages").asInt(0));
                out.put("total_results", root.path("total_results").asInt(0));
                com.fasterxml.jackson.databind.JsonNode results = root.get("results");
                java.util.List<java.util.Map<String, Object>> arr = (java.util.List<java.util.Map<String, Object>>) out.get("results");
                if (results != null && results.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode n : results) {
                        java.util.Map<String, Object> it = new java.util.HashMap<>();
                        it.put("id", n.path("id").asLong());
                        it.put("title", n.path("title").asText(""));
                        it.put("overview", n.path("overview").asText(""));
                        String posterPath = n.path("poster_path").asText(null);
                        String backdropPath = n.path("backdrop_path").asText(null);
                        if (posterPath != null && !posterPath.isBlank()) it.put("poster_path", posterPath);
                        if (backdropPath != null && !backdropPath.isBlank()) it.put("backdrop_path", backdropPath);
                        double vote = n.path("vote_average").asDouble(0);
                        if (vote > 0) it.put("vote_average", vote);
                        String releaseDate = n.path("release_date").asText(null);
                        if (releaseDate != null && !releaseDate.isBlank()) it.put("release_date", releaseDate);
                        arr.add(it);
                    }
                }
            } else {
                // non-200: return empty object, don't cache
                log.warn("TMDB {} non-200 status={} body={}...", type, resp.statusCode(), resp.body() == null ? "" : resp.body().substring(0, Math.min(120, resp.body().length())));
            }
        } catch (Exception e) {
            log.warn("TMDB {} error: {}", type, e.getMessage());
        }
        return out;
    }

    @GetMapping("/public/now-playing")
    @org.springframework.cache.annotation.Cacheable(value = "public_now_playing", unless = "#result == null")
    public ResponseEntity<?> publicNowPlaying() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime soon = now.plusDays(7);
        java.util.List<Long> movieIds = showTimeRepository.findDistinctMovieIdsBetween(now.minusHours(3), soon);
        java.util.Set<Long> uniq = new java.util.LinkedHashSet<>(movieIds);
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (Long mid : uniq) {
            com.movie.movie_booking_api.entity.Movie m = movieRepository.findById(mid).orElse(null);
            if (m == null) continue;
            java.util.List<com.movie.movie_booking_api.entity.ShowTime> sts = showTimeRepository.findByMovieId(mid);
            java.time.LocalDateTime first = null;
            for (com.movie.movie_booking_api.entity.ShowTime st : sts) {
                if (st.getDisabled() != null && st.getDisabled()) continue;
                if (first == null || st.getStartTime().isBefore(first)) first = st.getStartTime();
            }
            java.util.Map<String, Object> it = new java.util.HashMap<>();
            Long tmdbId = m.getTmdbId();
            it.put("id", tmdbId != null ? tmdbId : m.getId());
            it.put("internalId", m.getId());
            it.put("title", m.getTitleVi() != null && !m.getTitleVi().isBlank() ? m.getTitleVi() : m.getTitle());
            it.put("overview", m.getOverviewVi() != null && !m.getOverviewVi().isBlank() ? m.getOverviewVi() : m.getDescription());
            String poster = m.getPosterUrl();
            String backdrop = m.getBackdropUrl();
            String pp = toTmdbPath(poster);
            String bp = toTmdbPath(backdrop);
            if (pp != null) it.put("poster_path", pp);
            if (bp != null) it.put("backdrop_path", bp);
            if (first != null) it.put("release_date", first.toLocalDate().toString());
            result.add(it);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/public/upcoming")
    @org.springframework.cache.annotation.Cacheable(value = "public_upcoming", unless = "#result == null")
    public ResponseEntity<?> publicUpcoming() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime threshold = now.plusDays(7);
        java.util.List<Long> movieIds = showTimeRepository.findDistinctMovieIdsAfter(threshold);
        java.util.Set<Long> uniq = new java.util.LinkedHashSet<>(movieIds);
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (Long mid : uniq) {
            com.movie.movie_booking_api.entity.Movie m = movieRepository.findById(mid).orElse(null);
            if (m == null) continue;
            java.util.List<com.movie.movie_booking_api.entity.ShowTime> sts = showTimeRepository.findByMovieId(mid);
            java.time.LocalDateTime first = null;
            for (com.movie.movie_booking_api.entity.ShowTime st : sts) {
                if (st.getDisabled() != null && st.getDisabled()) continue;
                if (first == null || st.getStartTime().isBefore(first)) first = st.getStartTime();
            }
            java.util.Map<String, Object> it = new java.util.HashMap<>();
            Long tmdbId = m.getTmdbId();
            it.put("id", tmdbId != null ? tmdbId : m.getId());
            it.put("internalId", m.getId());
            it.put("title", m.getTitleVi() != null && !m.getTitleVi().isBlank() ? m.getTitleVi() : m.getTitle());
            it.put("overview", m.getOverviewVi() != null && !m.getOverviewVi().isBlank() ? m.getOverviewVi() : m.getDescription());
            String poster = m.getPosterUrl();
            String backdrop = m.getBackdropUrl();
            String pp = toTmdbPath(poster);
            String bp = toTmdbPath(backdrop);
            if (pp != null) it.put("poster_path", pp);
            if (bp != null) it.put("backdrop_path", bp);
            if (first != null) it.put("release_date", first.toLocalDate().toString());
            result.add(it);
        }
        return ResponseEntity.ok(result);
    }

    private String toTmdbPath(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            java.net.URI u = java.net.URI.create(url);
            String p = u.getPath();
            if (p == null || p.isBlank()) return null;
            int idx = p.indexOf("/t/p/");
            if (idx >= 0) {
                String sub = p.substring(idx + 5);
                int slash = sub.indexOf('/');
                if (slash >= 0) return sub.substring(slash + 1);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private List<Map<String, Object>> fetchTmdbCastSnake(Long tmdbId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (tmdbId == null) { log.warn("Missing tmdbId in public cast request"); return result; }
        String urlKey = "https://api.themoviedb.org/3/movie/" + tmdbId + "/credits?language=vi-VN";
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        try {
            if (tmdbApiKey != null && !tmdbApiKey.isBlank()) {
                String endpoint = urlKey + "&api_key=" + URLEncoder.encode(tmdbApiKey, StandardCharsets.UTF_8);
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(URI.create(endpoint)).GET().build();
                java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) return parseCastSnake(resp.body());
                log.warn("TMDB credits non-200 for tmdbId={} status={}", tmdbId, resp.statusCode());
            }
            if (tmdbReadAccessToken != null && !tmdbReadAccessToken.isBlank()) {
                java.net.http.HttpRequest req2 = java.net.http.HttpRequest.newBuilder(URI.create(urlKey))
                        .header("Authorization", "Bearer " + tmdbReadAccessToken)
                        .GET().build();
                java.net.http.HttpResponse<String> resp2 = client.send(req2, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (resp2.statusCode() == 200) return parseCastSnake(resp2.body());
                log.warn("TMDB credits non-200 (bearer) for tmdbId={} status={}", tmdbId, resp2.statusCode());
            }
        } catch (Exception e) { log.warn("TMDB credits error for tmdbId={} msg={}", tmdbId, e.getMessage()); }
        return result;
    }

    private List<Map<String, Object>> parseCastSnake(String body) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode root = om.readTree(body);
        com.fasterxml.jackson.databind.JsonNode cast = root.get("cast");
        List<Map<String, Object>> tmp = new ArrayList<>();
        if (cast != null && cast.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode n : cast) {
                Map<String, Object> it = new HashMap<>();
                String name = n.path("name").asText("");
                if (name.isBlank()) continue;
                it.put("name", name);
                it.put("character", n.path("character").asText(""));
                String p = n.path("profile_path").asText(null);
                it.put("profile_path", p);
                it.put("order", n.path("order").asInt(999));
                it.put("popularity", n.path("popularity").asDouble(0));
                tmp.add(it);
            }
        }
        tmp.sort(Comparator
                .comparingInt((Map<String, Object> it) -> (Integer) it.get("order"))
                .thenComparingDouble(it -> (Double) it.get("popularity")).reversed());
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < Math.min(tmp.size(), 15); i++) {
            Map<String, Object> it = new HashMap<>();
            it.put("name", tmp.get(i).get("name"));
            it.put("character", tmp.get(i).get("character"));
            it.put("profile_path", tmp.get(i).get("profile_path"));
            result.add(it);
        }
        return result;
    }

    private List<Map<String, Object>> fetchTmdbCastCamel(Long tmdbId) {
        List<Map<String, Object>> snake = fetchTmdbCastSnake(tmdbId);
        List<Map<String, Object>> camel = new ArrayList<>();
        for (Map<String, Object> s : snake) {
            Map<String, Object> it = new HashMap<>();
            it.put("name", s.get("name"));
            it.put("character", s.get("character"));
            it.put("profilePath", s.get("profile_path"));
            camel.add(it);
        }
        return camel;
    }
}
