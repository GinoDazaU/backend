package com.workpool.office;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface OfficeKindRepository extends JpaRepository<OfficeKind, UUID> {
    Optional<OfficeKind> findByKindName(String kindName);
    boolean existsByKindName(String kindName);
}