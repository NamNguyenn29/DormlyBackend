package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.building.BuildingNode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BuildingNodeRepository extends JpaRepository<BuildingNode, UUID> {

    List<BuildingNode> findByNodeType_Level(int level);

    List<BuildingNode> findByParentIsNull();
}
