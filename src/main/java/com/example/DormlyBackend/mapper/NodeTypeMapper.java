package com.example.DormlyBackend.mapper;

import com.example.DormlyBackend.dto.request.NodeTypeRequest;
import com.example.DormlyBackend.dto.response.NodeTypeResponseDto;
import com.example.DormlyBackend.entity.building.NodeType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface NodeTypeMapper {

    @Mapping(source = "auditMetaData.createdAt", target = "createdAt")
    @Mapping(source = "auditMetaData.updatedAt", target = "updatedAt")
    @Mapping(source = "auditMetaData.createdBy", target = "createdBy")
    @Mapping(source = "auditMetaData.updatedBy", target = "updatedBy")
    NodeTypeResponseDto toDto(NodeType nodeType);

    NodeType toEntity(NodeTypeRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "auditMetaData", ignore = true)
    void updateNodeTypeFromRequest(@MappingTarget NodeType nodeType, NodeTypeRequest request);
}
