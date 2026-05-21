package com.workpool.user;

import com.workpool.common.response.ApiResponse;
import com.workpool.user.dto.RolePermissionsResponse;
import com.workpool.user.dto.UpdateRolePermissionsRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;

    @GetMapping
    @PreAuthorize("hasAuthority('EDITAR_PERMISOS_ROLES')")
    public ResponseEntity<ApiResponse<List<RolePermissionsResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok("Roles con permisos",
                rolePermissionService.getAllRolesWithPermissions()));
    }

    @GetMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('EDITAR_PERMISOS_ROLES')")
    public ResponseEntity<ApiResponse<RolePermissionsResponse>> getPermissions(
            @PathVariable UUID roleId) {
        return ResponseEntity.ok(ApiResponse.ok("Permisos del rol",
                rolePermissionService.getRolePermissions(roleId)));
    }

    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('EDITAR_PERMISOS_ROLES')")
    public ResponseEntity<ApiResponse<RolePermissionsResponse>> updatePermissions(
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRolePermissionsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Permisos actualizados",
                rolePermissionService.updateRolePermissions(roleId, request)));
    }
}