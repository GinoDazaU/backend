package com.workpool.office;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OfficeBlockRepository extends JpaRepository<OfficeBlock, UUID> {

    @Query(value = """
        SELECT COUNT(*) FROM office_blocks b
        WHERE b.office_id = :officeId
        AND b.is_active = true
        AND tstzrange(b.begin_date, b.end_date) && tstzrange(:start, :end)
        """, nativeQuery = true)
    int countOverlapping(UUID officeId, OffsetDateTime start, OffsetDateTime end);

    List<OfficeBlock> findAllByOfficeIdAndActiveTrue(UUID officeId);
}