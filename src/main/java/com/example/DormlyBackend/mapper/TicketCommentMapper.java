package com.example.DormlyBackend.mapper;

import com.example.DormlyBackend.dto.response.TicketAttachmentResponseDto;
import com.example.DormlyBackend.dto.response.TicketCommentResponseDto;
import com.example.DormlyBackend.entity.ticket.TicketComment;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", uses = TicketAttachmentMapper.class)
public interface TicketCommentMapper {

    default TicketCommentResponseDto toDto(TicketComment entity, List<TicketAttachmentResponseDto> attachments) {
        if (entity == null) {
            return null;
        }
        return TicketCommentResponseDto.builder()
                .id(entity.getId())
                .authorId(entity.getAuthor() == null ? null : entity.getAuthor().getId())
                .authorName(entity.getAuthor() == null ? null : entity.getAuthor().getFullName())
                .body(entity.getBody())
                .internal(entity.isInternal())
                .attachments(attachments == null ? List.of() : attachments)
                .createdAt(entity.getAuditMetaData() == null ? null : entity.getAuditMetaData().getCreatedAt())
                .build();
    }
}
