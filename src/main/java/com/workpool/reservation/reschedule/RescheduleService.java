package com.workpool.reservation.reschedule;

import com.workpool.common.exception.AppException;
import com.workpool.office.Office;
import com.workpool.office.OfficeBlockRepository;
import com.workpool.office.OfficeRepository;
import com.workpool.reservation.*;
import com.workpool.reservation.reschedule.dto.AdminRescheduleRequest;
import com.workpool.reservation.reschedule.dto.RescheduleResponse;
import com.workpool.user.User;
import com.workpool.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RescheduleService {

    private final RescheduleRequestRepository rescheduleRequestRepository;
    private final RescheduleStatusRepository rescheduleStatusRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationStatusRepository reservationStatusRepository;
    private final OfficeRepository officeRepository;
    private final OfficeBlockRepository officeBlockRepository;
    private final UserRepository userRepository;
    private final ReservationEmailService reservationEmailService;

    // ===== CLIENTE: SOLICITAR REPROGRAMACIÓN =====

    @Transactional
    public RescheduleResponse requestReschedule(UUID reservationId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Reserva no encontrada"));

        // verificar que pertenece al usuario
        if (!reservation.getUser().getId().equals(user.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "No tienes acceso a esta reserva");
        }

        // RN-093: solo reservas confirmadas
        if (!reservation.isConfirmed()) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Solo se puede solicitar reprogramación de reservas confirmadas");
        }

        // RN-092: máximo 1 solicitud por reserva
        if (rescheduleRequestRepository.existsByReservationId(reservationId)) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Ya existe una solicitud de reprogramación para esta reserva");
        }

        RescheduleStatus pendingStatus = rescheduleStatusRepository
                .findByRescheduleStatusName("PENDIENTE")
                .orElseThrow(() -> new AppException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Estado de reprogramación no encontrado"));

        RescheduleRequest request = new RescheduleRequest();
        request.setUser(user);
        request.setReservation(reservation);
        request.setRescheduleStatus(pendingStatus);
        rescheduleRequestRepository.save(request);

        return RescheduleResponse.from(request);
    }

    // ===== ADMIN: LISTAR SOLICITUDES =====

    @Transactional(readOnly = true)
    public List<RescheduleResponse> getAllRequests() {
        return rescheduleRequestRepository.findAll().stream()
                .map(RescheduleResponse::from)
                .toList();
    }

    // ===== ADMIN: APROBAR O RECHAZAR =====

    @Transactional
    public RescheduleResponse processReschedule(AdminRescheduleRequest request) {
        RescheduleRequest reschedule = rescheduleRequestRepository
                .findById(request.getRescheduleRequestId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Solicitud de reprogramación no encontrada"));

        if (!reschedule.getRescheduleStatus().getRescheduleStatusName().equals("PENDIENTE")) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Esta solicitud ya fue procesada");
        }

        Reservation reservation = reschedule.getReservation();

        if (request.isApproved()) {
            // validar nuevos datos
            if (request.getNewBeginDate() == null || request.getNewEndDate() == null) {
                throw new AppException(HttpStatus.BAD_REQUEST,
                        "Debe indicar la nueva fecha de inicio y fin para aprobar");
            }

            validateDates(request.getNewBeginDate(), request.getNewEndDate());

            UUID officeId = request.getNewOfficeId() != null
                    ? request.getNewOfficeId()
                    : reservation.getOffice().getId();

            // validar oficina si cambió
            if (request.getNewOfficeId() != null) {
                Office newOffice = officeRepository.findById(request.getNewOfficeId())
                        .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Espacio no encontrado"));
                if (!newOffice.isEnabled()) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "El espacio no está disponible");
                }
                reservation.setOffice(newOffice);
            }

            // validar solapamiento en nuevo horario
            int overlapping = reservationRepository.countOverlapping(
                    officeId, request.getNewBeginDate(), request.getNewEndDate());
            if (overlapping > 0) {
                throw new AppException(HttpStatus.CONFLICT,
                        "El nuevo horario no está disponible");
            }

            int blocks = officeBlockRepository.countOverlapping(
                    officeId, request.getNewBeginDate(), request.getNewEndDate());
            if (blocks > 0) {
                throw new AppException(HttpStatus.CONFLICT,
                        "El nuevo horario está bloqueado por mantenimiento");
            }

            // aplicar reprogramación
            reservation.setBeginDate(request.getNewBeginDate());
            reservation.setEndDate(request.getNewEndDate());

            ReservationStatus rescheduledStatus = reservationStatusRepository
                    .findByStatusName("REPROGRAMADA")
                    .orElseThrow();
            reservation.setReservationStatus(rescheduledStatus);
            reservationRepository.save(reservation);

            RescheduleStatus approvedStatus = rescheduleStatusRepository
                    .findByRescheduleStatusName("APROBADA")
                    .orElseThrow();
            reschedule.setRescheduleStatus(approvedStatus);

            // RN-087: notificar
            reservationEmailService.sendStatusChangeEmail(reservation);

        } else {
            // rechazar: reserva mantiene su estado original
            RescheduleStatus rejectedStatus = rescheduleStatusRepository
                    .findByRescheduleStatusName("RECHAZADA")
                    .orElseThrow();
            reschedule.setRescheduleStatus(rejectedStatus);
        }

        rescheduleRequestRepository.save(reschedule);
        return RescheduleResponse.from(reschedule);
    }

    // ===== VALIDACIONES =====

    private void validateDates(OffsetDateTime begin, OffsetDateTime end) {
        if (begin.isAfter(end) || begin.isEqual(end)) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "La fecha de inicio debe ser anterior a la fecha de fin");
        }
        if (begin.isBefore(OffsetDateTime.now())) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "No se puede reprogramar a fechas pasadas");
        }
    }
}