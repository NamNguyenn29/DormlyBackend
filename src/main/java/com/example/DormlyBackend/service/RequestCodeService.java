package com.example.DormlyBackend.service;

import com.example.DormlyBackend.entity.authentication.RequestCode;
import com.example.DormlyBackend.enums.PurposeCode;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.repository.RequestCodeRepository;
import com.example.DormlyBackend.service.notification.EmailSender;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RequestCodeService {
    private final RequestCodeRepository requestCodeRepository;
    private final EmailSender emailSender;

    private static final SecureRandom random = new SecureRandom();

    public void sendRegisterCode(String email)   {
        String code = generate6DigitCode();
        RequestCode requestCode = new RequestCode();
        requestCode.setPurpose(PurposeCode.REGISTRATION);
        requestCode.setExpiryTime(LocalDateTime.now().plusMinutes(10));
        requestCode.setRecipientContact(email);
        requestCode.setCode(code);
        requestCodeRepository.save(requestCode);

        try {
            emailSender.sendRegistrationCode(email, code);
        } catch (MessagingException e) {
            throw  ExceptionFactory.business(ErrorCode.EMAIL_SEND_FAILED, e.getMessage());
        }
    }

    public void sendForgotPasswordCode(String email)   {
        String code = generate6DigitCode();
        RequestCode requestCode = new RequestCode();
        requestCode.setPurpose(PurposeCode.FORGOT_PASSWORD);
        requestCode.setExpiryTime(LocalDateTime.now().plusMinutes(10));
        requestCode.setRecipientContact(email);
        requestCode.setCode(code);
        requestCodeRepository.save(requestCode);

        try {
            emailSender.sendRegistrationCode(email, code);
        } catch (MessagingException e) {
            throw  ExceptionFactory.business(ErrorCode.EMAIL_SEND_FAILED, e.getMessage());
        }
    }

    private String generate6DigitCode() {
        int number = random.nextInt(1_000_000);
        return String.format("%06d", number);
    }



}
