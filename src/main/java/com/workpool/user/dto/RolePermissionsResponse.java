package com.workpool.user.dto;

import com.workpool.user.Permission;
import com.workpool.user.Role;
import lombok.Getter;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
public class RolePermissionsResponse {

    private final UUID roleId;
    private final String roleName;
    private final Set<PermissionItem> permissions;

    private RolePermissionsResponse(UUID roleId, String roleName, Set<PermissionItem> permissions) {
        this.roleId = roleId;
        this.roleName = roleName;
        this.permissions = permissions;
    }

    public static RolePermissionsResponse from(Role role) {
        Set<PermissionItem> items = role.getPermissions().stream()
                .map(p -> new PermissionItem(p.getId(), p.getPermissionName()))
                .collect(Collectors.toSet());
        return new RolePermissionsResponse(role.getId(), role.getRoleName(), items);
    }

    @Getter
    public static class PermissionItem {
        private final UUID id;
        private final String permissionName;

        public PermissionItem(UUID id, String permissionName) {
            this.id = id;
            this.permissionName = permissionName;
        }
    }
}