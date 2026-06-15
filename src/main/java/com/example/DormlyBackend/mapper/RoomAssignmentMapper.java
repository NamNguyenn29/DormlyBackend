package com.example.DormlyBackend.mapper;

import com.example.DormlyBackend.dto.request.RoomAssignmentRequest;
import com.example.DormlyBackend.dto.response.RoomAssignmentResponseDto;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.building.BuildingNode;
import com.example.DormlyBackend.entity.building.RoomAssignment;
import org.springframework.stereotype.Component;

@Component
public class RoomAssignmentMapper {

    public RoomAssignmentResponseDto toDto(RoomAssignment entity) {
        if (entity == null) {
            return null;
        }

        return RoomAssignmentResponseDto.builder()
                .id(entity.getId())
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .roomNodeId(entity.getRoomNode() != null ? entity.getRoomNode().getId() : null)
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .assignedBy(entity.getAssignedBy())
                .contractUrl(entity.getContractUrl())
                .notes(entity.getNotes())
                .auditMetaData(entity.getAuditMetaData())
                .build();
    }

    public RoomAssignment toEntity(RoomAssignmentRequest request) {
        if (request == null) {
            return null;
        }

        RoomAssignment entity = new RoomAssignment();
        entity.setStartDate(request.getStartDate());
        entity.setEndDate(request.getEndDate());
        entity.setAssignedBy(request.getAssignedBy());
        entity.setContractUrl(request.getContractUrl());
        entity.setNotes(request.getNotes());
        return entity;
    }

    public void updateRoomAssignmentFromRequest(RoomAssignment entity, RoomAssignmentRequest request) {
        if (request == null || entity == null) {
            return;
        }

        if (request.getStartDate() != null) {
            entity.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            entity.setEndDate(request.getEndDate());
        }
        if (request.getAssignedBy() != null) {
            entity.setAssignedBy(request.getAssignedBy());
        }
        if (request.getContractUrl() != null) {
            entity.setContractUrl(request.getContractUrl());
        }
        if (request.getNotes() != null) {
            entity.setNotes(request.getNotes());
        }
    }

    public void attachRelations(RoomAssignment entity, User user, BuildingNode roomNode) {
        if (entity == null)
            return;
        entity.setUser(user);
        entity.setRoomNode(roomNode);
    }
}
