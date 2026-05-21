package com.workpool.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    List<Permission> findAllByIdIn(Set<UUID> ids);
}