package com.blusimian.blusimian.entityModel;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Maps a URL path segment to an EntityType case-insensitively, so the API can use
 * tidy lowercase paths (/api/custom-fields/company, /api/entity-layouts/company)
 * while the enum stays uppercase. Spring Boot auto-registers Converter beans into
 * the MVC conversion service, so this applies anywhere an EntityType is bound from
 * a request.
 *
 * An unrecognised value makes valueOf throw IllegalArgumentException, which Spring
 * surfaces as a 400 Bad Request -- the desired "no such entity type" response.
 */
@Component
public class StringToEntityTypeConverter implements Converter<String, EntityType> {

    @Override
    public EntityType convert(String source) {
        return EntityType.valueOf(source.trim().toUpperCase());
    }
}
