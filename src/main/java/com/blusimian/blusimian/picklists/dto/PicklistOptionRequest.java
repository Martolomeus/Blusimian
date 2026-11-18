package com.blusimian.blusimian.picklists.dto;

/**
 * One option in a PUT. Without a code it's a new value, its code derived from the label.
 * active defaults to true. The rules are checked by PicklistService, so all come back in one 400.
 */
public record PicklistOptionRequest(String code, String label, Boolean active) {

    public boolean activeOrDefault() {
        return active == null || active;
    }
}
