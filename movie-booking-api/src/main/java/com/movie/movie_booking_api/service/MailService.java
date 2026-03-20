package com.movie.movie_booking_api.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;

@Service
public class MailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    @org.springframework.scheduling.annotation.Async
    public void sendTicketEmail(String to, com.movie.movie_booking_api.entity.Booking booking, com.movie.movie_booking_api.entity.ShowTime showTime) {
        if (mailSender == null) {
            org.slf4j.LoggerFactory.getLogger(MailService.class).warn("Mail sender not configured, skip ticket email to {}", to);
            return;
        }
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, java.nio.charset.StandardCharsets.UTF_8.name());
            helper.setTo(to);
            if (from != null && !from.isEmpty()) {
                helper.setFrom(from, "MovieBooking");
            }
            helper.setSubject("Vé xem phim đã đặt thành công");
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
            String timeText = showTime.getStartTime() == null ? "" : fmt.format(showTime.getStartTime());
            String cinema = String.valueOf(showTime.getCinema() == null ? "" : showTime.getCinema());
            String room = String.valueOf(showTime.getRoom() == null ? "" : showTime.getRoom());
            String seatsStr = (booking.getSeats() == null ? "" : String.join(", ", booking.getSeats()));
            java.text.NumberFormat nf = java.text.NumberFormat.getInstance(new java.util.Locale("vi", "VN"));
            String totalStr = nf.format(booking.getTotalPrice() == null ? 0 : booking.getTotalPrice()) + "đ";
            String qrText = 
                    "Vé xem phim: " + (showTime.getMovieTitle() == null ? "" : showTime.getMovieTitle()) + "\n" +
                    "Mã vé: " + (booking.getCode() == null ? String.valueOf(booking.getId()) : booking.getCode()) + "\n" +
                    "Suất: " + timeText + (cinema.isEmpty() ? "" : " - " + cinema) + (room.isEmpty() ? "" : " - Phòng " + room) + "\n" +
                    "Ghế: " + seatsStr + "\n" +
                    "Tổng tiền: " + totalStr;
            String html = """
                    <div style="font-family:Arial,sans-serif;color:#111827">
                      <div style="font-size:16px;font-weight:600;margin-bottom:6px">Vé xem phim đã đặt thành công</div>
                      <div style="margin:8px 0">Phim: <strong>%s</strong></div>
                      <div style="margin:4px 0">Mã vé: <strong>%s</strong></div>
                      <div style="margin:4px 0">Suất: <strong>%s</strong></div>
                      <div style="margin:4px 0">Rạp: <strong>%s</strong></div>
                      <div style="margin:4px 0">Phòng: <strong>%s</strong></div>
                      <div style="margin:4px 0">Ghế: <strong>%s</strong></div>
                      <div style="margin:4px 0">Tổng tiền: <strong>%s</strong></div>
                      <div style="margin-top:12px">
                        <div style="font-size:14px;margin-bottom:6px">Mã QR của vé:</div>
                        <img src="cid:qr" alt="QR Code" style="width:220px;height:220px;border:1px solid #e5e7eb;border-radius:10px" />
                      </div>
                    </div>
                    """.formatted(
                    showTime.getMovieTitle() == null ? "" : showTime.getMovieTitle(),
                    booking.getCode() == null ? String.valueOf(booking.getId()) : booking.getCode(),
                    timeText,
                    cinema,
                    room,
                    seatsStr,
                    totalStr
            );
            helper.setText(html, true);

            com.google.zxing.qrcode.QRCodeWriter writer = new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix matrix = writer.encode(qrText, com.google.zxing.BarcodeFormat.QR_CODE, 300, 300);
            java.awt.image.BufferedImage image = com.google.zxing.client.j2se.MatrixToImageWriter.toBufferedImage(matrix);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "PNG", baos);
            byte[] qrBytes = baos.toByteArray();
            try {
                helper.addInline("qr", new ByteArrayResource(qrBytes), "image/png");
            } catch (Exception inlineErr) {
                try {
                    helper.addAttachment("ticket-qr.png", new ByteArrayResource(qrBytes));
                } catch (Exception attachErr) {}
            }
            
            mailSender.send(mimeMessage);
            org.slf4j.LoggerFactory.getLogger(MailService.class).info("Sent ticket email to {}", to);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(MailService.class).error("Failed to send ticket email to {}: {}", to, e.getMessage());
        }
    }

    @org.springframework.scheduling.annotation.Async
    public void sendPasswordResetEmail(String to, String resetLink) {
        if (mailSender == null) {
            org.slf4j.LoggerFactory.getLogger(MailService.class).warn("Mail sender not configured, skip password reset email to {}", to);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            if (from != null && !from.isEmpty()) {
                message.setFrom("MovieBooking <" + from + ">");
            }
            message.setSubject("Password Reset");
            message.setText("Click the link to reset your password: " + resetLink);
            mailSender.send(message);
            org.slf4j.LoggerFactory.getLogger(MailService.class).info("Sent password reset email to {}", to);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(MailService.class).error("Failed to send password reset email to {}: {}", to, e.getMessage());
        }
    }

    @org.springframework.scheduling.annotation.Async
    public void sendVerificationEmail(String to, String verifyLink) {
        if (mailSender == null) {
            org.slf4j.LoggerFactory.getLogger(MailService.class).warn("Mail sender not configured, skip verification email to {}", to);
            return;
        }
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, java.nio.charset.StandardCharsets.UTF_8.name());
            helper.setTo(to);
            if (from != null && !from.isEmpty()) {
                helper.setFrom(from, "MovieBooking");
            }
            helper.setSubject("Xác minh email tài khoản Movie Booking");
            String html = """
                    <div style="font-family:Arial,sans-serif;font-size:14px;color:#222;">
                      <p>Nhấp vào link để xác nhận email: <a href="%s">Xác nhận</a></p>
                    </div>
                    """.formatted(verifyLink);
            helper.setText(html, true);
            mailSender.send(mimeMessage);
            org.slf4j.LoggerFactory.getLogger(MailService.class).info("Sent verification email to {}", to);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(MailService.class).error("Failed to send verification email to {}: {}", to, e.getMessage());
        }
    }
}
