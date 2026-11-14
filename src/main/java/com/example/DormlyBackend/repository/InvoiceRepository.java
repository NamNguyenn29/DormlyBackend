package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.building.Invoice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @Query("SELECT i FROM Invoice i JOIN FETCH i.roomAssignment ra JOIN FETCH ra.user u JOIN FETCH ra.roomNode rn LEFT JOIN FETCH rn.parent rnp WHERE u.id = :userId")
    List<Invoice> findByUserId(@Param("userId") UUID userId);

    /**
     * Loads an invoice with a write lock held until the surrounding transaction ends, so two staff
     * members confirming the same invoice at once are serialized and the second sees {@code PAID}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT i FROM Invoice i JOIN FETCH i.roomAssignment ra JOIN FETCH ra.user u JOIN FETCH ra.roomNode rn LEFT JOIN FETCH rn.parent rnp WHERE rn.id = :roomId")
    List<Invoice> findByRoomId(@Param("roomId") UUID roomId);

    @Query("SELECT i FROM Invoice i JOIN FETCH i.roomAssignment ra JOIN FETCH ra.user u JOIN FETCH ra.roomNode rn LEFT JOIN FETCH rn.parent rnp")
    List<Invoice> findAllInvoices();
}
