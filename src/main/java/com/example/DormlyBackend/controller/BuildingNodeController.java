package com.example.DormlyBackend.controller;

import com.example.DormlyBackend.dto.request.BuildingNodeRequest;
import com.example.DormlyBackend.dto.response.ApiResponse;
import com.example.DormlyBackend.dto.response.BuildingNodeResponseDto;
import com.example.DormlyBackend.service.BuildingNodeService;
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
@RequestMapping("/api/building-nodes")
@RequiredArgsConstructor
public class BuildingNodeController {

    private final BuildingNodeService buildingNodeService;

    @PostMapping
    public ResponseEntity<ApiResponse<BuildingNodeResponseDto>> create(
            @RequestBody @Valid BuildingNodeRequest request) {
        var result = buildingNodeService.create(request);
        return ResponseEntity.ok(ApiResponse.<BuildingNodeResponseDto>builder().result(result).build());
    }

    // node only (without children)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BuildingNodeResponseDto>> getById(@PathVariable UUID id) {
        var result = buildingNodeService.getById(id);
        return ResponseEntity.ok(ApiResponse.<BuildingNodeResponseDto>builder().result(result).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BuildingNodeResponseDto>> update(@PathVariable UUID id,
            @RequestBody @Valid BuildingNodeRequest request) {
        var result = buildingNodeService.update(id, request);
        return ResponseEntity.ok(ApiResponse.<BuildingNodeResponseDto>builder().result(result).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        buildingNodeService.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().result(null).build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BuildingNodeResponseDto>>> list() {
        var result = buildingNodeService.list();
        return ResponseEntity.ok(ApiResponse.<List<BuildingNodeResponseDto>>builder().result(result).build());
    }

    // recursive tree from node-level
    @GetMapping("/tree/{node-level}")
    public ResponseEntity<ApiResponse<List<BuildingNodeResponseDto>>> treeByNodeLevel(
            @PathVariable("node-level") int nodeLevel) {
        var result = buildingNodeService.getTreeByNodeLevel(nodeLevel);
        return ResponseEntity.ok(ApiResponse.<List<BuildingNodeResponseDto>>builder().result(result).build());
    }
}
