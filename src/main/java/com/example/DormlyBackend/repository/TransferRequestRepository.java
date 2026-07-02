package com.example.DormlyBackend.repository;

import com.example.DormlyBackend.entity.building.TransferRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TransferRequestRepository extends JpaRepository<TransferRequest, UUID> {
}
