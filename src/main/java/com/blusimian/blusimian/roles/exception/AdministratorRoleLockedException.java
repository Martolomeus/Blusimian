package com.blusimian.blusimian.roles.exception;

import com.blusimian.blusimian.platform.web.ConflictException;

/** The Administrator role can't be edited or deleted. Maps to 409. */
public class AdministratorRoleLockedException extends ConflictException {

    public AdministratorRoleLockedException(String action) {
        super("The Administrator role can't be " + action);
    }
}
