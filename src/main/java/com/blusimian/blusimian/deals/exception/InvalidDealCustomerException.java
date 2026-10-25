package com.blusimian.blusimian.deals.exception;

import com.blusimian.blusimian.platform.web.BadRequestException;
import com.blusimian.blusimian.platform.web.ErrorCode;

import java.util.UUID;

/**
 * Thrown when a deal references a customer company that doesn't exist (the customer_id
 * FK is violated). Extends the shared BadRequestException, mapping to 400 with code
 * VALIDATION_ERROR.
 */
public class InvalidDealCustomerException extends BadRequestException {

    public InvalidDealCustomerException(UUID customerId) {
        super(ErrorCode.VALIDATION_ERROR, "Unknown customer company '" + customerId + "'");
    }
}
