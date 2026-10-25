package com.blusimian.blusimian.entitySchema;

import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entitySchema.dto.EntitySchemaResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * HTTP layer for the entity schema bundle (§7).
 *
 * GET /api/entities/{entityType}/schema returns the cacheable metadata bundle
 * (fields + the caller's layout) for that type. The response carries a content-derived ETag, so a
 * client caches the bundle per type and revalidates cheaply: when nothing changed the
 * ETag matches and we answer 304; any customization write changes the content, hence
 * the ETag, hence a fresh 200. {entityType} binds via StringToEntityTypeConverter, so
 * an unknown value yields 400.
 */
@RestController
@RequestMapping("/api/entities")
public class EntitySchemaController {

    /**
     * no-cache: a browser may store the bundle but must revalidate it before every use,
     * which it does itself with If-None-Match -- so clients get cheap 304s without any
     * conditional-request code. private: the bundle is per caller (role, later tenant),
     * and a shared cache keys on the URL alone.
     */
    private static final CacheControl CACHE_CONTROL = CacheControl.noCache().cachePrivate();

    private final EntitySchemaService service;

    public EntitySchemaController(EntitySchemaService service) {
        this.service = service;
    }

    // GET /api/entities/company/schema
    @GetMapping("/{entityType}/schema")
    public ResponseEntity<EntitySchemaResponse> schema(
            @PathVariable EntityType entityType,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        EntitySchemaResponse body = service.getSchema(entityType);
        String etag = etagFor(body);

        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(CACHE_CONTROL).build();
        }
        return ResponseEntity.ok().eTag(etag).cacheControl(CACHE_CONTROL).body(body);
    }

    /**
     * A strong ETag derived from the bundle's content: SHA-256 over its record
     * toString(), which a record generates deterministically across all components
     * (recursively through the nested records, lists, and enums). Content in, hash
     * out -- so it changes exactly when the schema changes (a custom field
     * added/renamed/archived, the layout edited) and not otherwise. Using toString()
     * rather than serialized JSON keeps the ETag independent of any HTTP machinery.
     */
    private String etagFor(EntitySchemaResponse body) {
        try {
            byte[] content = body.toString().getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return "\"" + HexFormat.of().formatHex(digest) + "\"";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to compute schema ETag", e);
        }
    }
}
