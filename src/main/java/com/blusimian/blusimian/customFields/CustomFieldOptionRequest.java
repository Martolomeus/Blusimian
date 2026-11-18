package com.blusimian.blusimian.customFields;

/**
 * One option of a new PICKLIST field; code is optional, derived from the label when absent.
 * Mirrors picklists' PicklistOptionRequest without active, so customFields needn't know picklists.
 */
public record CustomFieldOptionRequest(String code, String label) {
}
