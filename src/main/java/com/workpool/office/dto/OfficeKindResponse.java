package com.workpool.office.dto;

import com.workpool.office.OfficeKind;
import lombok.Getter;

import java.util.UUID;

@Getter
public class OfficeKindResponse {

    private final UUID id;
    private final String kindName;
    private final boolean enabled;

    private OfficeKindResponse(UUID id, String kindName, boolean enabled) {
        this.id = id;
        this.kindName = kindName;
        this.enabled = enabled;
    }

    public static OfficeKindResponse from(OfficeKind kind) {
        return new OfficeKindResponse(kind.getId(), kind.getKindName(), kind.isEnabled());
    }
}