package com.movie.movie_booking_api.controller;

import com.movie.movie_booking_api.entity.Movie;
import com.movie.movie_booking_api.entity.MovieStatus;
import com.movie.movie_booking_api.entity.ShowTime;
import com.movie.movie_booking_api.repository.ShowTimeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.*;

@RestController
@RequestMapping("/api/admin/showtimes")
@RequiredArgsConstructor
@Slf4j
public class AdminShowTimeController {

    private final ShowTimeRepository showTimeRepository;
    private final com.movie.movie_booking_api.repository.MovieRepository movieRepository;
    private final com.movie.movie_booking_api.repository.BookingRepository bookingRepository;
    private final com.movie.movie_booking_api.repository.SeatRepository seatRepository;

    @org.springframework.beans.factory.annotation.Value("${tmdb.api-key:}")
    private String tmdbApiKey;

    @org.springframework.beans.factory.annotation.Value("${tmdb.read-access-token:}")
    private String tmdbReadAccessToken;

    private static final java.time.format.DateTimeFormatter ISO_LOCAL = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final java.time.format.DateTimeFormatter ISO_DATE = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
    private static final java.time.format.DateTimeFormatter ISO_TIME = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
    private static final java.time.format.DateTimeFormatter MDY_DATE = java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy");
    private static final java.time.format.DateTimeFormatter DMY_DATE = java.time.format.DateTimeFormatter.ofPattern("d/M/yyyy");
    private static final java.time.format.DateTimeFormatter TIME_12H = new java.time.format.DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("h:mm a")
            .toFormatter(java.util.Locale.ENGLISH);

    private String toLocalString(java.time.LocalDateTime dt){
        return dt == null ? null : dt.format(ISO_LOCAL);
    }

    private static String asString(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }

