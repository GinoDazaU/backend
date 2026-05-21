package com.workpool.office;

import com.workpool.common.exception.AppException;
import com.workpool.office.dto.*;
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
public class OfficeService {

    private final OfficeRepository officeRepository;
    private final OfficeKindRepository officeKindRepository;
    private final OfficePlanRepository officePlanRepository;
    private final OfficeBlockRepository officeBlockRepository;
    private final UserRepository userRepository;

    // ===== OFFICE KINDS =====

    public List<OfficeKindResponse> getAllKinds() {
        return officeKindRepository.findAll().stream()
                .map(OfficeKindResponse::from)
                .toList();
    }

    @Transactional
    public OfficeKindResponse createKind(OfficeKindRequest request) {
        if (officeKindRepository.existsByKindName(request.getKindName())) {
            throw new AppException(HttpStatus.CONFLICT, "El tipo de espacio ya existe");
        }

        OfficeKind kind = new OfficeKind();
        kind.setKindName(request.getKindName());
        return OfficeKindResponse.from(officeKindRepository.save(kind));
    }

    @Transactional
    public OfficeKindResponse updateKind(UUID id, OfficeKindRequest request) {
        OfficeKind kind = officeKindRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Tipo de espacio no encontrado"));

        kind.setKindName(request.getKindName());
        return OfficeKindResponse.from(officeKindRepository.save(kind));
    }

    // ===== OFFICES =====

    public List<OfficeResponse> getAllOffices() {
        return officeRepository.findAll().stream()
                .map(OfficeResponse::from)
                .toList();
    }

    public List<OfficeResponse> getAvailableOffices(UUID kindId, int minCapacity) {
        return officeRepository.findAvailable(kindId, minCapacity).stream()
                .map(OfficeResponse::from)
                .toList();
    }

    public OfficeResponse getOfficeById(UUID id) {
        Office office = officeRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Espacio no encontrado"));
        return OfficeResponse.from(office);
    }

    @Transactional
    public OfficeResponse createOffice(OfficeRequest request) {
        OfficeKind kind = officeKindRepository.findById(request.getOfficeKindId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Tipo de espacio no encontrado"));

        Office office = new Office();
        office.setName(request.getName());
        office.setDescription(request.getDescription());
        office.setCapacity(request.getCapacity());
        office.setOfficeKind(kind);
        office.setConditions(request.getConditions());
        return OfficeResponse.from(officeRepository.save(office));
    }

    @Transactional
    public OfficeResponse updateOffice(UUID id, OfficeRequest request) {
        Office office = officeRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Espacio no encontrado"));

        // RN-080: no modificar datos críticos si hay reservas vigentes
        long activeReservations = officeRepository.countActiveReservations(id);
        if (activeReservations > 0) {
            boolean criticalChange = office.getCapacity() != request.getCapacity()
                    || !office.getOfficeKind().getId().equals(request.getOfficeKindId());

            if (criticalChange) {
                throw new AppException(HttpStatus.CONFLICT,
                        "No se pueden modificar datos críticos del espacio con reservas vigentes");
            }
        }

        OfficeKind kind = officeKindRepository.findById(request.getOfficeKindId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Tipo de espacio no encontrado"));

        office.setName(request.getName());
        office.setDescription(request.getDescription());
        office.setCapacity(request.getCapacity());
        office.setOfficeKind(kind);
        office.setConditions(request.getConditions());
        return OfficeResponse.from(officeRepository.save(office));
    }

    @Transactional
    public void disableOffice(UUID id) {
        Office office = officeRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Espacio no encontrado"));

        // RN-079: si tiene reservas, desactivar en vez de eliminar
        office.setEnabled(false);
        officeRepository.save(office);
    }

    @Transactional
    public void deleteOffice(UUID id) {
        Office office = officeRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Espacio no encontrado"));

        long activeReservations = officeRepository.countActiveReservations(id);
        if (activeReservations > 0) {
            throw new AppException(HttpStatus.CONFLICT,
                    "El espacio tiene reservas vigentes. Use desactivar en lugar de eliminar.");
        }

        officeRepository.delete(office);
    }

    // ===== OFFICE PLANS =====

    public List<OfficePlanResponse> getPlansByKind(UUID officeKindId) {
        return officePlanRepository.findAllByOfficeKindIdAndEnabledTrue(officeKindId).stream()
                .map(OfficePlanResponse::from)
                .toList();
    }

    public List<OfficePlanResponse> getAllPlans() {
        return officePlanRepository.findAll().stream()
                .map(OfficePlanResponse::from)
                .toList();
    }

    @Transactional
    public OfficePlanResponse createPlan(OfficePlanRequest request) {
        OfficeKind kind = officeKindRepository.findById(request.getOfficeKindId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Tipo de espacio no encontrado"));

        OfficePlan plan = new OfficePlan();
        plan.setOfficeKind(kind);
        plan.setPricePerHour(request.getPricePerHour());
        plan.setPlanDurationHours(request.getPlanDurationHours());
        return OfficePlanResponse.from(officePlanRepository.save(plan));
    }

    @Transactional
    public OfficePlanResponse updatePlan(UUID id, OfficePlanRequest request) {
        OfficePlan plan = officePlanRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Plan no encontrado"));

        OfficeKind kind = officeKindRepository.findById(request.getOfficeKindId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Tipo de espacio no encontrado"));

        // RN-082: cambios no retroactivos — solo afecta nuevas reservas
        plan.setOfficeKind(kind);
        plan.setPricePerHour(request.getPricePerHour());
        plan.setPlanDurationHours(request.getPlanDurationHours());
        return OfficePlanResponse.from(officePlanRepository.save(plan));
    }

    @Transactional
    public void disablePlan(UUID id) {
        OfficePlan plan = officePlanRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Plan no encontrado"));

        plan.setEnabled(false);
        officePlanRepository.save(plan);
    }

    // ===== OFFICE BLOCKS =====

    public List<OfficeBlockResponse> getBlocksByOffice(UUID officeId) {
        return officeBlockRepository.findAllByOfficeIdAndActiveTrue(officeId).stream()
                .map(OfficeBlockResponse::from)
                .toList();
    }

    @Transactional
    public OfficeBlockResponse createBlock(OfficeBlockRequest request, String adminEmail) {
        if (request.getBeginDate().isAfter(request.getEndDate())
                || request.getBeginDate().isEqual(request.getEndDate())) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "La hora de inicio debe ser anterior a la hora de fin");
        }

        if (request.getBeginDate().isBefore(OffsetDateTime.now())) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "No se pueden crear bloqueos en fechas pasadas");
        }

        Office office = officeRepository.findById(request.getOfficeId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Espacio no encontrado"));

        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Administrador no encontrado"));

        int overlapping = officeBlockRepository.countOverlapping(
                request.getOfficeId(), request.getBeginDate(), request.getEndDate());
        if (overlapping > 0) {
            throw new AppException(HttpStatus.CONFLICT,
                    "Ya existe un bloqueo en el rango horario seleccionado");
        }

        OfficeBlock block = new OfficeBlock();
        block.setOffice(office);
        block.setBlockedBy(admin);
        block.setBeginDate(request.getBeginDate());
        block.setEndDate(request.getEndDate());
        block.setReason(request.getReason());
        return OfficeBlockResponse.from(officeBlockRepository.save(block));
    }

    @Transactional
    public void cancelBlock(UUID blockId) {
        OfficeBlock block = officeBlockRepository.findById(blockId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Bloqueo no encontrado"));

        block.setActive(false);
        officeBlockRepository.save(block);
    }
}