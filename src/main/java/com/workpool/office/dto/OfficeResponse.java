package com.workpool.office.dto;

import com.workpool.office.Office;
import lombok.Getter;

import java.util.UUID;

@Getter
public class OfficeResponse {

    private final UUID id;
    private final String name;
    private final String description;
    private final int capacity;
    private final String officeKindName;
    private final UUID officeKindId;
    private final String conditions;
    private final boolean enabled;

    private OfficeResponse(UUID id, String name, String description, int capacity,
                           String officeKindName, UUID officeKindId, String conditions,
                           boolean enabled) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.capacity = capacity;
        this.officeKindName = officeKindName;
        this.officeKindId = officeKindId;
        this.conditions = conditions;
        this.enabled = enabled;
    }

    public static OfficeResponse from(Office office) {
        return new OfficeResponse(
                office.getId(),
                office.getName(),
                office.getDescription(),
                office.getCapacity(),
                office.getOfficeKind().getKindName(),
                office.getOfficeKind().getId(),
                office.getConditions(),
                office.isEnabled()
        );
    }
}