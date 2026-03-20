package com.movie.movie_booking_api.realtime;

import com.movie.movie_booking_api.entity.Seat;
import com.movie.movie_booking_api.entity.SeatStatus;
import com.movie.movie_booking_api.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SeatHoldCleanupTask {

    private final SeatRepository seatRepository;
    private final SeatRealtimeService seatRealtimeService;

    @Scheduled(initialDelay = 10000, fixedDelay = 5000)
    @Transactional
    public void cleanupExpiredHolds() {
        LocalDateTime now = LocalDateTime.now();
        List<Seat> expired = seatRepository.lockExpiredHeldSeats(now);
        if (expired.isEmpty()) return;

        Map<Long, List<String>> codesByShowtimeId = new HashMap<>();
        for (Seat s : expired) {
            if (s.getStatus() != SeatStatus.HELD) continue;
            s.setStatus(SeatStatus.AVAILABLE);
            s.setHeldUntil(null);
            s.setHeldBy(null);
            codesByShowtimeId.computeIfAbsent(s.getShowtimeId(), k -> new ArrayList<>()).add(s.getCode());
        }
        seatRepository.saveAll(expired);

        for (Map.Entry<Long, List<String>> e : codesByShowtimeId.entrySet()) {
            if (!e.getValue().isEmpty()) {
                seatRealtimeService.broadcastAvailable(e.getKey(), e.getValue());
            }
        }
    }
}
