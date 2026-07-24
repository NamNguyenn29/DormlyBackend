package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.ticket.Ticket;
import com.example.DormlyBackend.enums.TicketCategory;
import com.example.DormlyBackend.enums.TicketPriority;
import com.example.DormlyBackend.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    @Query("""
            SELECT t FROM Ticket t
            WHERE t.reporter.id = :reporterId
              AND (:status IS NULL OR t.status = :status)
            ORDER BY t.auditMetaData.createdAt DESC
            """)
    List<Ticket> findByReporter(@Param("reporterId") UUID reporterId,
                                @Param("status") TicketStatus status);

    @Query("""
            SELECT t FROM Ticket t
            WHERE t.id = :ticketId AND t.reporter.id = :reporterId
            """)
    Optional<Ticket> findByIdAndReporter(@Param("ticketId") UUID ticketId,
                                         @Param("reporterId") UUID reporterId);

    @Query("""
            SELECT t FROM Ticket t
            WHERE (:status IS NULL OR t.status = :status)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:category IS NULL OR t.category = :category)
              AND (:reporterId IS NULL OR t.reporter.id = :reporterId)
              AND (:code IS NULL OR t.code = :code)
              AND (:assigneeId IS NULL OR EXISTS (
                    SELECT 1 FROM Ticket t2 JOIN t2.assignees a
                    WHERE t2.id = t.id AND a.id = :assigneeId))
              AND (:overdueOnly = FALSE OR (t.dueDate IS NOT NULL
                    AND t.dueDate < :today
                    AND t.status IN (com.example.DormlyBackend.enums.TicketStatus.OPEN,
                                     com.example.DormlyBackend.enums.TicketStatus.IN_PROGRESS)))
            ORDER BY t.auditMetaData.createdAt DESC
            """)
    Page<Ticket> search(@Param("status") TicketStatus status,
                        @Param("priority") TicketPriority priority,
                        @Param("category") TicketCategory category,
                        @Param("reporterId") UUID reporterId,
                        @Param("code") String code,
                        @Param("assigneeId") UUID assigneeId,
                        @Param("overdueOnly") boolean overdueOnly,
                        @Param("today") LocalDate today,
                        Pageable pageable);

    @Query("""
            SELECT t FROM Ticket t
            ORDER BY t.priority DESC, t.auditMetaData.createdAt DESC
            """)
    List<Ticket> findAllForBoard();

    /**
     * Mirrors TicketOverdueRule.shouldAlert. Keep the two in step.
     */
    @Query("""
            SELECT t FROM Ticket t
            LEFT JOIN FETCH t.assignees
            WHERE t.dueDate IS NOT NULL
              AND t.dueDate < :today
              AND t.status IN (com.example.DormlyBackend.enums.TicketStatus.OPEN,
                               com.example.DormlyBackend.enums.TicketStatus.IN_PROGRESS)
              AND (t.overdueAlertedAt IS NULL OR t.overdueAlertedAt < :remindBefore)
            """)
    List<Ticket> findOverdueCandidates(@Param("today") LocalDate today,
                                       @Param("remindBefore") LocalDateTime remindBefore);
}
