package com.movie.movie_booking_api.controller;

import com.movie.movie_booking_api.entity.User;
import com.movie.movie_booking_api.entity.UserRole;
import com.movie.movie_booking_api.repository.UserRepository;
import com.movie.movie_booking_api.repository.BookingRepository;
import com.movie.movie_booking_api.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<?> setRole(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        User u = userRepository.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        String role = body.getOrDefault("role", "CUSTOMER");
        u.setRole(UserRole.valueOf(role));
        return ResponseEntity.ok(userRepository.save(u));
    }

    @PutMapping("/{id}/lock")
    public ResponseEntity<?> lock(@PathVariable("id") Long id, @RequestBody Map<String, Boolean> body) {
        User u = userRepository.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        Boolean locked = body.getOrDefault("locked", Boolean.TRUE);
        u.setLocked(locked);
        return ResponseEntity.ok(userRepository.save(u));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") Long id) {
        User u = userRepository.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        
        // Cascade delete: Bookings
        java.util.List<com.movie.movie_booking_api.entity.Booking> bookings = bookingRepository.findByUserId(id);
        if (!bookings.isEmpty()) {
            bookingRepository.deleteAll(bookings);
        }

        // Cascade delete: Payments
        java.util.List<com.movie.movie_booking_api.entity.Payment> payments = paymentRepository.findByUserIdOrderByCreatedAtDesc(id);
        if (!payments.isEmpty()) {
            paymentRepository.deleteAll(payments);
        }

        userRepository.delete(u);
        return ResponseEntity.ok(java.util.Map.of("deleted", id));
    }

    @GetMapping("/duplicates")
    public ResponseEntity<?> listDuplicates() {
        java.util.List<User> all = userRepository.findAll();
        java.util.Map<String, java.util.List<User>> groups = new java.util.HashMap<>();
        for (User u : all) {
            String key = (u.getEmail() == null ? "" : u.getEmail().trim().toLowerCase());
            groups.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(u);
        }
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (var e : groups.entrySet()) {
            if (e.getValue().size() > 1) {
                java.util.List<User> list = new java.util.ArrayList<>(e.getValue());
                list.sort(java.util.Comparator.comparing(User::getId));
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("email", e.getKey());
                m.put("userIds", list.stream().map(User::getId).toList());
                result.add(m);
            }
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/merge-all")
    public ResponseEntity<?> mergeAllDuplicates() {
        java.util.List<User> all = userRepository.findAll();
        java.util.Map<String, java.util.List<User>> groups = new java.util.HashMap<>();
        for (User u : all) {
            String key = (u.getEmail() == null ? "" : u.getEmail().trim().toLowerCase());
            groups.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(u);
        }

        java.util.List<java.util.Map<String, Object>> merged = new java.util.ArrayList<>();
        for (var e : groups.entrySet()) {
            java.util.List<User> list = e.getValue();
            if (list.size() <= 1) continue;
            list.sort(java.util.Comparator.comparing(User::getId));
            User canonical = list.get(0);
            java.util.List<User> duplicates = list.subList(1, list.size());
            for (User dup : duplicates) {
                Long oldId = dup.getId();
                java.util.List<com.movie.movie_booking_api.entity.Booking> bs = bookingRepository.findByUserId(oldId);
                for (com.movie.movie_booking_api.entity.Booking b : bs) { b.setUserId(canonical.getId()); }
                bookingRepository.saveAll(bs);
                java.util.List<com.movie.movie_booking_api.entity.Payment> ps = paymentRepository.findByUserIdOrderByCreatedAtDesc(oldId);
                for (com.movie.movie_booking_api.entity.Payment p : ps) { p.setUserId(canonical.getId()); }
                paymentRepository.saveAll(ps);
            }
            // Sau khi migrate, xóa các user trùng để chuẩn bị tạo unique index email_lower
            userRepository.deleteAll(duplicates);
            merged.add(java.util.Map.of(
                    "email", e.getKey(),
                    "canonicalId", canonical.getId(),
                    "migratedFrom", duplicates.stream().map(User::getId).toList(),
                    "bookingsMoved", mergedCountBookings(bookingRepository, canonical.getId(), duplicates.stream().map(User::getId).toList())
            ));
        }

        return ResponseEntity.ok(java.util.Map.of("merged", merged));
    }

    private int mergedCountBookings(BookingRepository repo, Long canonicalId, java.util.List<Long> oldIds) {
        int total = 0;
        for (Long id : oldIds) {
            java.util.List<com.movie.movie_booking_api.entity.Booking> bs = repo.findByUserId(canonicalId);
            total += (bs == null ? 0 : bs.size());
        }
        return total;
    }
}
