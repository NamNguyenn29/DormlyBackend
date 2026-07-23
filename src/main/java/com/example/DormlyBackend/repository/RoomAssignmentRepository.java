package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.building.RoomAssignment;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomAssignmentRepository extends JpaRepository<RoomAssignment, UUID> {

    List<RoomAssignment> findByUser_Id(UUID userId);

    @Query("""
            SELECT ra
            FROM RoomAssignment ra
            WHERE ra.roomNode.id = :roomId
              AND ra.endDate IS NULL
            """)
    List<RoomAssignment> findActiveByRoomId(@Param("roomId") UUID roomId);

    @Query("""
            SELECT ra
            FROM RoomAssignment ra
            WHERE ra.user.id = :userId
              AND ra.roomNode.id = :roomId
              AND ra.endDate IS NULL
            """)
    Optional<RoomAssignment> findActiveByUserAndRoom(
            @Param("userId") UUID userId,
            @Param("roomId") UUID roomId);

    @Query("""
            SELECT ra
            FROM RoomAssignment ra
            WHERE ra.user.id = :userId
              AND ra.endDate IS NULL
            """)
    List<RoomAssignment> findActiveByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT ra
            FROM RoomAssignment ra
            WHERE ra.roomNode.id = :roomId
              AND ra.startDate <= :at
              AND (ra.endDate IS NULL OR ra.endDate > :at)
            """)
    List<RoomAssignment> findOccupancyAt(
            @Param("roomId") UUID roomId,
            @Param("at") LocalDateTime at);

    @Query("""
            SELECT ra
            FROM RoomAssignment ra
            WHERE ra.user.id = :userId
              AND ra.startDate <= :at
              AND (ra.endDate IS NULL OR ra.endDate > :at)
            ORDER BY ra.startDate DESC
            """)
    Optional<RoomAssignment> findCurrentByUserIdAt(
            @Param("userId") UUID userId,
            @Param("at") LocalDateTime at);

    @Query("""
            SELECT ra
            FROM RoomAssignment ra
            WHERE ra.user.id = :userId
            ORDER BY ra.startDate DESC
            """)
    List<RoomAssignment> findHistoryByUserId(
            @Param("userId") UUID userId);

    @Query("""
            SELECT ra
            FROM RoomAssignment ra
            JOIN FETCH ra.user
            WHERE ra.roomNode.id IN :roomIds
              AND ra.endDate IS NULL
            """)
    List<RoomAssignment> findActiveByRoomIds(@Param("roomIds") List<UUID> roomIds);

    @Query("""
            SELECT ra
            FROM RoomAssignment ra
            JOIN FETCH ra.user
            WHERE ra.roomNode.id IN :roomIds
              AND (:endDate IS NULL OR ra.startDate < :endDate)
              AND (ra.endDate IS NULL OR ra.endDate > :startDate)
            """)
    List<RoomAssignment> findOverlappingByRoomIds(
            @Param("roomIds") List<UUID> roomIds,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
