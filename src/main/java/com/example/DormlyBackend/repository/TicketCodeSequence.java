package com.example.DormlyBackend.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

@Component
public class TicketCodeSequence {

    @PersistenceContext
    private EntityManager em;

    /**
     * Reads the next value from ticket_code_seq. Non-transactional by design:
     * a rolled-back create burns its number and leaves a gap, which is expected.
     */
    public long next() {
        Object value = em.createNativeQuery("SELECT NEXT VALUE FOR ticket_code_seq").getSingleResult();
        return ((Number) value).longValue();
    }
}
