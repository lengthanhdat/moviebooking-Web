package com.movie.movie_booking_api;

import com.movie.movie_booking_api.controller.SeatingController;
import com.movie.movie_booking_api.controller.AdminShowTimeController;
import com.movie.movie_booking_api.dto.LoginRequest;
import com.movie.movie_booking_api.entity.Seat;
import com.movie.movie_booking_api.entity.SeatStatus;
import com.movie.movie_booking_api.entity.ShowTime;
import com.movie.movie_booking_api.entity.User;
import com.movie.movie_booking_api.entity.UserRole;
import com.movie.movie_booking_api.realtime.SeatHoldCleanupTask;
import com.movie.movie_booking_api.realtime.SeatRealtimeService;
import com.movie.movie_booking_api.repository.ShowTimeRepository;
import com.movie.movie_booking_api.repository.SeatRepository;
import com.movie.movie_booking_api.repository.UserRepository;
import com.movie.movie_booking_api.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@SpringBootTest
@TestPropertySource(properties = {
		"tmdb.api-key=",
		"tmdb.read-access-token="
})
class MovieBookingApiApplicationTests {

	@jakarta.annotation.Resource
	private SeatRepository seatRepository;

	@jakarta.annotation.Resource
	private SeatingController seatingController;

	@jakarta.annotation.Resource
	private SeatHoldCleanupTask seatHoldCleanupTask;

	@jakarta.annotation.Resource
	private UserRepository userRepository;

	@jakarta.annotation.Resource
	private ShowTimeRepository showTimeRepository;

	@jakarta.annotation.Resource
	private AdminShowTimeController adminShowTimeController;

	@jakarta.annotation.Resource
	private AuthService authService;

	@jakarta.annotation.Resource
	private PasswordEncoder passwordEncoder;

	@SpyBean
	private SeatRealtimeService seatRealtimeService;

	@jakarta.annotation.Resource
	private EntityManager entityManager;

	@Test
	void contextLoads() {
	}

	@Test
	void holdSeatsShouldMarkHeldAndBroadcast() {
		long showtimeId = 999_999L;
		List<String> codes = List.of("T1", "T2");

		List<Seat> created = codes.stream()
				.map(code -> Seat.builder()
						.showtimeId(showtimeId)
						.code(code)
						.status(SeatStatus.AVAILABLE)
						.build())
				.toList();

		created = seatRepository.saveAll(created);
		try {
			ResponseEntity<?> res = seatingController.hold(Map.of(
					"showtimeId", showtimeId,
					"seats", codes,
					"ttlSeconds", 5
			));
			assertEquals(200, res.getStatusCode().value());

			List<Seat> after = seatRepository.findByShowtimeIdAndCodeIn(showtimeId, codes);
			assertEquals(2, after.size());
			assertTrue(after.stream().allMatch(s -> s.getStatus() == SeatStatus.HELD));
			assertTrue(after.stream().allMatch(s -> s.getHeldUntil() != null));
			assertTrue(after.stream().allMatch(s -> s.getHeldBy() != null && !s.getHeldBy().isBlank()));

			verify(seatRealtimeService, atLeastOnce()).broadcastHeld(eq(showtimeId), eq(codes), any());
		} finally {
			seatRepository.deleteAllById(created.stream().map(Seat::getId).collect(Collectors.toList()));
		}
	}

	@Test
	void expiredHeldSeatsShouldBeReleasedAndBroadcastAvailable() {
		long showtimeId = 999_998L;
		List<String> codes = List.of("E1", "E2");

		LocalDateTime expiredAt = LocalDateTime.now().minusSeconds(30);
		List<Seat> created = codes.stream()
				.map(code -> Seat.builder()
						.showtimeId(showtimeId)
						.code(code)
						.status(SeatStatus.HELD)
						.heldBy("test-hold")
						.heldUntil(expiredAt)
						.build())
				.toList();

		created = seatRepository.saveAll(created);
		try {
			seatHoldCleanupTask.cleanupExpiredHolds();

			List<Seat> after = seatRepository.findByShowtimeIdAndCodeIn(showtimeId, codes);
			assertEquals(2, after.size());
			assertTrue(after.stream().allMatch(s -> s.getStatus() == SeatStatus.AVAILABLE));
			assertTrue(after.stream().allMatch(s -> s.getHeldUntil() == null));
			assertTrue(after.stream().allMatch(s -> s.getHeldBy() == null));

			verify(seatRealtimeService, atLeastOnce()).broadcastAvailable(eq(showtimeId), argThat(list -> list.containsAll(codes)));
		} finally {
			seatRepository.deleteAllById(created.stream().map(Seat::getId).collect(Collectors.toList()));
		}
	}

