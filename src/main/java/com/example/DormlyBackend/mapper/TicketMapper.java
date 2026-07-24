package com.example.DormlyBackend.mapper;

import com.example.DormlyBackend.dto.response.TicketAssigneeResponseDto;
import com.example.DormlyBackend.dto.response.TicketAttachmentResponseDto;
import com.example.DormlyBackend.dto.response.TicketCommentResponseDto;
import com.example.DormlyBackend.dto.response.TicketDetailResponseDto;
import com.example.DormlyBackend.dto.response.TicketSummaryResponseDto;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.entity.ticket.Ticket;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface TicketMapper {

    default TicketAssigneeResponseDto toAssigneeDto(User user) {
        if (user == null) {
            return null;
        }
        return TicketAssigneeResponseDto.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .build();
    }

    default List<TicketAssigneeResponseDto> toAssigneeDtos(Ticket ticket) {
        if (ticket == null || ticket.getAssignees() == null) {
            return List.of();
        }
        return ticket.getAssignees().stream()
                .map(this::toAssigneeDto)
                .collect(Collectors.toList());
    }

    default TicketSummaryResponseDto toSummary(Ticket ticket) {
        if (ticket == null) {
            return null;
        }
        return TicketSummaryResponseDto.builder()
                .id(ticket.getId())
                .code(ticket.getCode())
                .title(ticket.getTitle())
                .category(ticket.getCategory())
                .status(ticket.getStatus())
                .priority(ticket.getPriority())
                .dueDate(ticket.getDueDate())
                .reporterId(ticket.getReporter() == null ? null : ticket.getReporter().getId())
                .reporterName(ticket.getReporter() == null ? null : ticket.getReporter().getFullName())
                .assignees(toAssigneeDtos(ticket))
                .createdAt(ticket.getAuditMetaData() == null ? null : ticket.getAuditMetaData().getCreatedAt())
                .build();
    }

    default List<TicketSummaryResponseDto> toSummaryList(List<Ticket> tickets) {
        if (tickets == null) {
            return List.of();
        }
        return tickets.stream().map(this::toSummary).collect(Collectors.toList());
    }

    default TicketDetailResponseDto toDetail(Ticket ticket,
                                             List<TicketAttachmentResponseDto> attachments,
                                             List<TicketCommentResponseDto> comments) {
        if (ticket == null) {
            return null;
        }
        return TicketDetailResponseDto.builder()
                .id(ticket.getId())
                .code(ticket.getCode())
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .category(ticket.getCategory())
                .status(ticket.getStatus())
                .priority(ticket.getPriority())
                .dueDate(ticket.getDueDate())
                .reporterId(ticket.getReporter() == null ? null : ticket.getReporter().getId())
                .reporterName(ticket.getReporter() == null ? null : ticket.getReporter().getFullName())
                .buildingNodeId(ticket.getBuildingNode() == null ? null : ticket.getBuildingNode().getId())
                .buildingNodeName(ticket.getBuildingNode() == null ? null : ticket.getBuildingNode().getName())
                .assignees(toAssigneeDtos(ticket))
                .resolutionNote(ticket.getResolutionNote())
                .resolvedAt(ticket.getResolvedAt())
                .closedAt(ticket.getClosedAt())
                .attachments(attachments == null ? List.of() : attachments)
                .comments(comments == null ? List.of() : comments)
                .createdAt(ticket.getAuditMetaData() == null ? null : ticket.getAuditMetaData().getCreatedAt())
                .build();
    }
}
