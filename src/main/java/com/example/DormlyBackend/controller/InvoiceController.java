package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.dto.request.InvoiceRequestDto;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.InvoiceResponseDto;
import com.example.DormlyBackend.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    // Public / Shared endpoints for authenticated users
    @GetMapping("/api/invoices/{id}")
    public ApiResponse<InvoiceResponseDto> getById(@PathVariable UUID id) {
        var result = invoiceService.getInvoiceById(id);
        return ApiResponse.<InvoiceResponseDto>builder()
                .result(result)
                .message("Get invoice by id successfully")
                .build();
    }

    @PostMapping("/api/invoices/{id}/pay")
    public ApiResponse<InvoiceResponseDto> pay(@PathVariable UUID id) {
        var result = invoiceService.payInvoice(id);
        return ApiResponse.<InvoiceResponseDto>builder()
                .result(result)
                .message("Invoice paid successfully")
                .build();
    }

    // Student self invoice query
    @GetMapping("/api/users/me/invoices")
    public ApiResponse<List<InvoiceResponseDto>> getMyInvoices() {
        UUID userId = currentUserId();
        var result = invoiceService.getMyInvoices(userId);
        return ApiResponse.<List<InvoiceResponseDto>>builder()
                .result(result)
                .message("Get my invoices successfully")
                .build();
    }

    // Admin endpoint to create and list all invoices
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/invoices")
    public ApiResponse<InvoiceResponseDto> create(@RequestBody @Valid InvoiceRequestDto request) {
        var result = invoiceService.createInvoice(request);
        return ApiResponse.<InvoiceResponseDto>builder()
                .result(result)
                .message("Invoice generated successfully")
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/invoices")
    public ApiResponse<List<InvoiceResponseDto>> listAll() {
        var result = invoiceService.getAllInvoices();
        return ApiResponse.<List<InvoiceResponseDto>>builder()
                .result(result)
                .message("Get all invoices successfully")
                .build();
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth.getPrincipal();
        if (principal instanceof com.example.DormlyBackend.configuration.security.UserPrincipal up) {
            return up.getId();
        }
        throw new IllegalStateException("Unsupported principal type: " + principal);
    }
}
