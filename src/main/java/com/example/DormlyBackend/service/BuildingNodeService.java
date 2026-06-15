package com.example.DormlyBackend.service;

import com.example.DormlyBackend.dto.request.BuildingNodeRequest;
import com.example.DormlyBackend.dto.response.BuildingNodeResponseDto;
import com.example.DormlyBackend.entity.building.BuildingNode;
import com.example.DormlyBackend.entity.building.NodeType;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.mapper.BuildingNodeMapper;
import com.example.DormlyBackend.repository.BuildingNodeRepository;
import com.example.DormlyBackend.repository.NodeTypeRepository;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BuildingNodeService {

    private final BuildingNodeRepository buildingNodeRepository;
    private final NodeTypeRepository nodeTypeRepository;
    private final BuildingNodeMapper buildingNodeMapper;

    public BuildingNodeResponseDto create(BuildingNodeRequest request) {
        BuildingNode node = buildingNodeMapper.toEntity(request);

        if (request.getParentId() != null) {
            UUID parentId = parseUuid(request.getParentId(), "parentId");
            BuildingNode parent = buildingNodeRepository.findById(parentId)
                    .orElseThrow(
                            () -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "BuildingNode", parentId));
            node.setParent(parent);
        } else {
            node.setParent(null);
        }

        UUID nodeTypeId = parseUuid(request.getNodeTypeId(), "nodeTypeId");
        NodeType nodeType = nodeTypeRepository.findById(nodeTypeId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "NodeType", nodeTypeId));
        node.setNodeType(nodeType);

        // Ensure server-side defaults consistent with entity default currentOccupancy
        if (node.getCurrentOccupancy() == null) {
            node.setCurrentOccupancy(0L);
        }

        if (node.getStatus() == null) {
            node.setStatus("ENABLE");
        }

        node = buildingNodeRepository.save(node);
        return buildingNodeMapper.toDto(node);
    }

    @Transactional(readOnly = true)
    public BuildingNodeResponseDto getById(UUID id) {
        BuildingNode node = buildingNodeRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "BuildingNode", id));

        // node-only response: do NOT include children here
        BuildingNodeResponseDto dto = buildingNodeMapper.toDto(node);
        dto.setChildren(null);
        return dto;
    }

    public BuildingNodeResponseDto update(UUID id, BuildingNodeRequest request) {
        BuildingNode node = buildingNodeRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "BuildingNode", id));

        buildingNodeMapper.updateBuildingNodeFromRequest(node, request);

        if (request.getParentId() != null) {
            UUID parentId = parseUuid(request.getParentId(), "parentId");
            BuildingNode parent = buildingNodeRepository.findById(parentId)
                    .orElseThrow(
                            () -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "BuildingNode", parentId));
            node.setParent(parent);
        } else {
            node.setParent(null);
        }

        UUID nodeTypeId = parseUuid(request.getNodeTypeId(), "nodeTypeId");
        NodeType nodeType = nodeTypeRepository.findById(nodeTypeId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "NodeType", nodeTypeId));
        node.setNodeType(nodeType);

        if (node.getCurrentOccupancy() == null) {
            node.setCurrentOccupancy(0L);
        }

        node = buildingNodeRepository.save(node);
        BuildingNodeResponseDto dto = buildingNodeMapper.toDto(node);
        dto.setChildren(null);
        return dto;
    }

    public void delete(UUID id) {
        BuildingNode node = buildingNodeRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "BuildingNode", id));
        buildingNodeRepository.delete(node);
    }

    @Transactional(readOnly = true)
    public List<BuildingNodeResponseDto> list() {
        return buildingNodeRepository.findAll().stream().map(this::toNodeOnlyDto).toList();
    }

    // /api/building-nodes/tree/{node-level}
    @Transactional(readOnly = true)
    public List<BuildingNodeResponseDto> getTreeByNodeLevel(int nodeLevel) {
        List<BuildingNode> roots = buildingNodeRepository.findByNodeType_Level(nodeLevel);
        if (roots == null || roots.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> visited = new HashSet<>();
        return roots.stream().map(r -> toTreeRecursive(r, visited)).collect(Collectors.toList());
    }

    private BuildingNodeResponseDto toNodeOnlyDto(BuildingNode node) {
        BuildingNodeResponseDto dto = buildingNodeMapper.toDto(node);
        dto.setChildren(null);
        return dto;
    }

    private BuildingNodeResponseDto toTreeRecursive(BuildingNode node, Set<UUID> visited) {
        if (node == null)
            return null;

        UUID id = node.getId();
        if (id != null && !visited.add(id)) {
            // cycle-safety
            return BuildingNodeResponseDto.builder().id(id).build();
        }

        BuildingNodeResponseDto dto = buildingNodeMapper.toDto(node);
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            dto.setChildren(null);
            return dto;
        }

        Set<BuildingNodeResponseDto> childrenDtos = node.getChildren().stream()
                .map(child -> toTreeRecursive(child, visited))
                .collect(Collectors.toCollection(HashSet::new));
        dto.setChildren(childrenDtos);
        return dto;
    }

    private UUID parseUuid(String value, String fieldName) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            // using INVALID_REQUEST for simplicity since no dedicated validation error code
            // exists here
            throw ExceptionFactory.validation(
                    java.util.List.of(new com.example.DormlyBackend.exception.model.ValidationException.FieldError(
                            fieldName, "Invalid UUID", value)));
        }
    }
}
