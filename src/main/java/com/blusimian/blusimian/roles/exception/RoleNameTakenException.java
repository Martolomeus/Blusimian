package com.blusimian.blusimian.roles.exception;

import com.blusimian.blusimian.platform.web.ConflictException;

/** The tenant has a role of this name already, in any case. Maps to 409. */
public class RoleNameTakenException extends ConflictException {

    public RoleNameTakenException(String name) {
        super("A role named '" + name + "' already exists");
    }
}
