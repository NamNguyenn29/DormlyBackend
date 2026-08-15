package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.building.BuildingNode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BuildingNodeRepository extends JpaRepository<BuildingNode, UUID> {

    List<BuildingNode> findByNodeType_Level(int level);

    List<BuildingNode> findByParentIsNull();

    @Query("""
            SELECT b
            FROM BuildingNode b
            WHERE b.nodeType.level = :level
              AND UPPER(b.status) = :status
              AND b.currentOccupancy < b.maxCapacity
            """)
    List<BuildingNode> findAvailableRooms(
            @Param("level") int level,
            @Param("status") String status);

    @Query("""
            SELECT b
            FROM BuildingNode b
            WHERE b.nodeType.level = :level
              AND UPPER(b.status) = :status
            """)
    List<BuildingNode> findRoomsByLevelAndStatus(
            @Param("level") int level,
            @Param("status") String status);

    @Query("""
            SELECT DISTINCT b
            FROM BuildingNode b
            WHERE (b.nodeType.level = 3 OR UPPER(b.nodeType.name) = 'ROOM' OR (b.children IS EMPTY AND b.parent IS NOT NULL AND (b.maxCapacity IS NULL OR b.maxCapacity > 0)))
              AND (b.status IS NULL OR UPPER(b.status) IN ('AVAILABLE', 'ACTIVE', 'VACANT', 'OPEN'))
            """)
    List<BuildingNode> findCandidateRooms();
}
