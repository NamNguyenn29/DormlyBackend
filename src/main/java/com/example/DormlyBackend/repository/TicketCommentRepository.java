package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.ticket.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketCommentRepository extends JpaRepository<TicketComment, UUID> {

    @Query("""
            SELECT c FROM TicketComment c
            JOIN FETCH c.author
            WHERE c.ticket.id = :ticketId
              AND (:includeInternal = TRUE OR c.internal = FALSE)
            ORDER BY c.auditMetaData.createdAt ASC
            """)
    List<TicketComment> findByTicket(@Param("ticketId") UUID ticketId,
                                     @Param("includeInternal") boolean includeInternal);
}
