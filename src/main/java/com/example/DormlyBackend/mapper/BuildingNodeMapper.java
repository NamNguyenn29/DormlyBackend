package com.example.DormlyBackend.mapper;

import com.example.DormlyBackend.dto.request.BuildingNodeRequest;
import com.example.DormlyBackend.dto.response.BuildingNodeResponseDto;
import com.example.DormlyBackend.entity.building.BuildingNode;
import com.example.DormlyBackend.entity.building.NodeType;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface BuildingNodeMapper {

    @Mapping(source = "auditMetaData.createdAt", target = "createdAt")
    @Mapping(source = "auditMetaData.updatedAt", target = "updatedAt")
    @Mapping(source = "auditMetaData.createdBy", target = "createdBy")
    @Mapping(source = "auditMetaData.updatedBy", target = "updatedBy")
    @Mapping(source = "parent.id", target = "parentId")
    @Mapping(source = "nodeType.id", target = "nodeTypeId")
    BuildingNodeResponseDto toDto(BuildingNode buildingNode);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "nodeType", ignore = true)
    @Mapping(target = "children", ignore = true)
    @Mapping(target = "auditMetaData", ignore = true)
    BuildingNode toEntity(BuildingNodeRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "nodeType", ignore = true)
    @Mapping(target = "children", ignore = true)
    @Mapping(target = "auditMetaData", ignore = true)
    void updateBuildingNodeFromRequest(@MappingTarget BuildingNode buildingNode, BuildingNodeRequest request);

    default NodeType mapNodeTypeId(String nodeTypeId) {
        if (nodeTypeId == null)
            return null;
        UUID id = UUID.fromString(nodeTypeId);
        NodeType nt = new NodeType();
        nt.setId(id);
        return nt;
    }
}
