package com.blusimian.blusimian.roles.exception;

import com.blusimian.blusimian.platform.web.ConflictException;

/** A role still held by a user can't be deleted. Maps to 409. */
public class RoleInUseException extends ConflictException {

    public RoleInUseException(long holders) {
        super(holders == 1 ? "1 user still has this role." : holders + " users still have this role.");
    }
}
