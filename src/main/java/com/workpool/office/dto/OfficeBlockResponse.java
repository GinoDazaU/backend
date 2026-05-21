package com.workpool.office.dto;

import com.workpool.office.OfficeBlock;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
public class OfficeBlockResponse {

    private final UUID id;
    private final UUID officeId;
    private final String officeName;
    private final String blockedByEmail;
    private final OffsetDateTime beginDate;
    private final OffsetDateTime endDate;
    private final String reason;
    private final boolean active;

    private OfficeBlockResponse(UUID id, UUID officeId, String officeName,
                                String blockedByEmail, OffsetDateTime beginDate,
                                OffsetDateTime endDate, String reason, boolean active) {
        this.id = id;
        this.officeId = officeId;
        this.officeName = officeName;
        this.blockedByEmail = blockedByEmail;
        this.beginDate = beginDate;
        this.endDate = endDate;
        this.reason = reason;
        this.active = active;
    }

    public static OfficeBlockResponse from(OfficeBlock block) {
        return new OfficeBlockResponse(
                block.getId(),
                block.getOffice().getId(),
                block.getOffice().getName(),
                block.getBlockedBy().getEmail(),
                block.getBeginDate(),
                block.getEndDate(),
                block.getReason(),
                block.isActive()
        );
    }
}