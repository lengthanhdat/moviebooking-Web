package com.movie.movie_booking_api.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "seats", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"showtime_id", "code"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "showtime_id", nullable = false)
    private Long showtimeId;

    @Column(name = "code", nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SeatStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private SeatType type;

    @Column(name = "price")
    private Integer price;

    @Column(name = "row_label")
    private String row;

    @Column(name = "number")
    private Integer number;

    @Column(name = "row_index")
    private Integer rowIndex;

    @Column(name = "col_index")
    private Integer colIndex;

    @Column(name = "section")
    private String section;

    @Column(name = "held_until")
    private java.time.LocalDateTime heldUntil;
}