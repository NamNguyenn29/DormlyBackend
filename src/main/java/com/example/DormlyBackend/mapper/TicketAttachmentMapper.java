package com.example.DormlyBackend.mapper;

import com.example.DormlyBackend.dto.response.TicketAttachmentResponseDto;
import com.example.DormlyBackend.entity.ticket.TicketAttachment;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface TicketAttachmentMapper {

    String URL_PREFIX = "/api/ticket-attachments/";

    default TicketAttachmentResponseDto toDto(TicketAttachment entity) {
        if (entity == null) {
            return null;
        }
        return TicketAttachmentResponseDto.builder()
                .id(entity.getId())
                .originalFilename(entity.getOriginalFilename())
                .contentType(entity.getContentType())
                .sizeBytes(entity.getSizeBytes())
                .url(URL_PREFIX + entity.getStoredName())
                .uploadedById(entity.getUploadedBy() == null ? null : entity.getUploadedBy().getId())
                .createdAt(entity.getAuditMetaData() == null ? null : entity.getAuditMetaData().getCreatedAt())
                .build();
    }

    default List<TicketAttachmentResponseDto> toDtoList(List<TicketAttachment> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(this::toDto).collect(Collectors.toList());
    }
}
