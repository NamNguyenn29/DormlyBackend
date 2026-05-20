package com.example.DormlyBackend.service.notification;

import com.example.DormlyBackend.dto.notification.NotificationEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailSender {
    private final JavaMailSender mailSender;

    @NonFinal
    @Value("${spring.mail.username}")
    String fromEmail;

    public void send(NotificationEvent event) throws MessagingException {
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(event.getRecipient());
        helper.setSubject(event.getSubject());

        String body = event.getMessage();

        helper.setText(body, true); // true = HTML
        mailSender.send(mime);
        log.info("[EMAIL] Sent to={} subject={}", event.getRecipient(), event.getSubject());
    }

    public void sendRegistrationCode(String toEmail, String code) throws MessagingException {

        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(toEmail);
        helper.setSubject("Dormly Registration Verification Code");

        String body = """
            <div style="font-family: Arial, sans-serif; padding: 20px;">
                <h2 style="color:#2563eb;">Dormly Email Verification</h2>

                <p>Your verification code is:</p>

                <div style="
                    font-size:32px;
                    font-weight:bold;
                    letter-spacing:6px;
                    color:#111827;
                    margin:20px 0;
                ">
                    %s
                </div>

                <p>This code will expire in 10 minutes.</p>

                <p>If you did not request this code, please ignore this email.</p>
            </div>
            """.formatted(code);

        helper.setText(body, true);

        mailSender.send(mime);

        log.info("[EMAIL] Registration code sent to={}", toEmail);
    }
}
