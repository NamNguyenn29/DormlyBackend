package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.ticket.TicketAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, UUID> {

    List<TicketAttachment> findByTicket_Id(UUID ticketId);

    List<TicketAttachment> findByComment_Id(UUID commentId);

    List<TicketAttachment> findByTicket_IdAndCommentIsNotNull(UUID ticketId);

    List<TicketAttachment> findByTicket_IdAndCommentIsNull(UUID ticketId);

    @Query("""
            SELECT a FROM TicketAttachment a
            JOIN FETCH a.ticket t
            JOIN FETCH t.reporter
            WHERE a.storedName = :storedName
            """)
    Optional<TicketAttachment> findByStoredNameWithTicket(@Param("storedName") String storedName);

    @Query("SELECT COUNT(a) FROM TicketAttachment a WHERE a.ticket.id = :ticketId AND a.comment IS NULL")
    long countTicketLevelAttachments(@Param("ticketId") UUID ticketId);

    @Query("SELECT COUNT(a) FROM TicketAttachment a WHERE a.comment.id = :commentId")
    long countByComment(@Param("commentId") UUID commentId);
}