	@Test
	void releaseHoldShouldFreeSeatsAndBroadcastAvailable() {
		long showtimeId = 999_997L;
		String holdId = "test-release-hold";
		List<String> codes = List.of("R1", "R2");

		List<Seat> created = codes.stream()
				.map(code -> Seat.builder()
						.showtimeId(showtimeId)
						.code(code)
						.status(SeatStatus.HELD)
						.heldBy(holdId)
						.heldUntil(LocalDateTime.now().plusSeconds(300))
						.build())
				.toList();

		created = seatRepository.saveAll(created);
		try {
			ResponseEntity<?> res = seatingController.release(Map.of(
					"showtimeId", showtimeId,
					"holdId", holdId
			));
			assertEquals(200, res.getStatusCode().value());

			List<Seat> after = seatRepository.findByShowtimeIdAndCodeIn(showtimeId, codes);
			assertEquals(2, after.size());
			assertTrue(after.stream().allMatch(s -> s.getStatus() == SeatStatus.AVAILABLE));
			assertTrue(after.stream().allMatch(s -> s.getHeldUntil() == null));
			assertTrue(after.stream().allMatch(s -> s.getHeldBy() == null));

			verify(seatRealtimeService, atLeastOnce()).broadcastAvailable(eq(showtimeId), argThat(list -> list.containsAll(codes)));
		} finally {
			seatRepository.deleteAllById(created.stream().map(Seat::getId).collect(Collectors.toList()));
		}
	}

	@Test
	void adminCanLoginWithoutEmailVerified() {
		String email = "admin-login-" + System.currentTimeMillis() + "@example.com";
		String rawPassword = "P@ssw0rd123";
		String uniqueName = "Admin-" + System.currentTimeMillis();

		User u = User.builder()
				.name(uniqueName)
				.fullName(uniqueName)
				.legacyName(uniqueName)
				.email(email)
				.password(passwordEncoder.encode(rawPassword))
				.role(UserRole.ADMIN)
				.locked(Boolean.FALSE)
				.emailVerified(Boolean.FALSE)
				.build();
		u = userRepository.save(u);

		try {
			LoginRequest req = new LoginRequest();
			req.setEmail(email);
			req.setPassword(rawPassword);
			var res = authService.login(req);
			assertNotNull(res.getToken());
			assertFalse(res.getToken().isBlank());
		} finally {
			userRepository.deleteById(u.getId());
		}
	}

	@Test
	void bulkDeleteShouldRemoveSeatsThenDeleteShowtime() {
		ShowTime st = ShowTime.builder()
				.movieId(1L)
				.movieTmdbId(1L)
				.movieTitle("Test Movie")
				.cinema("Test Cinema")
				.room("Room 1")
				.startTime(LocalDateTime.now().plusDays(1))
				.durationMinutes(120)
				.price(70000)
				.priceVip(90000)
				.currency("VND")
				.status("UPCOMING")
				.disabled(Boolean.FALSE)
				.build();
		st = showTimeRepository.save(st);

		long showtimeId = st.getId();
		List<Seat> seats = List.of(
				Seat.builder().showtimeId(showtimeId).code("A1").status(SeatStatus.AVAILABLE).build(),
				Seat.builder().showtimeId(showtimeId).code("A2").status(SeatStatus.AVAILABLE).build()
		);
		seats = seatRepository.saveAll(seats);

		try {
			ResponseEntity<?> res = adminShowTimeController.bulkDelete(Map.of("ids", List.of(showtimeId)));
			assertEquals(200, res.getStatusCode().value());
			assertNotNull(res.getBody());
			assertTrue(res.getBody() instanceof Map);
			@SuppressWarnings("unchecked")
			Map<String, Object> body = (Map<String, Object>) res.getBody();
			Object errorsObj = body.get("errors");
			Object hardDeletedObj = body.get("hardDeleted");
			int errors = (errorsObj instanceof Number n) ? n.intValue() : 0;
			int hardDeleted = (hardDeletedObj instanceof Number n) ? n.intValue() : 0;
			assertEquals(0, errors, String.valueOf(body));
			assertEquals(1, hardDeleted, String.valueOf(body));
			entityManager.clear();
			assertTrue(showTimeRepository.findById(showtimeId).isEmpty());
			assertTrue(seatRepository.findByShowtimeId(showtimeId).isEmpty());
		} finally {
			seatRepository.deleteAllById(seats.stream().map(Seat::getId).collect(Collectors.toList()));
			showTimeRepository.deleteById(showtimeId);
		}
	}

}
