package com.workpool.reservation.reschedule;

import com.workpool.common.response.ApiResponse;
import com.workpool.reservation.reschedule.dto.AdminRescheduleRequest;
import com.workpool.reservation.reschedule.dto.CreateRescheduleRequest;
import com.workpool.reservation.reschedule.dto.RescheduleResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations/reschedule")
@RequiredArgsConstructor
public class RescheduleController {

    private final RescheduleService rescheduleService;

    @PostMapping
    @PreAuthorize("hasAuthority('REALIZAR_RESERVAS')")
    public ResponseEntity<ApiResponse<RescheduleResponse>> request(
            @Valid @RequestBody CreateRescheduleRequest request,
            @AuthenticationPrincipal String userEmail) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Solicitud de reprogramación creada. Contacta a Workpool por WhatsApp.",
                        rescheduleService.requestReschedule(request.getReservationId(), userEmail)));
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasAuthority('GESTIONAR_RESERVAS')")
    public ResponseEntity<ApiResponse<List<RescheduleResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("Solicitudes de reprogramación",
                rescheduleService.getAllRequests()));
    }

    @PostMapping("/admin/process")
    @PreAuthorize("hasAuthority('GESTIONAR_RESERVAS')")
    public ResponseEntity<ApiResponse<RescheduleResponse>> process(
            @Valid @RequestBody AdminRescheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Solicitud procesada",
                rescheduleService.processReschedule(request)));
    }
}