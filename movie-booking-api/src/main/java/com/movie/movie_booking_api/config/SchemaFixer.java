package com.movie.movie_booking_api.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Component
public class SchemaFixer {

    private final DataSource dataSource;

    public SchemaFixer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void fix() {
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            try {
                st.execute("ALTER TABLE seats MODIFY status VARCHAR(10) NOT NULL");
            } catch (SQLException ignored) {}
            try {
                st.execute("ALTER TABLE seats MODIFY COLUMN held_until DATETIME NULL");
            } catch (SQLException ignored) {}
            try {
                st.execute("ALTER DATABASE movie_booking CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            } catch (SQLException ignored) {}
            try {
                st.execute("ALTER TABLE payments CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            } catch (SQLException ignored) {}
            try {
                st.execute("ALTER TABLE payments MODIFY movie_title VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            } catch (SQLException ignored) {}
            try {
                st.execute("ALTER TABLE show_times CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            } catch (SQLException ignored) {}
            try {
                st.execute("ALTER TABLE show_times MODIFY movie_title VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            } catch (SQLException ignored) {}
            try {
                st.execute("ALTER TABLE movies CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            } catch (SQLException ignored) {}
            try {
                st.execute("ALTER TABLE movies ADD COLUMN cast_text VARCHAR(2000)");
            } catch (SQLException ignored) {}
            try {
                st.execute("UPDATE movies SET cast_text = `cast` WHERE cast_text IS NULL AND `cast` IS NOT NULL");
            } catch (SQLException ignored) {}
            try {
                st.execute("UPDATE movies SET cast_text = \"cast\" WHERE cast_text IS NULL AND \"cast\" IS NOT NULL");
            } catch (SQLException ignored) {}
            try {
                st.execute("ALTER TABLE movies DROP COLUMN `cast`");
            } catch (SQLException ignored) {}
            try {
                st.execute("ALTER TABLE movies DROP COLUMN \"cast\"");
            } catch (SQLException ignored) {}
            try {
                st.execute("ALTER TABLE movies MODIFY title VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            } catch (SQLException ignored) {}
            try {
                st.execute("ALTER TABLE movies MODIFY movie_id VARCHAR(64) NULL");
            } catch (SQLException ignored) {}
            try {
                st.execute("ALTER TABLE movies ALTER COLUMN movie_id VARCHAR(64)");
            } catch (SQLException ignored) {}
            try {
                st.execute("ALTER TABLE movies ADD INDEX idx_tmdb_id (tmdb_id)");
            } catch (SQLException ignored) {}
            try {
                st.execute("UPDATE show_times s JOIN movies m ON s.movie_id = m.id SET s.movie_title = m.title WHERE s.movie_title IS NULL OR s.movie_title = '' OR s.movie_title LIKE '%?%'");
            } catch (SQLException ignored) {}
            try {
                st.execute("UPDATE payments p JOIN show_times s ON p.showtime_id = s.id JOIN movies m ON s.movie_id = m.id SET p.movie_title = m.title WHERE p.movie_title IS NULL OR p.movie_title = '' OR p.movie_title LIKE '%?%'");
            } catch (SQLException ignored) {}
            try {
                st.execute("UPDATE payments p JOIN show_times s ON p.showtime_id = s.id SET p.movie_title = s.movie_title WHERE p.movie_title IS NULL OR p.movie_title = '' OR p.movie_title LIKE '%?%'");
            } catch (SQLException ignored) {}

            // Unique email (case-insensitive) via generated column
            try {
                st.execute("ALTER TABLE users ADD COLUMN email_lower VARCHAR(255) GENERATED ALWAYS AS (LOWER(email)) STORED");
            } catch (SQLException ignored) {}
            try {
                st.execute("CREATE UNIQUE INDEX uq_users_email_lower ON users (email_lower)");
            } catch (SQLException ignored) {}
        } catch (Exception ignored) {}
    }
}
