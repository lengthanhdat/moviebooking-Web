package com.movie.movie_booking_api.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
@RequiredArgsConstructor
public class SeatRealtimeService {

    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>> sessionsByShowtimeId = new ConcurrentHashMap<>();

    public void register(Long showtimeId, WebSocketSession session) {
        sessionsByShowtimeId.computeIfAbsent(showtimeId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void unregister(Long showtimeId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByShowtimeId.get(showtimeId);
        if (sessions == null) return;
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByShowtimeId.remove(showtimeId, sessions);
        }
    }

    public void broadcastHeld(Long showtimeId, List<String> seatCodes, OffsetDateTime expiresAt) {
        send(showtimeId, Map.of(
                "showtimeId", showtimeId,
                "status", "HELD",
                "seats", seatCodes,
                "heldUntil", expiresAt
        ));
    }

    public void broadcastAvailable(Long showtimeId, List<String> seatCodes) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("showtimeId", showtimeId);
        payload.put("status", "AVAILABLE");
        payload.put("seats", seatCodes);
        send(showtimeId, payload);
    }

    private void send(Long showtimeId, Object payload) {
        Set<WebSocketSession> sessions = sessionsByShowtimeId.get(showtimeId);
        if (sessions == null || sessions.isEmpty()) return;

        final String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return;
        }

        TextMessage msg = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            try {
                if (session == null || !session.isOpen()) {
                    sessions.remove(session);
                    continue;
                }
                session.sendMessage(msg);
            } catch (Exception e) {
                sessions.remove(session);
            }
        }
        if (sessions.isEmpty()) {
            sessionsByShowtimeId.remove(showtimeId);
        }
    }
}
