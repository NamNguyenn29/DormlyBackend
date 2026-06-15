package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.dto.request.NodeTypeRequest;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.NodeTypeResponseDto;
import com.example.DormlyBackend.service.NodeTypeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/node-types")
@RequiredArgsConstructor
public class NodeTypeController {

    private final NodeTypeService nodeTypeService;

    @PostMapping
    public ResponseEntity<ApiResponse<NodeTypeResponseDto>> create(@RequestBody @Valid NodeTypeRequest request) {
        var result = nodeTypeService.create(request);
        return ResponseEntity.ok(ApiResponse.<NodeTypeResponseDto>builder().result(result).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NodeTypeResponseDto>> getById(@PathVariable UUID id) {
        var result = nodeTypeService.getById(id);
        return ResponseEntity.ok(ApiResponse.<NodeTypeResponseDto>builder().result(result).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NodeTypeResponseDto>> update(@PathVariable UUID id,
            @RequestBody @Valid NodeTypeRequest request) {
        var result = nodeTypeService.update(id, request);
        return ResponseEntity.ok(ApiResponse.<NodeTypeResponseDto>builder().result(result).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        nodeTypeService.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().result(null).build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NodeTypeResponseDto>>> list() {
        var result = nodeTypeService.list();
        return ResponseEntity.ok(ApiResponse.<List<NodeTypeResponseDto>>builder().result(result).build());
    }
}
