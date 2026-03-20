package com.movie.movie_booking_api.repository;

import com.movie.movie_booking_api.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByCinemaId(Long cinemaId);
}