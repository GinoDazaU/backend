package com.workpool.reservation;

import com.workpool.common.response.ApiResponse;
import com.workpool.reservation.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    // ===== CLIENTE =====

    @PostMapping
    @PreAuthorize("hasAuthority('REALIZAR_RESERVAS')")
    public ResponseEntity<ApiResponse<ReservationResponse>> createPreReservation(
            @Valid @RequestBody CreateReservationRequest request,
            @AuthenticationPrincipal String userEmail) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Pre-reserva creada. Tienes 30 minutos para completar el pago.",
                        reservationService.createPreReservation(request, userEmail)));
    }

    @PostMapping("/confirm-payment")
    @PreAuthorize("hasAuthority('REALIZAR_RESERVAS')")
    public ResponseEntity<ApiResponse<ReservationResponse>> confirmPayment(
            @Valid @RequestBody ConfirmPaymentRequest request,
            @AuthenticationPrincipal String userEmail) {
        return ResponseEntity.ok(ApiResponse.ok("Reserva confirmada",
                reservationService.confirmPayment(request.getReservationId(), userEmail)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('CANCELAR_RESERVAS_PROPIAS')")
    public ResponseEntity<ApiResponse<ReservationResponse>> cancelOwn(
            @PathVariable UUID id,
            @AuthenticationPrincipal String userEmail) {
        return ResponseEntity.ok(ApiResponse.ok("Reserva cancelada",
                reservationService.cancelReservation(id, userEmail, false)));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAuthority('REALIZAR_RESERVAS')")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> myReservations(
            @AuthenticationPrincipal String userEmail) {
        return ResponseEntity.ok(ApiResponse.ok("Mis reservas",
                reservationService.getMyReservations(userEmail)));
    }

    @GetMapping("/my/pending")
    @PreAuthorize("hasAuthority('REALIZAR_RESERVAS')")
    public ResponseEntity<ApiResponse<ReservationResponse>> myPendingDraft(
            @AuthenticationPrincipal String userEmail) {
        return ResponseEntity.ok(ApiResponse.ok("Borrador pendiente",
                reservationService.getMyPendingDraft(userEmail)));
    }

    @GetMapping("/quote")
    @PreAuthorize("hasAuthority('VER_ESPACIOS')")
    public ResponseEntity<ApiResponse<PriceQuoteResponse>> quote(
            @RequestParam UUID planId,
            @RequestParam OffsetDateTime beginDate,
            @RequestParam OffsetDateTime endDate) {
        return ResponseEntity.ok(ApiResponse.ok("Cotización",
                reservationService.calculateQuote(planId, beginDate, endDate)));
    }

    // ===== ADMIN =====

    @PostMapping("/admin")
    @PreAuthorize("hasAuthority('GESTIONAR_RESERVAS')")
    public ResponseEntity<ApiResponse<ReservationResponse>> adminCreate(
            @Valid @RequestBody AdminCreateReservationRequest request,
            @AuthenticationPrincipal String adminEmail) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Reserva creada por administrador",
                        reservationService.createAdminReservation(request, adminEmail)));
    }

    @PostMapping("/admin/{id}/cancel")
    @PreAuthorize("hasAuthority('GESTIONAR_RESERVAS')")
    public ResponseEntity<ApiResponse<ReservationResponse>> adminCancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal String adminEmail) {
        return ResponseEntity.ok(ApiResponse.ok("Reserva cancelada por administrador",
                reservationService.cancelReservation(id, adminEmail, true)));
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasAuthority('VER_TODAS_RESERVAS')")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("Todas las reservas",
                reservationService.getAllReservations()));
    }

    @GetMapping("/admin/user/{userId}")
    @PreAuthorize("hasAuthority('VER_TODAS_RESERVAS')")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok("Reservas del usuario",
                reservationService.getReservationsByUser(userId)));
    }
}