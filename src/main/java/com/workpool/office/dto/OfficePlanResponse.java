package com.workpool.office.dto;

import com.workpool.office.OfficePlan;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class OfficePlanResponse {

    private final UUID id;
    private final String officeKindName;
    private final UUID officeKindId;
    private final BigDecimal pricePerHour;
    private final int planDurationHours;
    private final boolean enabled;

    private OfficePlanResponse(UUID id, String officeKindName, UUID officeKindId,
                               BigDecimal pricePerHour, int planDurationHours, boolean enabled) {
        this.id = id;
        this.officeKindName = officeKindName;
        this.officeKindId = officeKindId;
        this.pricePerHour = pricePerHour;
        this.planDurationHours = planDurationHours;
        this.enabled = enabled;
    }

    public static OfficePlanResponse from(OfficePlan plan) {
        return new OfficePlanResponse(
                plan.getId(),
                plan.getOfficeKind().getKindName(),
                plan.getOfficeKind().getId(),
                plan.getPricePerHour(),
                plan.getPlanDurationHours(),
                plan.isEnabled()
        );
    }
}