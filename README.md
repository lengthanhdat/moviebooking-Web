# 🎬 Movie Booking API

[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Spring Boot 3.5.7](https://img.shields.io/badge/Spring%20Boot-3.5.7-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)
[![JWT](https://img.shields.io/badge/Authentication-JWT-purple.svg)](https://jwt.io/)
[![Swagger](https://img.shields.io/badge/API%20Documentation-Swagger/OpenAPI-green.svg)](https://swagger.io/)

A professional, full-stack Movie Booking System built with **Spring Boot 3** and **Java 17**. This project provides a robust RESTful API for managing movie schedules, seat availability in real-time, user bookings, and a comprehensive admin dashboard.

---
## 🖼️ Screenshots
<img width="1912" height="856" alt="Screenshot 2026-03-20 180540" src="https://github.com/user-attachments/assets/91f7ce0e-dd29-4142-ac82-2e990b398212" />
<img width="1910" height="899" alt="Screenshot 2026-03-20 180631" src="https://github.com/user-attachments/assets/849f883c-ccc5-4360-9ea2-a01e27bedcc9" />
<img width="1896" height="888" alt="Screenshot 2026-03-20 180721" src="https://github.com/user-attachments/assets/44dde690-e2fc-42a5-9ff6-fd380f812a46" />
<img width="1902" height="802" alt="Screenshot 2026-03-20 180742" src="https://github.com/user-attachments/assets/cf09f909-2e9f-40a0-bf65-bb9f7072d9f6" />
<img width="1917" height="905" alt="Screenshot 2025-12-18 235158" src="https://github.com/user-attachments/assets/a6b6471e-9d79-40dc-9b4a-fe0d3f457331" />
<img width="1903" height="901" alt="Screenshot 2025-12-18 235237" src="https://github.com/user-attachments/assets/ca37850c-4191-433e-8e96-9c4b63951916" />
<img width="1916" height="903" alt="Screenshot 2025-12-18 235302" src="https://github.com/user-attachments/assets/df4de474-dd05-4c4f-af5c-72d855dcffe9" />
<img width="1912" height="905" alt="Screenshot 2025-12-18 235416" src="https://github.com/user-attachments/assets/c9e73e1f-40b2-43ed-87f4-8749b792db78" />
<img width="1912" height="905" alt="Screenshot 2025-12-18 235416" src="https://github.com/user-attachments/assets/0e87dbb9-90e4-464b-8128-38309cff31cc" />
<img width="1897" height="904" alt="Screenshot 2025-12-18 235354" src="https://github.com/user-attachments/assets/ccf871df-4e4c-4600-ad25-b0e664760e0d" />
<img width="1908" height="907" alt="Screenshot 2025-12-19 141641" src="https://github.com/user-attachments/assets/8023eec4-ac5c-47f2-86f6-e6b08fbde11f" />
<img width="1908" height="907" alt="Screenshot 2025-12-19 141641" src="https://github.com/user-attachments/assets/57c6b54c-91ae-45f0-9bbb-99baf9dd6677" />

## 🚀 Công Nghệ Sử Dụng (Tech Stack)

### **Backend**
- **Framework:** [Spring Boot 3.5.7](https://spring.io/projects/spring-boot) - Nền tảng chính cho sự ổn định và hiệu suất cao.
- **Security:** [Spring Security](https://spring.io/projects/spring-security) & [JSON Web Token (JWT)](https://jwt.io/) - Bảo mật xác thực và phân quyền (Admin/User).
- **ORM:** [Spring Data JPA (Hibernate)](https://spring.io/projects/spring-data-jpa) - Quản lý cơ sở dữ liệu quan hệ một cách hiệu quả.
- **Real-time:** [Spring WebSocket](https://docs.spring.io/spring-framework/reference/web/websocket.html) - Cập nhật trạng thái ghế ngồi trực tiếp theo thời gian thực.
- **Database:** [MySQL](https://www.mysql.com/) - Lưu trữ dữ liệu phim, lịch chiếu, người dùng và đặt vé.
- **Caching:** [Redis](https://redis.io/) - Tối ưu hóa hiệu suất truy vấn (Hỗ trợ cấu hình cache đơn giản cho môi trường dev).
- **Email:** [Spring Mail](https://docs.spring.io/spring-boot/docs/current/reference/html/io.html#io.email) - Tự động gửi email xác nhận đặt vé và xác minh tài khoản.
- **Documentation:** [Swagger/OpenAPI 3](https://swagger.io/) - Tự động tạo tài liệu API chuyên nghiệp.
- **Tools:** [Lombok](https://projectlombok.org/), [Maven](https://maven.apache.org/), [Java 17](https://openjdk.org/projects/jdk/17/).

### **Frontend**
- **Web UI:** HTML5, CSS3, JavaScript (Vanilla) - Giao diện hiện đại, tối giản và phản hồi nhanh.
- **Admin Dashboard:** Hệ thống quản trị toàn diện cho phim, rạp, suất chiếu và doanh thu.
- **Features:** Tích hợp [QR Code](https://davidshimjs.github.io/qrcodejs/) cho vé điện tử, thanh toán mô phỏng.

### **Integrations**
- **TMDB API:** Tự động đồng bộ thông tin phim, poster và trailer từ [The Movie Database](https://www.themoviedb.org/).

---

## ✨ Tính Năng Nổi Bật

- **Hệ thống đặt ghế thời gian thực:** Sử dụng WebSocket để ngăn chặn việc đặt trùng ghế, mang lại trải nghiệm mượt mà như các ứng dụng rạp phim lớn.
- **Quản lý lịch chiếu thông minh:** Tự động cập nhật trạng thái phim và sắp xếp lịch chiếu linh hoạt.
- **Bảo mật đa tầng:** Xác thực qua JWT, phân quyền người dùng và quản trị viên chặt chẽ.
- **Hệ thống gửi Mail tự động:** Thông báo đặt vé thành công kèm mã vé qua email người dùng.
- **API Documentation:** Tài liệu API đầy đủ tại `/swagger-ui.html` giúp dễ dàng tích hợp và phát triển thêm.

---

## 🛠️ Cài Đặt (Installation)

1. **Clone repository:**
   ```bash
   git clone https://github.com/lengthanhdat/moviebooking-Web.git
   cd movie-booking-api
   ```

2. **Cấu hình Cơ sở dữ liệu:**
   - Tạo database `movie_booking` trong MySQL.
   - Cập nhật thông tin kết nối trong `application.properties`:
     ```properties
     spring.datasource.username=your_username
     spring.datasource.password=your_password
     ```

3. **Cấu hình Mail & TMDB (Tùy chọn):**
   - Thiết lập các biến môi trường cho `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` và `TMDB_API_KEY`.

4. **Chạy ứng dụng:**
   ```bash
   ./mvnw spring-boot:run
   ```

---

## 📖 API Endpoints (Swagger)

Sau khi chạy ứng dụng, bạn có thể truy cập tài liệu API tại:
- **Swagger UI:** `http://localhost:8081/swagger-ui/index.html`

---

## 👤 Tác Giả

- **LÊ NGUYỄN THÀNH ĐẠT** - *Developer* - [GitHub](https://github.com/lengthanhdat)

---

⭐ **Đừng quên để lại một ngôi sao nếu bạn thấy dự án này hữu ích!**
