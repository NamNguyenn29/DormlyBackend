package com.example.DormlyBackend.service;

import com.example.DormlyBackend.dto.request.NodeTypeRequest;
import com.example.DormlyBackend.dto.response.NodeTypeResponseDto;
import com.example.DormlyBackend.entity.building.NodeType;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.mapper.NodeTypeMapper;
import com.example.DormlyBackend.repository.NodeTypeRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class NodeTypeService {

    private final NodeTypeRepository nodeTypeRepository;
    private final NodeTypeMapper nodeTypeMapper;

    public NodeTypeResponseDto create(NodeTypeRequest request) {
        nodeTypeRepository.findByName(request.getName())
                .ifPresent(existing -> {
                    throw ExceptionFactory.business(ErrorCode.INVALID_REQUEST,
                            "NodeType already exists: " + request.getName());
                });

        NodeType nodeType = nodeTypeMapper.toEntity(request);
        nodeType = nodeTypeRepository.save(nodeType);
        return nodeTypeMapper.toDto(nodeType);
    }

    @Transactional(readOnly = true)
    public NodeTypeResponseDto getById(UUID id) {
        NodeType nodeType = nodeTypeRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "NodeType", id));
        return nodeTypeMapper.toDto(nodeType);
    }

    public NodeTypeResponseDto update(UUID id, NodeTypeRequest request) {
        NodeType nodeType = nodeTypeRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "NodeType", id));

        nodeTypeMapper.updateNodeTypeFromRequest(nodeType, request);
        nodeType = nodeTypeRepository.save(nodeType);
        return nodeTypeMapper.toDto(nodeType);
    }

    public void delete(UUID id) {
        NodeType nodeType = nodeTypeRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "NodeType", id));
        nodeTypeRepository.delete(nodeType);
    }

    @Transactional(readOnly = true)
    public List<NodeTypeResponseDto> list() {
        return nodeTypeRepository.findAll().stream().map(nodeTypeMapper::toDto).toList();
    }
}
