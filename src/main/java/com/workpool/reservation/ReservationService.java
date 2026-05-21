package com.workpool.reservation;

import com.workpool.common.exception.AppException;
import com.workpool.office.*;
import com.workpool.payment.Payment;
import com.workpool.payment.PaymentService;
import com.workpool.reservation.dto.*;
import com.workpool.user.User;
import com.workpool.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationStatusRepository statusRepository;
    private final GuestRepository guestRepository;
    private final VehicleRepository vehicleRepository;
    private final OfficeRepository officeRepository;
    private final OfficePlanRepository officePlanRepository;
    private final OfficeBlockRepository officeBlockRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final ReservationEmailService reservationEmailService;

    @Value("${app.exchange-rate:3.75}")
    private BigDecimal exchangeRate;

    private static final int PRE_RESERVATION_MINUTES = 30;

    // ===== CREAR PRE-RESERVA (CLIENTE) =====

    @Transactional
    public ReservationResponse createPreReservation(CreateReservationRequest request, String userEmail) {
        User user = findUserByEmail(userEmail);
        Office office = findOfficeById(request.getOfficeId());
        OfficePlan plan = findPlanById(request.getOfficePlanId());

        // validar que el plan corresponda al tipo de oficina
        if (!plan.getOfficeKind().getId().equals(office.getOfficeKind().getId())) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "El plan seleccionado no corresponde al tipo de espacio");
        }

        validateDates(request.getBeginDate(), request.getEndDate());
        validateCapacity(office, request.getPersonAmount());
        validateNoOverlap(request.getOfficeId(), request.getBeginDate(), request.getEndDate());
        validateNoBlock(request.getOfficeId(), request.getBeginDate(), request.getEndDate());
        validateVehicles(request.getVehiclePlates(), request.isUsesParking());

        // RN-003: calcular precio
        long totalHours = calculateHours(request.getBeginDate(), request.getEndDate());
        BigDecimal totalUsd = plan.getPricePerHour().multiply(BigDecimal.valueOf(totalHours));
        BigDecimal totalPen = totalUsd.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);

        ReservationStatus pendingStatus = findStatus("PENDIENTE");

        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setOffice(office);
        reservation.setReservationStatus(pendingStatus);
        reservation.setBeginDate(request.getBeginDate());
        reservation.setEndDate(request.getEndDate());
        reservation.setPersonAmount(request.getPersonAmount());
        reservation.setUsesParking(request.isUsesParking());
        reservation.setPricePerHour(plan.getPricePerHour());
        reservation.setTotalPriceUsd(totalUsd);
        reservation.setTotalPricePen(totalPen);
        reservation.setExchangeRate(exchangeRate);
        reservation.setRepresentativeName(request.getRepresentativeName());
        reservation.setRepresentativeLastName(request.getRepresentativeLastName());
        reservation.setRepresentativeDni(request.getRepresentativeDni());
        reservation.setCreatedByAdmin(false);
        reservation.setExpiresAt(OffsetDateTime.now().plusMinutes(PRE_RESERVATION_MINUTES));

        try {
            reservationRepository.save(reservation);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new AppException(HttpStatus.CONFLICT, "El horario seleccionado no está disponible");
        }

        // invitados
        if (request.getGuests() != null && !request.getGuests().isEmpty()) {
            addGuests(reservation, request.getGuests());
        }

        // vehículos
        if (request.getVehiclePlates() != null && !request.getVehiclePlates().isEmpty()) {
            addVehicles(reservation, request.getVehiclePlates());
        }

        return ReservationResponse.from(reservation);
    }

    // ===== CONFIRMAR PAGO =====

    @Transactional
    public ReservationResponse confirmPayment(UUID reservationId, String userEmail) {
        // lock pesimista sobre la reserva específica
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Reserva no encontrada"));

        if (!reservation.getUser().getEmail().equals(userEmail)) {
            throw new AppException(HttpStatus.FORBIDDEN, "No tienes acceso a esta reserva");
        }

        if (!reservation.isPending()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "La reserva no está en estado pendiente");
        }

        if (reservation.isExpired()) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "La pre-reserva ha expirado. Debes iniciar un nuevo proceso de reserva.");
        }

        String description = String.format("Reserva %s - %s",
                reservation.getOffice().getName(), reservation.getId().toString().substring(0, 8));

        Payment payment = paymentService.processPayment(
                reservation.getTotalPriceUsd(),
                reservation.getTotalPricePen(),
                reservation.getExchangeRate(),
                description
        );

        if (!"SUCCESS".equals(payment.getStatus())) {
            throw new AppException(HttpStatus.BAD_GATEWAY,
                    "El pago no pudo procesarse. Puedes intentarlo nuevamente mientras la pre-reserva esté vigente.");
        }

        ReservationStatus confirmedStatus = findStatus("CONFIRMADA");
        reservation.setPayment(payment);
        reservation.setReservationStatus(confirmedStatus);
        reservation.setExpiresAt(null);
        reservationRepository.save(reservation);

        reservationEmailService.sendStatusChangeEmail(reservation);

        return ReservationResponse.from(reservation);
    }

    // ===== RESERVA MANUAL (ADMIN) =====

    @Transactional
    public ReservationResponse createAdminReservation(AdminCreateReservationRequest request, String adminEmail) {
        Office office = findOfficeById(request.getOfficeId());

        validateDates(request.getBeginDate(), request.getEndDate());
        validateCapacity(office, request.getPersonAmount());
        validateNoOverlap(request.getOfficeId(), request.getBeginDate(), request.getEndDate());
        validateNoBlock(request.getOfficeId(), request.getBeginDate(), request.getEndDate());

        // RN-010: admin crea directamente como CONFIRMADA sin pago
        ReservationStatus confirmedStatus = findStatus("CONFIRMADA");

        // si viene userId, asignar al usuario; si no, asignar al admin
        User assignedUser;
        if (request.getUserId() != null) {
            assignedUser = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        } else {
            assignedUser = findUserByEmail(adminEmail);
        }

        Reservation reservation = new Reservation();
        reservation.setUser(assignedUser);
        reservation.setOffice(office);
        reservation.setReservationStatus(confirmedStatus);
        reservation.setBeginDate(request.getBeginDate());
        reservation.setEndDate(request.getEndDate());
        reservation.setPersonAmount(request.getPersonAmount());
        reservation.setUsesParking(false);
        reservation.setPricePerHour(BigDecimal.ZERO);
        reservation.setTotalPriceUsd(BigDecimal.ZERO);
        reservation.setTotalPricePen(BigDecimal.ZERO);
        reservation.setExchangeRate(exchangeRate);
        reservation.setRepresentativeName(request.getRepresentativeName());
        reservation.setRepresentativeLastName(request.getRepresentativeLastName());
        reservation.setRepresentativeDni(request.getRepresentativeDni());
        reservation.setCreatedByAdmin(true);
        reservation.setExpiresAt(null);

        reservationRepository.save(reservation);
        return ReservationResponse.from(reservation);
    }

    // ===== CANCELAR RESERVA =====

    @Transactional
    public ReservationResponse cancelReservation(UUID reservationId, String userEmail, boolean isAdmin) {
        Reservation reservation = findReservationById(reservationId);

        // si no es admin, verificar que sea del usuario
        if (!isAdmin && !reservation.getUser().getEmail().equals(userEmail)) {
            throw new AppException(HttpStatus.FORBIDDEN, "No tienes acceso a esta reserva");
        }

        if (!reservation.isPending() && !reservation.isConfirmed()) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Solo se pueden cancelar reservas pendientes o confirmadas");
        }

        // RN-006: cancelar y liberar horario
        ReservationStatus cancelledStatus = findStatus("CANCELADA");
        reservation.setReservationStatus(cancelledStatus);
        reservation.setExpiresAt(null);
        reservation.setActiveSlot(false);
        reservationRepository.save(reservation);

        // RN-087: notificar cancelación
        reservationEmailService.sendStatusChangeEmail(reservation);

        return ReservationResponse.from(reservation);
    }

    // ===== COTIZACIÓN =====

    public PriceQuoteResponse calculateQuote(UUID officePlanId, OffsetDateTime beginDate, OffsetDateTime endDate) {
        OfficePlan plan = findPlanById(officePlanId);
        validateDates(beginDate, endDate);

        long totalHours = calculateHours(beginDate, endDate);
        long extraHours = Math.max(0, totalHours - plan.getPlanDurationHours());

        BigDecimal totalUsd = plan.getPricePerHour().multiply(BigDecimal.valueOf(totalHours));
        BigDecimal totalPen = totalUsd.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);

        return new PriceQuoteResponse(
                plan.getPricePerHour(),
                plan.getPlanDurationHours(),
                totalHours,
                extraHours,
                totalUsd,
                exchangeRate,
                totalPen
        );
    }

    // ===== PANEL DEL USUARIO =====

    @Transactional(readOnly = true)
    public List<ReservationResponse> getMyReservations(String userEmail) {
        User user = findUserByEmail(userEmail);
        return reservationRepository.findAllByUserId(user.getId()).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReservationResponse getMyPendingDraft(String userEmail) {
        User user = findUserByEmail(userEmail);
        Reservation draft = reservationRepository.findPendingDraft(user.getId(), OffsetDateTime.now())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "No tienes reservas pendientes"));
        return ReservationResponse.from(draft);
    }

    // ===== ADMIN: HISTORIAL Y CONSULTA =====

    @Transactional(readOnly = true)
    public List<ReservationResponse> getAllReservations() {
        return reservationRepository.findAll().stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsByUser(UUID userId) {
        return reservationRepository.findAllByUserId(userId).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    // ===== SCHEDULER: EXPIRAR PRE-RESERVAS =====

    @Transactional
    public int expireOldPreReservations() {
        List<Reservation> expired = reservationRepository.findExpiredPreReservations(OffsetDateTime.now());
        ReservationStatus expiredStatus = findStatus("EXPIRADA");

        for (Reservation r : expired) {
            r.setReservationStatus(expiredStatus);
            r.setActiveSlot(false);
            reservationRepository.save(r);
            reservationEmailService.sendStatusChangeEmail(r);
        }

        return expired.size();
    }

    // ===== VALIDACIONES PRIVADAS =====

    private void validateDates(OffsetDateTime begin, OffsetDateTime end) {
        if (begin.isAfter(end) || begin.isEqual(end)) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "La fecha de inicio debe ser anterior a la fecha de fin");
        }
        if (begin.isBefore(OffsetDateTime.now())) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "No se pueden crear reservas en fechas pasadas");
        }
    }

    private void validateCapacity(Office office, int personAmount) {
        if (personAmount > office.getCapacity()) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    String.format("La cantidad de asistentes (%d) supera la capacidad del espacio (%d)",
                            personAmount, office.getCapacity()));
        }
    }

    private void validateNoOverlap(UUID officeId, OffsetDateTime begin, OffsetDateTime end) {
        int overlapping = reservationRepository.countOverlapping(officeId, begin, end);
        if (overlapping > 0) {
            throw new AppException(HttpStatus.CONFLICT,
                    "El horario seleccionado no está disponible");
        }
    }

    private void validateNoBlock(UUID officeId, OffsetDateTime begin, OffsetDateTime end) {
        int blocks = officeBlockRepository.countOverlapping(officeId, begin, end);
        if (blocks > 0) {
            throw new AppException(HttpStatus.CONFLICT,
                    "El horario seleccionado está bloqueado por mantenimiento");
        }
    }

    private void validateVehicles(List<String> plates, boolean usesParking) {
        if (plates != null && plates.size() > 2) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Máximo 2 vehículos por reserva");
        }
        if (plates != null && !plates.isEmpty() && !usesParking) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Debe indicar que usa estacionamiento para registrar vehículos");
        }
    }

    private long calculateHours(OffsetDateTime begin, OffsetDateTime end) {
        long minutes = Duration.between(begin, end).toMinutes();
        return (long) Math.ceil(minutes / 60.0);
    }

    private void addGuests(Reservation reservation, List<CreateReservationRequest.GuestItem> guestItems) {
        Set<Guest> guests = new HashSet<>();
        for (CreateReservationRequest.GuestItem item : guestItems) {
            Guest guest = new Guest();
            guest.setName(item.getName());
            guest.setLastName(item.getLastName());
            guest.setDni(item.getDni());
            guestRepository.save(guest);
            guests.add(guest);
        }
        reservation.setGuests(guests);
        reservationRepository.save(reservation);
    }

    private void addVehicles(Reservation reservation, List<String> plates) {
        Set<Vehicle> vehicles = new HashSet<>();
        for (String plate : plates) {
            Vehicle vehicle = new Vehicle();
            vehicle.setPlate(plate.toUpperCase());
            vehicleRepository.save(vehicle);
            vehicles.add(vehicle);
        }
        reservation.setVehicles(vehicles);
        reservationRepository.save(reservation);
    }

    private void expireReservation(Reservation reservation) {
        ReservationStatus expiredStatus = findStatus("EXPIRADA");
        reservation.setReservationStatus(expiredStatus);
        reservation.setActiveSlot(false);
        reservationRepository.save(reservation);
        reservationEmailService.sendStatusChangeEmail(reservation);
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    private Office findOfficeById(UUID id) {
        Office office = officeRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Espacio no encontrado"));
        if (!office.isEnabled()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "El espacio no está disponible");
        }
        return office;
    }

    private OfficePlan findPlanById(UUID id) {
        OfficePlan plan = officePlanRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Plan no encontrado"));
        if (!plan.isEnabled()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "El plan no está activo");
        }
        return plan;
    }

    private Reservation findReservationById(UUID id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Reserva no encontrada"));
    }

    private ReservationStatus findStatus(String name) {
        return statusRepository.findByStatusName(name)
                .orElseThrow(() -> new AppException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Estado de reserva no encontrado: " + name));
    }
}