package com.example.DormlyBackend.service;

import com.example.DormlyBackend.dto.request.InvoiceRequestDto;
import com.example.DormlyBackend.dto.response.InvoiceResponseDto;
import com.example.DormlyBackend.entity.building.Invoice;
import com.example.DormlyBackend.entity.building.RoomAssignment;
import com.example.DormlyBackend.enums.InvoiceStatus;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.repository.InvoiceRepository;
import com.example.DormlyBackend.repository.RoomAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final RoomAssignmentRepository roomAssignmentRepository;

    public InvoiceResponseDto createInvoice(InvoiceRequestDto request) {
        RoomAssignment assignment = roomAssignmentRepository.findById(request.getRoomAssignmentId())
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "RoomAssignment", request.getRoomAssignmentId()));

        Invoice invoice = Invoice.builder()
                .roomAssignment(assignment)
                .feeCategory(request.getFeeCategory())
                .amount(request.getAmount())
                .status(InvoiceStatus.UNPAID)
                .month(request.getMonth())
                .dueDate(LocalDateTime.now().plusDays(7))
                .notes(request.getNotes())
                .paymentQrCodeUrl("https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=pay-dorm-rent")
                .build();

        Invoice saved = invoiceRepository.save(invoice);
        return mapToDto(saved);
    }

    public InvoiceResponseDto payInvoice(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Invoice", invoiceId));

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now());
        Invoice saved = invoiceRepository.save(invoice);
        return mapToDto(saved);
    }

    @Transactional(readOnly = true)
    public InvoiceResponseDto getInvoiceById(UUID id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Invoice", id));
        return mapToDto(invoice);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponseDto> getMyInvoices(UUID userId) {
        return invoiceRepository.findByUserId(userId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponseDto> getAllInvoices() {
        return invoiceRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private InvoiceResponseDto mapToDto(Invoice invoice) {
        RoomAssignment ra = invoice.getRoomAssignment();
        return InvoiceResponseDto.builder()
                .id(invoice.getId())
                .roomAssignmentId(ra.getId())
                .roomId(ra.getRoomNode().getId())
                .roomName(ra.getRoomNode().getName())
                .blockName(ra.getRoomNode().getParent() != null ? ra.getRoomNode().getParent().getName() : "Khu A")
                .studentId(ra.getUser().getId())
                .studentName(ra.getUser().getFullName())
                .feeCategory(invoice.getFeeCategory())
                .amount(invoice.getAmount())
                .status(invoice.getStatus())
                .month(invoice.getMonth())
                .dueDate(invoice.getDueDate())
                .paidAt(invoice.getPaidAt())
                .paymentQrCodeUrl(invoice.getPaymentQrCodeUrl())
                .notes(invoice.getNotes())
                .build();
    }
}
