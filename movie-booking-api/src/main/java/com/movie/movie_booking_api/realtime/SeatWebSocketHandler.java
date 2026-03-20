package com.movie.movie_booking_api.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class SeatWebSocketHandler extends TextWebSocketHandler {

    private final SeatRealtimeService seatRealtimeService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            Long showtimeId = parseShowtimeId(session);
            if (showtimeId == null) {
                try {
                    session.close(CloseStatus.BAD_DATA);
                } catch (Exception ignored) {
                }
                return;
            }
            session.getAttributes().put("showtimeId", showtimeId);
            seatRealtimeService.register(showtimeId, session);
        } catch (Exception e) {
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object v = session.getAttributes().get("showtimeId");
        if (v instanceof Long showtimeId) {
            seatRealtimeService.unregister(showtimeId, session);
        }
    }

    private Long parseShowtimeId(WebSocketSession session) {
        try {
            String v = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst("showtimeId");
            if (v == null || v.isBlank()) return null;
            return Long.parseLong(v.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
