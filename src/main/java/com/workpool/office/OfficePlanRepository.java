package com.workpool.office;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OfficePlanRepository extends JpaRepository<OfficePlan, UUID> {
    List<OfficePlan> findAllByOfficeKindIdAndEnabledTrue(UUID officeKindId);
}