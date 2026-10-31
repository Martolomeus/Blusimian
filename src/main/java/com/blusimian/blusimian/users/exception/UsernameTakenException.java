package com.blusimian.blusimian.users.exception;

import com.blusimian.blusimian.platform.web.ConflictException;

/** The username exists already, in this tenant or another. Maps to 409 Conflict. */
public class UsernameTakenException extends ConflictException {

    public UsernameTakenException(String username) {
        super("Username '" + username + "' is already taken");
    }
}