    private static Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private static Boolean asBool(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        String s = String.valueOf(v).trim();
        if (s.isBlank()) return null;
        if ("true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(s) || "0".equals(s) || "no".equalsIgnoreCase(s)) return Boolean.FALSE;
        return null;
    }

    private static List<String> asStringList(Object v) {
        if (v == null) return List.of();
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object it : list) {
                String s = asString(it);
                if (s != null) out.add(s);
            }
            return out;
        }
        String s = asString(v);
        return s == null ? List.of() : List.of(s);
    }

    private static List<Long> asLongList(Object v) {
        if (v == null) return List.of();
        if (v instanceof List<?> list) {
            List<Long> out = new ArrayList<>();
            for (Object it : list) {
                Long x = asLong(it);
                if (x != null) out.add(x);
            }
            return out;
        }
        Long x = asLong(v);
        return x == null ? List.of() : List.of(x);
    }

    private static int intOrDefault(Integer v, int d) { return v == null ? d : v; }

    private static LocalTime parseTimeOrDefault(String s, LocalTime d) {
        if (s == null) return d;
        String raw = s.trim();
        try { return LocalTime.parse(raw, ISO_TIME); } catch (Exception ignored) {}
        try { return LocalTime.parse(raw.replaceAll("\\s+", " "), TIME_12H); } catch (Exception ignored) {}
        return d;
    }

    private static LocalDate parseDateOrDefault(String s, LocalDate d) {
        if (s == null) return d;
        String raw = s.trim();
        try { return LocalDate.parse(raw, ISO_DATE); } catch (Exception ignored) {}
        try { return LocalDate.parse(raw, MDY_DATE); } catch (Exception ignored) {}
        try { return LocalDate.parse(raw, DMY_DATE); } catch (Exception ignored) {}
        return d;
    }

    private record MovieCandidate(Long id, Long tmdbId, String title, int durationMinutes, MovieStatus status, int priorityScore, Integer revenueRank) {}

    private int resolveDurationMinutes(Movie m, int defaultDurationMinutes) {
        Integer dur = m.getDurationMinutes();
        if (dur != null && dur > 0) return dur;
        Integer rt = m.getRuntime();
        if (rt != null && rt > 0) return rt;
        return defaultDurationMinutes;
    }

    private List<MovieCandidate> loadCandidates(List<Long> movieIds, int defaultDurationMinutes, String revenueRange) {
        List<Movie> movies;
        if (movieIds != null && !movieIds.isEmpty()) {
            movies = movieRepository.findAllById(movieIds);
        } else {
            List<Movie> now = movieRepository.findByStatus(MovieStatus.NOW_SHOWING);
            List<Movie> up = movieRepository.findByStatus(MovieStatus.UPCOMING);
            movies = new ArrayList<>(now.size() + up.size());
            movies.addAll(now);
            movies.addAll(up);
        }

        Map<Long, Integer> revenueRankByMovieId = new HashMap<>();
        try {
            List<Object[]> rows = "today".equalsIgnoreCase(revenueRange)
                    ? movieRepository.topMoviesTodayByRevenue()
                    : movieRepository.topMoviesMonthByRevenue();
            int rank = 0;
            for (Object[] row : rows) {
                Long mid = row == null || row.length == 0 ? null : asLong(row[0]);
                if (mid == null) continue;
                revenueRankByMovieId.put(mid, ++rank);
            }
        } catch (Exception e) {
            revenueRankByMovieId = Map.of();
        }

        LocalDateTime now = LocalDateTime.now();
        List<MovieCandidate> out = new ArrayList<>();
        for (Movie m : movies) {
            if (m == null || m.getId() == null) continue;
            MovieStatus st = m.getStatus();
            int base = (st == MovieStatus.NOW_SHOWING) ? 1000 : (st == MovieStatus.UPCOMING) ? 500 : 0;
            Integer revenueRank = revenueRankByMovieId.get(m.getId());
            int revenueBonus = revenueRank == null ? 0 : Math.max(0, 110 - (revenueRank * 10));
            int recencyBonus = 0;
            if (m.getCreatedAt() != null) {
                long days = java.time.Duration.between(m.getCreatedAt(), now).toDays();
                recencyBonus = (int) Math.max(0, 50 - days);
            }
            int score = base + revenueBonus + recencyBonus;
            out.add(new MovieCandidate(
                    m.getId(),
                    m.getTmdbId(),
                    m.getTitle(),
                    resolveDurationMinutes(m, defaultDurationMinutes),
                    st,
                    score,
                    revenueRank
            ));
        }

        out.sort((a, b) -> Integer.compare(b.priorityScore(), a.priorityScore()));
        return out;
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) { return (a == null) ? b : (b == null) ? a : (a.isAfter(b) ? a : b); }

    private static LocalDateTime endOf(ShowTime st) {
        if (st == null || st.getStartTime() == null) return null;
        Integer dur = st.getDurationMinutes();
        if (dur == null || dur <= 0) return st.getStartTime();
        return st.getStartTime().plusMinutes(dur);
    }

    private MovieStatus statusFromShowtimeTime(LocalDateTime startTime) {
        if (startTime == null) return MovieStatus.NOW_SHOWING;
        LocalDateTime now = LocalDateTime.now();
        return startTime.isAfter(now.plusDays(7)) ? MovieStatus.UPCOMING : MovieStatus.NOW_SHOWING;
    }

    private Movie importTmdbMovieIfMissing(Long tmdbId, LocalDateTime startTime) {
        if (tmdbId == null) return null;
        Movie existing = movieRepository.findByTmdbId(tmdbId).orElse(null);
        if (existing != null) return existing;
        try {
            String base = "https://api.themoviedb.org/3/movie/" + tmdbId + "?language=vi-VN";
            java.net.http.HttpRequest req;
            if (tmdbApiKey != null && !tmdbApiKey.isBlank()) {
                String endpoint = base + "&api_key=" + java.net.URLEncoder.encode(tmdbApiKey, java.nio.charset.StandardCharsets.UTF_8);
                req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(endpoint)).GET().build();
            } else if (tmdbReadAccessToken != null && !tmdbReadAccessToken.isBlank()) {
                req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base))
                        .header("Authorization", "Bearer " + tmdbReadAccessToken)
                        .GET().build();
            } else {
                return null;
            }
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = om.readTree(resp.body());
            String title = root.path("title").asText(null);
            String overview = root.path("overview").asText(null);
            String originalLanguage = root.path("original_language").asText(null);
            int runtimeRaw = root.path("runtime").asInt(0);
            Integer runtime = runtimeRaw > 0 ? runtimeRaw : null;
            String posterPath = root.path("poster_path").asText(null);
            String backdropPath = root.path("backdrop_path").asText(null);

            Movie m = Movie.builder()
                    .tmdbId(tmdbId)
                    .movieIdLegacy("0")
                    .title(title == null || title.isBlank() ? ("TMDB " + tmdbId) : title)
                    .description(overview)
                    .language(originalLanguage)
                    .runtime(runtime)
                    .posterUrl(posterPath == null || posterPath.isBlank() ? null : ("https://image.tmdb.org/t/p/w500" + posterPath))
                    .backdropUrl(backdropPath == null || backdropPath.isBlank() ? null : ("https://image.tmdb.org/t/p/w780" + backdropPath))
                    .status(statusFromShowtimeTime(startTime))
                    .createdAt(java.time.LocalDateTime.now())
                    .updatedAt(java.time.LocalDateTime.now())
                    .build();
            return movieRepository.save(m);
        } catch (Exception e) {
            return null;
        }
    }

    private void resolveMovieIds(ShowTime payload) {
        if (payload == null) return;
        if (payload.getMovieId() != null) {
            Movie m = movieRepository.findById(payload.getMovieId()).orElse(null);
            if (m != null) {
                if (payload.getMovieTmdbId() == null && m.getTmdbId() != null) payload.setMovieTmdbId(m.getTmdbId());
                if (payload.getMovieTitle() == null || payload.getMovieTitle().isBlank()) payload.setMovieTitle(m.getTitle());
            }
            return;
        }
        Long tmdbId = payload.getMovieTmdbId();
        if (tmdbId == null) return;
        Movie m = movieRepository.findByTmdbId(tmdbId).orElse(null);
        if (m == null) m = importTmdbMovieIfMissing(tmdbId, payload.getStartTime());
        if (m != null && m.getId() != null) {
            payload.setMovieId(m.getId());
            if (payload.getMovieTitle() == null || payload.getMovieTitle().isBlank()) payload.setMovieTitle(m.getTitle());
        }
    }

    private record TimeRange(LocalTime start, LocalTime end) {}
    private record SlotScore(int bonus, String tag, boolean inGolden) {}

    private static TimeRange parseTimeRange(String s) {
        if (s == null) return null;
        String raw = s.trim();
        int idx = raw.indexOf('-');
        if (idx <= 0 || idx >= raw.length() - 1) return null;
        String a = raw.substring(0, idx).trim();
        String b = raw.substring(idx + 1).trim();
        try {
            LocalTime start = LocalTime.parse(a, ISO_TIME);
            LocalTime end = LocalTime.parse(b, ISO_TIME);
            if (!end.isAfter(start)) return null;
            return new TimeRange(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<TimeRange> parseGoldenRanges(Object v) {
        List<String> raw = asStringList(v);
        List<TimeRange> out = new ArrayList<>();
        for (String s : raw) {
            TimeRange tr = parseTimeRange(s);
            if (tr != null) out.add(tr);
        }
        if (!out.isEmpty()) return out;
        out.add(new TimeRange(LocalTime.of(18, 0), LocalTime.of(21, 30)));
        return out;
    }

    private static Set<DayOfWeek> parseDays(Object v) {
        List<String> raw = asStringList(v);
        Set<DayOfWeek> out = EnumSet.noneOf(DayOfWeek.class);
        for (String s : raw) {
            if (s == null) continue;
            try { out.add(DayOfWeek.valueOf(s.trim().toUpperCase())); } catch (Exception ignored) {}
        }
        if (!out.isEmpty()) return out;
        return EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    }

    private static boolean isInRange(LocalTime t, TimeRange r) {
        return (t.equals(r.start()) || t.isAfter(r.start())) && t.isBefore(r.end());
    }

    private static SlotScore scoreSlot(LocalDateTime start, int durationMinutes, List<TimeRange> goldenRanges, Set<DayOfWeek> goldenDays) {
        LocalTime t = start.toLocalTime();
        DayOfWeek dow = start.getDayOfWeek();

        boolean inGolden = false;
        for (TimeRange r : goldenRanges) {
            if (isInRange(t, r)) { inGolden = true; break; }
        }

        boolean goldenDay = goldenDays.contains(dow);
        boolean isWeekend = (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY);
        boolean isFriday = (dow == DayOfWeek.FRIDAY);

        int bonus = 0;
        if (isWeekend) bonus += 120;
        else if (isFriday) bonus += 60;

        int hour = t.getHour();
        if (hour >= 18 && hour <= 21) bonus += 160;
        else if (hour >= 12 && hour <= 14) bonus += 60;
        else if (hour >= 9 && hour <= 11) bonus += 20;
        else if (hour >= 22) bonus -= 30;
        else if (hour < 8) bonus -= 60;

        if (inGolden) bonus += goldenDay ? 280 : 230;

        if (inGolden && durationMinutes >= 150) bonus -= 80;
        if (inGolden && durationMinutes > 0 && durationMinutes < 90) bonus -= 30;

        String tag = "NORMAL";
        if (inGolden && (isWeekend || isFriday)) tag = "GOLDEN_WEEKEND";
        else if (inGolden) tag = "GOLDEN";
        else if (hour >= 18 && hour <= 21 && (isWeekend || isFriday)) tag = "PRIME_WEEKEND";
        else if (hour >= 18 && hour <= 21) tag = "PRIME";
        else if (hour >= 12 && hour <= 14) tag = "MIDDAY";
        else if (hour >= 9 && hour <= 11) tag = "MORNING";
        else if (hour >= 22) tag = "LATE";

        return new SlotScore(bonus, tag, inGolden);
    }

    private List<Map<String, Object>> generatePlan(Map<String, Object> body) {
        String cinema = asString(body.get("cinema"));
        if (cinema == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "cinema required");
        List<String> rooms = asStringList(body.get("rooms"));
        if (rooms.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "rooms required");

        Integer price = asInt(body.get("price"));
        if (price == null || price <= 0) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "price required");
        Integer priceVip = asInt(body.get("priceVip"));
        String currency = asString(body.get("currency"));
        if (currency == null) currency = "VND";
        String format = asString(body.get("format"));
        String status = asString(body.get("status"));
        if (status == null) status = "ACTIVE";

        int gapMinutes = intOrDefault(asInt(body.get("gapMinutes")), 15);
        int stepMinutes = intOrDefault(asInt(body.get("stepMinutes")), 5);
        int leadTimeMinutes = intOrDefault(asInt(body.get("leadTimeMinutes")), 30);
        int maxPerRoomPerDay = intOrDefault(asInt(body.get("maxPerRoomPerDay")), 0);
        int defaultDurationMinutes = intOrDefault(asInt(body.get("defaultDurationMinutes")), 120);

        String revenueRange = asString(body.get("revenueRange"));
        if (revenueRange == null) revenueRange = "month";

        LocalDate today = LocalDate.now();
        LocalDate dateFrom = parseDateOrDefault(asString(body.get("dateFrom")), today);
        LocalDate dateTo = parseDateOrDefault(asString(body.get("dateTo")), dateFrom);
        if (dateTo.isBefore(dateFrom)) dateTo = dateFrom;

        LocalTime dayStart = parseTimeOrDefault(asString(body.get("dayStart")), LocalTime.of(9, 0));
        LocalTime dayEnd = parseTimeOrDefault(asString(body.get("dayEnd")), LocalTime.of(23, 0));
        if (!dayEnd.isAfter(dayStart)) dayEnd = dayStart.plusHours(10);

        List<TimeRange> goldenRanges = parseGoldenRanges(body.get("goldenHours"));
        Set<DayOfWeek> goldenDays = parseDays(body.get("goldenDays"));

        List<Long> movieIds = asLongList(body.get("movieIds"));
        List<MovieCandidate> candidates = loadCandidates(movieIds, defaultDurationMinutes, revenueRange);
        if (candidates.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "no movies for scheduling");

        List<Map<String, Object>> items = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDate d = dateFrom;
        while (!d.isAfter(dateTo)) {
            LocalDateTime windowStart = LocalDateTime.of(d, dayStart).withSecond(0).withNano(0);
            LocalDateTime windowEnd = LocalDateTime.of(d, dayEnd).withSecond(0).withNano(0);
            LocalDateTime minStart = now.plusMinutes(leadTimeMinutes).withSecond(0).withNano(0);

            for (String room : rooms) {
                if (room == null || room.isBlank()) continue;
                LocalDateTime cursor = max(windowStart, minStart);
                int planned = 0;
                int guard = 0;
                Long lastMovieId = null;
                Map<Long, Integer> counts = new HashMap<>();

                while (cursor.isBefore(windowEnd) && guard++ < 5000) {
                    int bestScore = Integer.MIN_VALUE;
                    MovieCandidate pick = null;
                    SlotScore slot = null;
                    int dur = defaultDurationMinutes;

                    int scan = Math.min(candidates.size(), 30);
                    for (int i = 0; i < scan; i++) {
                        MovieCandidate c = candidates.get(i);
                        int candidateDur = c.durationMinutes();
                        SlotScore ss = scoreSlot(cursor, candidateDur, goldenRanges, goldenDays);

                        int statusBonus = 0;
                        if (ss.inGolden()) {
                            statusBonus = (c.status() == MovieStatus.NOW_SHOWING) ? 180 : (c.status() == MovieStatus.UPCOMING) ? -40 : 0;
                        } else {
                            int hour = cursor.getHour();
                            if (hour < 12) statusBonus = (c.status() == MovieStatus.UPCOMING) ? 30 : 0;
                        }

                        int repeatPenalty = (lastMovieId != null && lastMovieId.equals(c.id())) ? 160 : 0;
                        int seenPenalty = counts.getOrDefault(c.id(), 0) * 25;

                        int finalScore = c.priorityScore() + ss.bonus() + statusBonus - repeatPenalty - seenPenalty;
                        if (finalScore > bestScore) {
                            bestScore = finalScore;
                            pick = c;
                            slot = ss;
                            dur = candidateDur;
                        }
                    }
                    if (pick == null) break;

                    LocalDateTime end = cursor.plusMinutes(dur);
                    if (!end.plusMinutes(gapMinutes).isBefore(windowEnd) && !end.plusMinutes(gapMinutes).isEqual(windowEnd)) break;

                    LocalDateTime checkStart = cursor.minusMinutes(gapMinutes);
                    LocalDateTime checkEnd = end.plusMinutes(gapMinutes);
                    List<ShowTime> conflicts = showTimeRepository.findConflicts(cinema, room, checkStart, checkEnd);
                    if (conflicts != null && !conflicts.isEmpty()) {
                        LocalDateTime maxEnd = null;
                        for (ShowTime c : conflicts) maxEnd = max(maxEnd, endOf(c));
                        if (maxEnd == null) {
                            cursor = cursor.plusMinutes(stepMinutes);
                        } else {
                            cursor = max(cursor.plusMinutes(stepMinutes), maxEnd.plusMinutes(gapMinutes));
                        }
                        continue;
                    }

                    Map<String, Object> it = new HashMap<>();
                    it.put("cinema", cinema);
                    it.put("room", room);
                    it.put("movieId", pick.id());
                    it.put("movieTmdbId", pick.tmdbId());
                    it.put("movieTitle", pick.title());
                    it.put("startTime", toLocalString(cursor));
                    it.put("endTime", toLocalString(end));
                    it.put("durationMinutes", dur);
                    it.put("price", price);
                    it.put("priceVip", priceVip);
                    it.put("currency", currency);
                    it.put("format", format);
                    it.put("status", status);
                    it.put("priorityScore", bestScore);
                    it.put("baseMovieScore", pick.priorityScore());
                    it.put("slotScore", slot == null ? 0 : slot.bonus());
                    it.put("slotTag", slot == null ? null : slot.tag());
                    it.put("movieStatus", pick.status() == null ? null : pick.status().name());
                    it.put("revenueRank", pick.revenueRank());
                    items.add(it);

                    lastMovieId = pick.id();
                    counts.put(pick.id(), counts.getOrDefault(pick.id(), 0) + 1);

                    cursor = end.plusMinutes(gapMinutes);
                    planned++;
                    if (maxPerRoomPerDay > 0 && planned >= maxPerRoomPerDay) break;
                }
            }
            d = d.plusDays(1);
        }

        items.sort((a, b) -> {
            Integer pa = asInt(a.get("priorityScore"));
            Integer pb = asInt(b.get("priorityScore"));
            int c = Integer.compare(pb == null ? 0 : pb, pa == null ? 0 : pa);
            if (c != 0) return c;
            String sa = asString(a.get("startTime"));
            String sb = asString(b.get("startTime"));
            if (sa != null && sb != null) {
                try {
                    LocalDateTime da = LocalDateTime.parse(sa, ISO_LOCAL);
                    LocalDateTime db = LocalDateTime.parse(sb, ISO_LOCAL);
                    c = da.compareTo(db);
                    if (c != 0) return c;
                } catch (Exception e) {
                    c = sa.compareTo(sb);
                    if (c != 0) return c;
                }
            } else if (sa != null) return -1;
            else if (sb != null) return 1;
            String ra = asString(a.get("room"));
            String rb = asString(b.get("room"));
            if (ra == null) ra = "";
            if (rb == null) rb = "";
            return ra.compareTo(rb);
        });

        return items;
    }

    @PostMapping("/auto/preview")
    public ResponseEntity<?> autoPreview(@RequestBody Map<String, Object> body) {
        List<Map<String, Object>> items = generatePlan(body);
        Map<String, Object> resp = new HashMap<>();
        resp.put("count", items.size());
        resp.put("items", items);
        return ResponseEntity.ok(resp);
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(value = "cinema", required = false) String cinema) {
        java.util.List<ShowTime> list = (cinema == null || cinema.isBlank())
                ? showTimeRepository.findAllByOrderByStartTimeDesc()
                : showTimeRepository.findByCinemaOrderByStartTimeDesc(cinema);
        java.util.List<java.util.Map<String, Object>> result = list.stream().map(st -> {
            java.time.LocalDateTime start = st.getStartTime();
            Integer dur = st.getDurationMinutes();
            java.time.LocalDateTime end = (start == null || dur == null) ? null : start.plusMinutes(dur);
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            String rawStatus = st.getStatus();
            String computed;
            if (Boolean.TRUE.equals(st.getDisabled())) {
                computed = "DISABLED";
            } else if (rawStatus != null && rawStatus.equalsIgnoreCase("CANCELLED")) {
                computed = "CANCELLED";
            } else if (start != null && now.isBefore(start)) {
                computed = "UPCOMING";
            } else if (end != null && now.isAfter(end)) {
                computed = "FINISHED";
            } else {
                computed = (rawStatus == null || rawStatus.isBlank()) ? "ACTIVE" : rawStatus;
            }
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", st.getId());
            m.put("movieId", st.getMovieId());
            m.put("movieTmdbId", st.getMovieTmdbId());
            m.put("movieTitle", st.getMovieTitle());
            m.put("cinema", st.getCinema());
            m.put("room", st.getRoom());
            m.put("startTime", toLocalString(st.getStartTime()));
            m.put("durationMinutes", st.getDurationMinutes());
            m.put("price", st.getPrice());
            m.put("priceVip", st.getPriceVip());
            m.put("status", computed);
            m.put("disabled", st.getDisabled());
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/auto/create")
    @org.springframework.cache.annotation.Caching(evict = {
            @org.springframework.cache.annotation.CacheEvict(value = "showtimes", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "public_now_playing", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "public_upcoming", allEntries = true)
    })
    public ResponseEntity<?> autoCreate(@RequestBody Map<String, Object> body) {
        List<Map<String, Object>> items = generatePlan(body);
        String mode = asString(body.get("mode"));
        if (mode == null) mode = "SKIP";

        List<Map<String, Object>> created = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        for (Map<String, Object> it : items) {
            try {
                ShowTime payload = new ShowTime();
                payload.setMovieId(asLong(it.get("movieId")));
                payload.setMovieTmdbId(asLong(it.get("movieTmdbId")));
                payload.setMovieTitle(asString(it.get("movieTitle")));
                payload.setCinema(asString(it.get("cinema")));
                payload.setRoom(asString(it.get("room")));
                payload.setStartTime(LocalDateTime.parse(asString(it.get("startTime")), ISO_LOCAL));
                payload.setDurationMinutes(asInt(it.get("durationMinutes")));
                payload.setPrice(asInt(it.get("price")));
                payload.setPriceVip(asInt(it.get("priceVip")));
                payload.setCurrency(asString(it.get("currency")));
                payload.setFormat(asString(it.get("format")));
                payload.setStatus(asString(it.get("status")));

                resolveMovieIds(payload);

                if (payload.getMovieId() == null || payload.getStartTime() == null || payload.getDurationMinutes() == null || payload.getDurationMinutes() <= 0) {
                    Map<String, Object> sk = new HashMap<>(it);
                    sk.put("reason", "INVALID_ITEM");
                    skipped.add(sk);
                    continue;
                }

                LocalDateTime start = payload.getStartTime();
                LocalDateTime end = start.plusMinutes(payload.getDurationMinutes());
                List<ShowTime> conflicts = showTimeRepository.findConflicts(payload.getCinema(), payload.getRoom(), start, end);
                if (conflicts != null && !conflicts.isEmpty()) {
                    Map<String, Object> sk = new HashMap<>(it);
                    sk.put("reason", "CONFLICT");
                    skipped.add(sk);
                    if ("FAIL_FAST".equalsIgnoreCase(mode)) break;
                    continue;
                }

                ShowTime st = ShowTime.builder()
                        .movieId(payload.getMovieId())
                        .movieTmdbId(payload.getMovieTmdbId())
                        .startTime(payload.getStartTime())
                        .cinema(payload.getCinema())
                        .room(payload.getRoom())
                        .price(payload.getPrice())
                        .priceVip(payload.getPriceVip())
                        .currency(payload.getCurrency() == null ? "VND" : payload.getCurrency())
                        .movieTitle(payload.getMovieTitle())
                        .format(payload.getFormat())
                        .durationMinutes(payload.getDurationMinutes())
                        .status(payload.getStatus() == null ? "ACTIVE" : payload.getStatus())
                        .disabled(Boolean.FALSE)
                        .build();

                st = showTimeRepository.save(st);
                seedSeats(st);

                Map<String, Object> cr = new HashMap<>(it);
                cr.put("id", st.getId());
                created.add(cr);
            } catch (Exception e) {
                Map<String, Object> sk = new HashMap<>(it);
                sk.put("reason", "ERROR");
                sk.put("error", e.getMessage());
                skipped.add(sk);
                if ("FAIL_FAST".equalsIgnoreCase(mode)) break;
            }
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("planned", items.size());
        resp.put("created", created.size());
        resp.put("skipped", skipped.size());
        resp.put("createdItems", created);
        resp.put("skippedItems", skipped);
        return ResponseEntity.ok(resp);
    }

    @PostMapping
    @org.springframework.cache.annotation.Caching(evict = {
            @org.springframework.cache.annotation.CacheEvict(value = "showtimes", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "public_now_playing", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "public_upcoming", allEntries = true)
    })
    public ResponseEntity<?> create(@org.springframework.web.bind.annotation.RequestBody ShowTime payload) {
        try {
            if (payload.getDurationMinutes() == null || payload.getDurationMinutes() <= 0) {
                return ResponseEntity.badRequest().body("durationMinutes required");
            }
            if (payload.getMovieId() == null && payload.getMovieTmdbId() == null) {
                return ResponseEntity.badRequest().body("movieId or movieTmdbId required");
            }
            if (payload.getStartTime() == null) {
                return ResponseEntity.badRequest().body("startTime required");
            }
            if (payload.getStartTime().isBefore(java.time.LocalDateTime.now())) {
                return ResponseEntity.badRequest().body("startTime must be in the future");
            }
            resolveMovieIds(payload);
            if (payload.getMovieId() == null) {
                return ResponseEntity.badRequest().body("movieId not resolvable");
            }
            if (payload.getMovieId() != null && (payload.getMovieTitle() == null || payload.getMovieTitle().isBlank())) {
                movieRepository.findById(payload.getMovieId()).ifPresent(m -> payload.setMovieTitle(m.getTitle()));
            }
            java.time.LocalDateTime start = payload.getStartTime();
            java.time.LocalDateTime end = start.plusMinutes(payload.getDurationMinutes());
            java.util.List<com.movie.movie_booking_api.entity.ShowTime> conflicts = showTimeRepository.findConflicts(payload.getCinema(), payload.getRoom(), start, end);
            if (!conflicts.isEmpty()) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).body("Showtime conflict");
            }
            ShowTime st = ShowTime.builder()
                    .movieId(payload.getMovieId())
                    .movieTmdbId(payload.getMovieTmdbId())
                    .startTime(payload.getStartTime())
                    .cinema(payload.getCinema())
                    .room(payload.getRoom())
                    .price(payload.getPrice())
                    .priceVip(payload.getPriceVip())
                    .currency(payload.getCurrency() == null ? "VND" : payload.getCurrency())
                    .movieTitle(payload.getMovieTitle())
                    .format(payload.getFormat())
                    .durationMinutes(payload.getDurationMinutes())
                    .status(payload.getStatus() == null ? "ACTIVE" : payload.getStatus())
                    .disabled(Boolean.FALSE)
                    .build();
            log.info("Admin create showtime: title={}, startTime(Local)={}, cinema={}, room={}", st.getMovieTitle(), st.getStartTime(), st.getCinema(), st.getRoom());
            st = showTimeRepository.save(st);

            // Auto seed seats
            seedSeats(st);

            java.util.Map<String, Object> resp = new java.util.HashMap<>();
            resp.put("id", st.getId());
            resp.put("movieId", st.getMovieId());
            resp.put("movieTmdbId", st.getMovieTmdbId());
            resp.put("startTime", toLocalString(st.getStartTime()));
            resp.put("cinema", st.getCinema());
            resp.put("room", st.getRoom());
            resp.put("price", st.getPrice());
            resp.put("movieTitle", st.getMovieTitle());
            resp.put("durationMinutes", st.getDurationMinutes());
            resp.put("status", st.getStatus());
            
            return ResponseEntity.status(201).body(resp);
        } catch (Exception e) {
            log.error("Error creating showtime", e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @org.springframework.cache.annotation.Caching(evict = {
            @org.springframework.cache.annotation.CacheEvict(value = "showtimes", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "public_now_playing", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "public_upcoming", allEntries = true)
    })
    public ResponseEntity<?> update(@PathVariable("id") Long id,
                                    @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body) {
        ShowTime st = showTimeRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        if (body.containsKey("movieTitle")) st.setMovieTitle(String.valueOf(body.get("movieTitle")));
        if (body.containsKey("price")) {
            st.setPrice(((Number) body.get("price")).intValue());
            // Update seats
            updateSeatPrices(st);
        }
        if (body.containsKey("priceVip")) {
            Object v = body.get("priceVip");
            if (v == null) st.setPriceVip(null);
            else st.setPriceVip(((Number) v).intValue());
            // Update seats
            updateSeatPrices(st);
        }
        if (body.containsKey("durationMinutes")) st.setDurationMinutes(((Number) body.get("durationMinutes")).intValue());
        if (body.containsKey("status")) st.setStatus(String.valueOf(body.get("status")));
        if (body.containsKey("disabled")) {
            Boolean d = asBool(body.get("disabled"));
            if (d != null) st.setDisabled(d);
        }
        if (body.containsKey("startTime")) {
            Object v = body.get("startTime");
            java.time.OffsetDateTime odt;
            if (v instanceof String s) {
                odt = java.time.OffsetDateTime.parse(s);
            } else {
                odt = java.time.OffsetDateTime.parse(String.valueOf(v));
            }
            java.time.LocalDateTime newStart = odt.toLocalDateTime();
            if (newStart.isBefore(java.time.LocalDateTime.now())) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "startTime must be in the future");
            }
            st.setStartTime(newStart);
        }
        st = showTimeRepository.save(st);
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("id", st.getId());
        resp.put("movieTitle", st.getMovieTitle());
        resp.put("price", st.getPrice());
        resp.put("priceVip", st.getPriceVip());
        resp.put("startTime", toLocalString(st.getStartTime()));
        resp.put("durationMinutes", st.getDurationMinutes());
        resp.put("status", st.getStatus());
        return ResponseEntity.ok(resp);
    }

    private void seedSeats(ShowTime st) {
        try {
            java.util.List<String> rows = java.util.List.of("A", "B", "C", "D", "E", "F");
            int cols = 8;
            java.util.List<com.movie.movie_booking_api.entity.Seat> toCreate = new java.util.ArrayList<>();
            for (int rIdx = 0; rIdx < rows.size(); rIdx++) {
                String row = rows.get(rIdx);
                for (int cIdx = 0; cIdx < cols; cIdx++) {
                    int number = cIdx + 1;
                    String code = row + number;
                    com.movie.movie_booking_api.entity.SeatType type = ("C".equals(row) || "D".equals(row)) ? com.movie.movie_booking_api.entity.SeatType.VIP : com.movie.movie_booking_api.entity.SeatType.NORMAL;
                    int seatPrice = (type == com.movie.movie_booking_api.entity.SeatType.VIP)
                            ? (st.getPriceVip() != null ? st.getPriceVip() : (st.getPrice() + 20000))
                            : st.getPrice();

                    toCreate.add(com.movie.movie_booking_api.entity.Seat.builder()
                            .showtimeId(st.getId())
                            .code(code)
                            .status(com.movie.movie_booking_api.entity.SeatStatus.AVAILABLE)
                            .type(type)
                            .price(seatPrice)
                            .row(row)
                            .number(number)
                            .rowIndex(rIdx)
                            .colIndex(cIdx)
                            .build());
                }
            }
            if (!toCreate.isEmpty()) {
                seatRepository.saveAll(toCreate);
            }
        } catch (Exception e) {
            log.error("Failed to auto seed seats for showtime {}", st.getId(), e);
        }
    }

    private void updateSeatPrices(ShowTime st) {
        java.util.List<com.movie.movie_booking_api.entity.Seat> seats = seatRepository.findByShowtimeId(st.getId());
        for (com.movie.movie_booking_api.entity.Seat s : seats) {
            com.movie.movie_booking_api.entity.SeatType type = s.getType();
            int seatPrice = (type == com.movie.movie_booking_api.entity.SeatType.VIP) 
                    ? (st.getPriceVip() != null ? st.getPriceVip() : (st.getPrice() + 20000)) 
                    : st.getPrice();
            s.setPrice(seatPrice);
        }
        if (!seats.isEmpty()) {
            seatRepository.saveAll(seats);
        }
    }

    @DeleteMapping("/{id}")
    @Transactional
    @org.springframework.cache.annotation.Caching(evict = {
            @org.springframework.cache.annotation.CacheEvict(value = "showtimes", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "public_now_playing", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "public_upcoming", allEntries = true)
    })
    public ResponseEntity<?> delete(@PathVariable("id") Long id) {
        Long bookingCount = bookingRepository.countByShowtimeId(id);
        if (bookingCount > 0) {
            ShowTime st = showTimeRepository.findById(id)
                    .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
            st.setDisabled(Boolean.TRUE);
            showTimeRepository.save(st);
            return ResponseEntity.ok(java.util.Map.of("deleted", id, "soft", true, "message", "Showtime has bookings, disabled instead."));
        }
        seatRepository.deleteByShowtimeId(id);
        showTimeRepository.deleteById(id);
        return ResponseEntity.ok(java.util.Map.of("deleted", id));
    }

    @PostMapping("/bulk-delete")
    @Transactional
    @org.springframework.cache.annotation.Caching(evict = {
            @org.springframework.cache.annotation.CacheEvict(value = "showtimes", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "public_now_playing", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "public_upcoming", allEntries = true)
    })
    public ResponseEntity<?> bulkDelete(@RequestBody Map<String, Object> body) {
        List<Long> ids = asLongList(body.get("ids"));
        if (ids.isEmpty()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "ids required"));
        }

        int hardDeleted = 0;
        int softDisabled = 0;
        int notFound = 0;
        int errors = 0;
        java.util.List<java.util.Map<String, Object>> items = new java.util.ArrayList<>();

        for (Long id : ids) {
            if (id == null) continue;
            try {
                ShowTime st = showTimeRepository.findById(id).orElse(null);
                if (st == null) {
                    notFound++;
                    items.add(java.util.Map.of("id", id, "result", "NOT_FOUND"));
                    continue;
                }
                Long bookingCount = bookingRepository.countByShowtimeId(id);
                if (bookingCount != null && bookingCount > 0) {
                    st.setDisabled(Boolean.TRUE);
                    showTimeRepository.save(st);
                    softDisabled++;
                    items.add(java.util.Map.of("id", id, "result", "SOFT_DISABLED", "bookings", bookingCount));
                } else {
                    seatRepository.deleteByShowtimeId(id);
                    showTimeRepository.deleteById(id);
                    hardDeleted++;
                    items.add(java.util.Map.of("id", id, "result", "DELETED"));
                }
            } catch (Exception e) {
                errors++;
                java.util.Map<String, Object> it = new java.util.HashMap<>();
                it.put("id", id);
                it.put("result", "ERROR");
                it.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                items.add(it);
            }
        }

        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("requested", ids.size());
        resp.put("hardDeleted", hardDeleted);
        resp.put("softDisabled", softDisabled);
        resp.put("notFound", notFound);
        resp.put("errors", errors);
        resp.put("items", items);
        return ResponseEntity.ok(resp);
    }
}
