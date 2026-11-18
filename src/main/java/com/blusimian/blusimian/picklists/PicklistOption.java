package com.blusimian.blusimian.picklists;

/** One picklist value as the bundle and the picklist API serve it. Inactive ones are included. */
public record PicklistOption(String code, String label, boolean active) {
}
