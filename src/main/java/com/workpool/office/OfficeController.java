package com.workpool.office;

import com.workpool.common.response.ApiResponse;
import com.workpool.office.dto.*;
import com.workpool.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/offices")
@RequiredArgsConstructor
public class OfficeController {

    private final OfficeService officeService;

    // ===== PÚBLICO (clientes) =====

    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<OfficeResponse>>> getAvailable(
            @RequestParam UUID kindId,
            @RequestParam(defaultValue = "1") int minCapacity) {
        return ResponseEntity.ok(ApiResponse.ok("Espacios disponibles",
                officeService.getAvailableOffices(kindId, minCapacity)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OfficeResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Espacio encontrado",
                officeService.getOfficeById(id)));
    }

    @GetMapping("/kinds")
    public ResponseEntity<ApiResponse<List<OfficeKindResponse>>> getKinds() {
        return ResponseEntity.ok(ApiResponse.ok("Tipos de espacio",
                officeService.getAllKinds()));
    }

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<OfficePlanResponse>>> getPlansByKind(
            @RequestParam UUID kindId) {
        return ResponseEntity.ok(ApiResponse.ok("Planes de precios",
                officeService.getPlansByKind(kindId)));
    }

    // ===== ADMIN =====

    @GetMapping("/admin/all")
    @PreAuthorize("hasAuthority('GESTIONAR_ESPACIOS')")
    public ResponseEntity<ApiResponse<List<OfficeResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("Todos los espacios",
                officeService.getAllOffices()));
    }

    @PostMapping("/admin")
    @PreAuthorize("hasAuthority('GESTIONAR_ESPACIOS')")
    public ResponseEntity<ApiResponse<OfficeResponse>> create(
            @Valid @RequestBody OfficeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Espacio creado", officeService.createOffice(request)));
    }

    @PutMapping("/admin/{id}")
    @PreAuthorize("hasAuthority('GESTIONAR_ESPACIOS')")
    public ResponseEntity<ApiResponse<OfficeResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody OfficeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Espacio actualizado",
                officeService.updateOffice(id, request)));
    }

    @PatchMapping("/admin/{id}/disable")
    @PreAuthorize("hasAuthority('GESTIONAR_ESPACIOS')")
    public ResponseEntity<ApiResponse<Void>> disable(@PathVariable UUID id) {
        officeService.disableOffice(id);
        return ResponseEntity.ok(ApiResponse.ok("Espacio desactivado"));
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasAuthority('GESTIONAR_ESPACIOS')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        officeService.deleteOffice(id);
        return ResponseEntity.ok(ApiResponse.ok("Espacio eliminado"));
    }

    // ===== ADMIN - PLANES =====

    @GetMapping("/admin/plans")
    @PreAuthorize("hasAuthority('GESTIONAR_ESPACIOS')")
    public ResponseEntity<ApiResponse<List<OfficePlanResponse>>> getAllPlans() {
        return ResponseEntity.ok(ApiResponse.ok("Todos los planes",
                officeService.getAllPlans()));
    }

    @PostMapping("/admin/plans")
    @PreAuthorize("hasAuthority('GESTIONAR_ESPACIOS')")
    public ResponseEntity<ApiResponse<OfficePlanResponse>> createPlan(
            @Valid @RequestBody OfficePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Plan creado", officeService.createPlan(request)));
    }

    @PutMapping("/admin/plans/{id}")
    @PreAuthorize("hasAuthority('GESTIONAR_ESPACIOS')")
    public ResponseEntity<ApiResponse<OfficePlanResponse>> updatePlan(
            @PathVariable UUID id,
            @Valid @RequestBody OfficePlanRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Plan actualizado",
                officeService.updatePlan(id, request)));
    }

    @PatchMapping("/admin/plans/{id}/disable")
    @PreAuthorize("hasAuthority('GESTIONAR_ESPACIOS')")
    public ResponseEntity<ApiResponse<Void>> disablePlan(@PathVariable UUID id) {
        officeService.disablePlan(id);
        return ResponseEntity.ok(ApiResponse.ok("Plan desactivado"));
    }

    // ===== ADMIN - BLOQUEOS =====

    @GetMapping("/admin/blocks/{officeId}")
    @PreAuthorize("hasAuthority('GESTIONAR_ESPACIOS')")
    public ResponseEntity<ApiResponse<List<OfficeBlockResponse>>> getBlocks(
            @PathVariable UUID officeId) {
        return ResponseEntity.ok(ApiResponse.ok("Bloqueos del espacio",
                officeService.getBlocksByOffice(officeId)));
    }

    @PostMapping("/admin/blocks")
    @PreAuthorize("hasAuthority('GESTIONAR_ESPACIOS')")
    public ResponseEntity<ApiResponse<OfficeBlockResponse>> createBlock(
            @Valid @RequestBody OfficeBlockRequest request,
            @AuthenticationPrincipal String adminEmail) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Bloqueo creado",
                        officeService.createBlock(request, adminEmail)));
    }

    @PatchMapping("/admin/blocks/{id}/cancel")
    @PreAuthorize("hasAuthority('GESTIONAR_ESPACIOS')")
    public ResponseEntity<ApiResponse<Void>> cancelBlock(@PathVariable UUID id) {
        officeService.cancelBlock(id);
        return ResponseEntity.ok(ApiResponse.ok("Bloqueo cancelado"));
    }
}