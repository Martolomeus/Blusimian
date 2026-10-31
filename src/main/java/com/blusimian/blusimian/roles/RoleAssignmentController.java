package com.blusimian.blusimian.roles;

import com.blusimian.blusimian.roles.dto.RoleAssignmentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** HTTP layer for role assignments. users knows nothing of roles, so its paths are served here. */
@RestController
public class RoleAssignmentController {

    private final RoleAssignmentService service;

    public RoleAssignmentController(RoleAssignmentService service) {
        this.service = service;
    }

    // GET /api/role-assignments -- every assignment in the tenant.
    @GetMapping("/api/role-assignments")
    public List<RoleAssignmentResponse> list() {
        return service.list().stream().map(RoleAssignmentResponse::from).toList();
    }

    // PUT /api/users/{userId}/roles/{roleId} -- idempotent.
    @PutMapping("/api/users/{userId}/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(@PathVariable UUID userId, @PathVariable UUID roleId) {
        service.assign(userId, roleId);
    }

    // DELETE /api/users/{userId}/roles/{roleId} -- idempotent; 409 on the last Administrator.
    @DeleteMapping("/api/users/{userId}/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(@PathVariable UUID userId, @PathVariable UUID roleId) {
        service.unassign(userId, roleId);
    }
}
