package com.example.DormlyBackend.repository;


import com.example.DormlyBackend.entity.notification.NotificationLog;
import com.example.DormlyBackend.enums.ChannelType;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    Optional<NotificationLog> findByEventId(String eventId);

    @Query("""
        SELECT n FROM NotificationLog n
        WHERE (:recipient IS NULL OR n.recipient = :recipient)
          AND (:channel   IS NULL OR n.channel   = :channel)
          AND (:status    IS NULL OR n.status    = :status)
        ORDER BY n.createdAt DESC
    """)
    Page<NotificationLog> findByFilters(
            @Param("recipient") String recipient,
            @Param("channel") ChannelType channel,
            @Param("status")    String status,
            Pageable pageable);

    @Query("""
        SELECT n FROM NotificationLog n
        WHERE n.status = 'FAILED'
          AND n.createdAt >= :since
          AND n.retryCount < :maxRetries
    """)
    List<NotificationLog> findRetryable(
            @Param("since") LocalDateTime since,
            @Param("maxRetries") int maxRetries);

    @Modifying
    @Query("UPDATE NotificationLog n SET n.status = :status, n.errorMessage = :err, n.processedAt = CURRENT_TIMESTAMP WHERE n.eventId = :eventId")
    @Transactional
    void updateStatus(@Param("eventId") String eventId,
                      @Param("status")  String status,
                      @Param("err")     String err);

    @Modifying
    @Query("UPDATE NotificationLog n SET n.retryCount = n.retryCount + 1 WHERE n.id = :id")
    void incrementRetryCount(@Param("id") Long id);
}
