package com.movie.movie_booking_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
@EnableScheduling
@org.springframework.scheduling.annotation.EnableAsync
@org.springframework.cache.annotation.EnableCaching
public class MovieBookingApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MovieBookingApiApplication.class, args);
    }

    @Component
    public static class AutoSyncTask {
        private static final Logger log = LoggerFactory.getLogger(AutoSyncTask.class);
        @Value("${tmdb.api-key:}")
        private String tmdbApiKey;
        @Autowired
        private com.movie.movie_booking_api.repository.MovieRepository movieRepository;

        @Scheduled(initialDelay = 5000, fixedDelay = 21600000)
        public void sync() {
            String apiKey = tmdbApiKey == null ? "" : tmdbApiKey.trim();
            if (apiKey.isEmpty()) return;
            long t0 = System.currentTimeMillis();
            int total = 0;
            try {
                java.util.Set<Long> existingTmdbIds = movieRepository.findAll().stream()
                        .map(m -> m.getTmdbId() == null ? 0L : m.getTmdbId())
                        .filter(id -> id != 0L)
                        .collect(java.util.stream.Collectors.toSet());
                total += importPage(apiKey, "now_playing", "vi-VN", 1, existingTmdbIds);
                total += importPage(apiKey, "upcoming", "vi-VN", 1, existingTmdbIds);
                total += importPage(apiKey, "now_playing", "vi-VN", 2, existingTmdbIds);
                total += importPage(apiKey, "upcoming", "vi-VN", 2, existingTmdbIds);
            } catch (Exception e) {
                log.error("Auto TMDB sync failed", e);
            } finally {
                long t1 = System.currentTimeMillis();
                log.info("Auto TMDB sync imported={} elapsed={}ms", total, (t1 - t0));
            }
        }

        private int importPage(String apiKey, String type, String lang, int page, java.util.Set<Long> existingTmdbIds) throws Exception {
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
            if (resp.statusCode() != 200) return 0;
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = om.readTree(resp.body());
            com.fasterxml.jackson.databind.JsonNode results = root.get("results");
            if (results == null || !results.isArray()) return 0;
            java.util.List<com.movie.movie_booking_api.entity.Movie> toSave = new java.util.ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode n : results) {
                Long tmdbId = n.path("id").asLong(0L);
                if (tmdbId == null || tmdbId == 0L) continue;
                if (existingTmdbIds.contains(tmdbId) || movieRepository.findByTmdbId(tmdbId).isPresent()) continue;
                String title = n.path("title").asText(null);
                String overview = n.path("overview").asText(null);
                String originalLanguage = n.path("original_language").asText(null);
                String posterPath = n.path("poster_path").asText(null);
                com.movie.movie_booking_api.entity.MovieStatus st;
                String t = type.toLowerCase();
                if ("now_playing".equals(t)) st = com.movie.movie_booking_api.entity.MovieStatus.NOW_SHOWING;
                else if ("upcoming".equals(t)) st = com.movie.movie_booking_api.entity.MovieStatus.UPCOMING;
                else st = com.movie.movie_booking_api.entity.MovieStatus.NOW_SHOWING;
                com.movie.movie_booking_api.entity.Movie m = com.movie.movie_booking_api.entity.Movie.builder()
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

        @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
        public void runOnStartup() {
            try {
                sync();
            } catch (Exception ignored) {}
        }

        @jakarta.annotation.PostConstruct
        public void postConstructSync() {
            try { sync(); } catch (Exception ignored) {}
        }
    }

    @Component
    public static class ShowTimeStatusTask {
        private static final Logger log = LoggerFactory.getLogger(ShowTimeStatusTask.class);
        @Autowired
        private com.movie.movie_booking_api.repository.ShowTimeRepository showTimeRepository;
        @Scheduled(initialDelay = 10000, fixedDelay = 300000)
        @org.springframework.cache.annotation.CacheEvict(value = "showtimes", allEntries = true)
        @org.springframework.transaction.annotation.Transactional
        public void sync() {
            int upcoming = showTimeRepository.markUpcoming();
            int active = showTimeRepository.markActive();
            int finished = showTimeRepository.markFinished();
            if (upcoming + active + finished > 0) {
                log.info("Showtime status sync updated: upcoming={}, active={}, finished={}", upcoming, active, finished);
            }
        }
    }
}
