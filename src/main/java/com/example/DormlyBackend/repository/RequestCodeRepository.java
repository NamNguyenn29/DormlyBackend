package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.authentication.RequestCode;
import com.example.DormlyBackend.enums.PurposeCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RequestCodeRepository extends JpaRepository<RequestCode, Long> {
    Optional<RequestCode> findTopByRecipientContactAndPurposeOrderByExpiryTimeDesc(
            String recipientContact,
            PurposeCode purpose
    );

    void deleteByRecipientContactAndPurpose(String recipientContact, PurposeCode purpose);
}
