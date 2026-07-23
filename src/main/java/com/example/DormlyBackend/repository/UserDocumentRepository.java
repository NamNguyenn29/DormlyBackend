package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.information.UserDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDocumentRepository extends JpaRepository<UserDocument, UUID> {
    List<UserDocument> findAllByUserId(UUID userId);

    // In repository
    @Query("SELECT d FROM UserDocument d JOIN FETCH d.user WHERE d.fileUrl = :fileUrl")
    Optional<UserDocument> findByFileUrlWithUser(@Param("fileUrl") String fileUrl);

    @Query("SELECT d FROM UserDocument d JOIN FETCH d.user WHERE d.fileUrl LIKE CONCAT('%', :fileSuffix)")
    Optional<UserDocument> findByFileUrlWithUserBySuffix(@Param("fileSuffix") String fileSuffix);
}
