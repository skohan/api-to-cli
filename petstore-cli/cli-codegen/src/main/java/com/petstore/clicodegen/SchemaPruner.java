package com.petstore.clicodegen;

import java.util.LinkedHashSet;
import java.util.Set;

import io.swagger.v3.oas.models.OpenAPI;

/**
 * Retains only the schemas in components/schemas that are transitively $ref-reachable
 * from the operations under paths, so no model classes are generated for schemas used
 * exclusively by operations that were filtered out.
 */
final class SchemaPruner {

    private SchemaPruner() {
    }

    static void pruneUnreachable(OpenAPI openAPI) {
        if (openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null) {
            return;
        }
        Set<String> reachable = new LinkedHashSet<>();
        OpenApiSchemaWalker.walkOperations(openAPI, schema -> {
            String ref = schema.get$ref();
            if (ref != null && ref.startsWith(OpenApiSchemaWalker.SCHEMA_REF_PREFIX)) {
                reachable.add(OpenApiSchemaWalker.refName(ref));
            }
        });
        openAPI.getComponents().getSchemas().keySet().retainAll(reachable);
    }
}
