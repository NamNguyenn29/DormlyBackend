package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.building.NodeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NodeTypeRepository extends JpaRepository<NodeType, UUID> {

    Optional<NodeType> findByName(String name);
}
