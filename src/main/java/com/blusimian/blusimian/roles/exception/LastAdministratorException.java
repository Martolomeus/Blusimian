package com.blusimian.blusimian.roles.exception;

import com.blusimian.blusimian.platform.web.ConflictException;

/** No cross-tenant console exists, so a tenant can't recover its last Administrator. Maps to 409. */
public class LastAdministratorException extends ConflictException {

    public LastAdministratorException() {
        super("At least one user must stay an Administrator.");
    }
}
