package com.blusimian.blusimian.users.exception;

import com.blusimian.blusimian.platform.web.NotFoundException;

/** No user with this id in the current tenant. Maps to 404. */
public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException() {
        super("User not found");
    }
}
