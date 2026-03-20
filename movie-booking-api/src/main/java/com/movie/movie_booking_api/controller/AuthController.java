// src/main/java/com/movie/movie_booking_api/controller/AuthController.java
package com.movie.movie_booking_api.controller;

import com.movie.movie_booking_api.dto.AuthResponse;
import com.movie.movie_booking_api.dto.LoginRequest;
import com.movie.movie_booking_api.dto.RegisterRequest;
import com.movie.movie_booking_api.dto.ChangePasswordRequest;
import com.movie.movie_booking_api.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping(value = "/register", produces = "application/json; charset=utf-8")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/login", produces = "application/json; charset=utf-8")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot")
    public ResponseEntity<?> forgot(@RequestBody java.util.Map<String, String> req) {
        String email = req.getOrDefault("email", "");
        email = email == null ? "" : email.trim();
        if (email.isEmpty()) {
            return ResponseEntity.badRequest().body("Invalid email");
        }
        return ResponseEntity.status(202).body("OK");
    }

    @GetMapping(value = "/verify", produces = "text/html; charset=utf-8")
    public ResponseEntity<String> verify(@RequestParam("token") String token) {
        authService.verifyEmail(token);
        String html = """
                <!DOCTYPE html>
                <html lang="vi"><head><meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <title>Xác minh thành công</title>
                <style>
                  :root{--bg:#0b1220;--text:#e8eef7;--accent:#0ea5e9;}
                  body{background:var(--bg);color:var(--text);font-family:system-ui,-apple-system,Segoe UI,Roboto,Arial,sans-serif;margin:0;padding:0;display:flex;align-items:center;justify-content:center;min-height:100vh}
                  .card{max-width:520px;width:92%;background:#0e1528;border-radius:18px;box-shadow:0 20px 40px rgba(14,165,233,.25);padding:24px;text-align:center;border:1px solid rgba(255,255,255,.08)}
                  .title{font-size:20px;font-weight:800;margin-bottom:8px}
                  .desc{opacity:.9;margin-bottom:18px}
                  .cta{display:inline-block;background:linear-gradient(180deg,#0ea5e9,#0284c7);color:#fff;text-decoration:none;padding:12px 18px;border-radius:10px;font-weight:700}
                </style></head>
                <body>
                  <div class="card">
                    <div class="title">Xác minh thành công</div>
                    <div class="desc">Email của bạn đã được xác minh. Bạn có thể đăng nhập để sử dụng tài khoản.</div>
                    <a class="cta" href="/site/auth.html?mode=login">Đăng nhập</a>
                  </div>
                </body></html>
                """;
        return ResponseEntity.ok(html);
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resend(@RequestBody java.util.Map<String, String> req) {
        String email = String.valueOf(req.getOrDefault("email", "")).trim();
        if (email.isEmpty()) {
            return ResponseEntity.badRequest().body("Invalid email");
        }
        authService.resendVerification(email);
        return ResponseEntity.status(202).body(java.util.Map.of("message", "Verification email sent if account exists and not verified"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(Authentication authentication,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(authentication.getName(), request);
        return ResponseEntity.ok().build();
    }
}
