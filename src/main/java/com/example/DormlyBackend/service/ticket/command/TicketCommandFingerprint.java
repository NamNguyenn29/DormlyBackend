package com.example.DormlyBackend.service.ticket.command;

import com.example.DormlyBackend.policy.ticket.TicketOperation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class TicketCommandFingerprint {

    private TicketCommandFingerprint() {}

    public static String hash(TicketOperation operation, String canonicalPayload, long expectedVersion, String evidenceFingerprint) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = (operation != null ? operation.name() : "") + "|"
                    + expectedVersion + "|"
                    + (canonicalPayload != null ? canonicalPayload : "") + "|"
                    + (evidenceFingerprint != null ? evidenceFingerprint : "");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
