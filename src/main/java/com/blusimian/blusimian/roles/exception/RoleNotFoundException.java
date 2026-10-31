package com.blusimian.blusimian.roles.exception;

import com.blusimian.blusimian.platform.web.NotFoundException;

/** No role with this id in the current tenant. Maps to 404. */
public class RoleNotFoundException extends NotFoundException {

    public RoleNotFoundException() {
        super("Role not found");
    }
}
