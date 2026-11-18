package com.blusimian.blusimian.picklists.dto;

import java.util.List;

/** The whole list, in display order. Every stored code must still be in it. */
public record ReplacePicklistRequest(List<PicklistOptionRequest> options) {
}
