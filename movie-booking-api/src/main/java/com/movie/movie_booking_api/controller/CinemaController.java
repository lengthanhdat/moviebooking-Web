package com.movie.movie_booking_api.controller;

import com.movie.movie_booking_api.entity.Cinema;
import com.movie.movie_booking_api.entity.Room;
import com.movie.movie_booking_api.repository.CinemaRepository;
import com.movie.movie_booking_api.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/cinemas")
@RequiredArgsConstructor
public class CinemaController {

    private final CinemaRepository cinemaRepository;
    private final RoomRepository roomRepository;

    @GetMapping
    public ResponseEntity<?> list() { return ResponseEntity.ok(cinemaRepository.findAll()); }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Cinema payload) { return ResponseEntity.status(201).body(cinemaRepository.save(payload)); }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable("id") Long id, @RequestBody Cinema payload) {
        Cinema c = cinemaRepository.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        c.setName(payload.getName()); c.setLocation(payload.getLocation()); c.setStatus(payload.getStatus());
        return ResponseEntity.ok(cinemaRepository.save(c));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") Long id) { cinemaRepository.deleteById(id); return ResponseEntity.ok(Map.of("deleted", id)); }

    @GetMapping("/{id}/rooms")
    public ResponseEntity<?> rooms(@PathVariable("id") Long id) { return ResponseEntity.ok(roomRepository.findByCinemaId(id)); }

    @PostMapping("/{id}/rooms")
    public ResponseEntity<?> createRoom(@PathVariable("id") Long id, @RequestBody Room payload) {
        Room r = Room.builder().cinemaId(id).name(payload.getName()).status(payload.getStatus()).build();
        return ResponseEntity.status(201).body(roomRepository.save(r));
    }

    @PutMapping("/rooms/{id}")
    public ResponseEntity<?> updateRoom(@PathVariable("id") Long id, @RequestBody Room payload) {
        Room r = roomRepository.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        if (payload.getName() != null) r.setName(payload.getName());
        if (payload.getStatus() != null) r.setStatus(payload.getStatus());
        return ResponseEntity.ok(roomRepository.save(r));
    }

    @DeleteMapping("/rooms/{id}")
    public ResponseEntity<?> deleteRoom(@PathVariable("id") Long id) {
        roomRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }
}