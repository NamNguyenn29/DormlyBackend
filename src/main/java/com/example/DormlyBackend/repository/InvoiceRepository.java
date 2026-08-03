package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.building.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @Query("SELECT i FROM Invoice i JOIN FETCH i.roomAssignment ra JOIN FETCH ra.user u JOIN FETCH ra.roomNode rn WHERE u.id = :userId")
    List<Invoice> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT i FROM Invoice i JOIN FETCH i.roomAssignment ra JOIN FETCH ra.user u JOIN FETCH ra.roomNode rn WHERE rn.id = :roomId")
    List<Invoice> findByRoomId(@Param("roomId") UUID roomId);
}
