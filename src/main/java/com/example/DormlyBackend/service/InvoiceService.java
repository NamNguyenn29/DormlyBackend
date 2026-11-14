package com.example.DormlyBackend.service;

import com.example.DormlyBackend.dto.request.InvoiceRequestDto;
import com.example.DormlyBackend.dto.response.InvoiceResponseDto;
import com.example.DormlyBackend.entity.building.Invoice;
import com.example.DormlyBackend.entity.building.RoomAssignment;
import com.example.DormlyBackend.enums.InvoiceStatus;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.policy.InvoiceAccessPolicy;
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
    private final InvoiceAccessPolicy invoiceAccessPolicy;

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

    /**
     * Returns one invoice to a caller who is allowed to see it.
     *
     * <p>A missing invoice and an invoice the caller may not see are reported identically
     * ({@code RESOURCE_NOT_FOUND}) so invoice ids cannot be probed.
     *
     * @param id       invoice id
     * @param callerId id of the authenticated user
     * @param isStaff  true when the caller holds the ADMIN or STAFF role
     * @throws com.example.DormlyBackend.exception.model.ResourceNotFoundException when the invoice
     *         does not exist or {@link InvoiceAccessPolicy#canView} rejects the caller
     */
    @Transactional(readOnly = true)
    public InvoiceResponseDto getInvoiceById(UUID id, UUID callerId, boolean isStaff) {
        Invoice invoice = invoiceRepository.findById(id)
                .filter(found -> invoiceAccessPolicy.canView(found, callerId, isStaff))
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
        return invoiceRepository.findAllInvoices().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private InvoiceResponseDto mapToDto(Invoice invoice) {
        if (invoice == null) return null;
        RoomAssignment ra = invoice.getRoomAssignment();
        if (ra == null) return null;

        String blockName = "Khu A";
        if (ra.getRoomNode() != null && ra.getRoomNode().getParent() != null && ra.getRoomNode().getParent().getName() != null) {
            blockName = ra.getRoomNode().getParent().getName();
        }

        return InvoiceResponseDto.builder()
                .id(invoice.getId())
                .roomAssignmentId(ra.getId())
                .roomId(ra.getRoomNode() != null ? ra.getRoomNode().getId() : null)
                .roomName(ra.getRoomNode() != null ? ra.getRoomNode().getName() : "Phòng")
                .blockName(blockName)
                .studentId(ra.getUser() != null ? ra.getUser().getId() : null)
                .studentName(ra.getUser() != null ? ra.getUser().getFullName() : "Sinh viên")
                .feeCategory(invoice.getFeeCategory())
                .amount(invoice.getAmount())
                .status(invoice.getStatus())
                .month(invoice.getMonth())
                .dueDate(invoice.getDueDate())
                .paidAt(invoice.getPaidAt())
                .paymentQrCodeUrl(invoice.getPaymentQrCodeUrl())
                .notes(invoice.getNotes())
                .paymentMethod(invoice.getPaymentMethod())
                .paymentReference(invoice.getPaymentReference())
                .paymentNote(invoice.getPaymentNote())
                .confirmedByName(invoice.getConfirmedBy() != null ? invoice.getConfirmedBy().getFullName() : null)
                .build();
    }
}
