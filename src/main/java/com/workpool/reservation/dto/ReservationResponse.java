package com.workpool.reservation.dto;

import com.workpool.reservation.Reservation;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
public class ReservationResponse {

    private final UUID id;
    private final UUID officeId;
    private final String officeName;
    private final String status;
    private final OffsetDateTime beginDate;
    private final OffsetDateTime endDate;
    private final int personAmount;
    private final boolean usesParking;
    private final BigDecimal pricePerHour;
    private final BigDecimal totalPriceUsd;
    private final BigDecimal totalPricePen;
    private final BigDecimal exchangeRate;
    private final String representativeName;
    private final String representativeLastName;
    private final String representativeDni;
    private final boolean createdByAdmin;
    private final OffsetDateTime expiresAt;
    private final List<GuestItem> guests;
    private final List<String> vehiclePlates;
    private final OffsetDateTime createdAt;

    private ReservationResponse(UUID id, UUID officeId, String officeName, String status,
                                OffsetDateTime beginDate, OffsetDateTime endDate, int personAmount,
                                boolean usesParking, BigDecimal pricePerHour, BigDecimal totalPriceUsd,
                                BigDecimal totalPricePen, BigDecimal exchangeRate,
                                String representativeName, String representativeLastName,
                                String representativeDni, boolean createdByAdmin,
                                OffsetDateTime expiresAt, List<GuestItem> guests,
                                List<String> vehiclePlates, OffsetDateTime createdAt) {
        this.id = id;
        this.officeId = officeId;
        this.officeName = officeName;
        this.status = status;
        this.beginDate = beginDate;
        this.endDate = endDate;
        this.personAmount = personAmount;
        this.usesParking = usesParking;
        this.pricePerHour = pricePerHour;
        this.totalPriceUsd = totalPriceUsd;
        this.totalPricePen = totalPricePen;
        this.exchangeRate = exchangeRate;
        this.representativeName = representativeName;
        this.representativeLastName = representativeLastName;
        this.representativeDni = representativeDni;
        this.createdByAdmin = createdByAdmin;
        this.expiresAt = expiresAt;
        this.guests = guests;
        this.vehiclePlates = vehiclePlates;
        this.createdAt = createdAt;
    }

    public static ReservationResponse from(Reservation r) {
        List<GuestItem> guests = r.getGuests().stream()
                .map(g -> new GuestItem(g.getId(), g.getName(), g.getLastName(), g.getDni()))
                .toList();

        List<String> plates = r.getVehicles().stream()
                .map(v -> v.getPlate())
                .toList();

        return new ReservationResponse(
                r.getId(),
                r.getOffice().getId(),
                r.getOffice().getName(),
                r.getReservationStatus().getStatusName(),
                r.getBeginDate(),
                r.getEndDate(),
                r.getPersonAmount(),
                r.isUsesParking(),
                r.getPricePerHour(),
                r.getTotalPriceUsd(),
                r.getTotalPricePen(),
                r.getExchangeRate(),
                r.getRepresentativeName(),
                r.getRepresentativeLastName(),
                r.getRepresentativeDni(),
                r.isCreatedByAdmin(),
                r.getExpiresAt(),
                guests,
                plates,
                r.getCreatedAt()
        );
    }

    @Getter
    public static class GuestItem {
        private final UUID id;
        private final String name;
        private final String lastName;
        private final String dni;

        public GuestItem(UUID id, String name, String lastName, String dni) {
            this.id = id;
            this.name = name;
            this.lastName = lastName;
            this.dni = dni;
        }
    }
}