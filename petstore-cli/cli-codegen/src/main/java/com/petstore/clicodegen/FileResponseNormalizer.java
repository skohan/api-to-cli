package com.petstore.clicodegen;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

/**
 * Ensures every {@code application/octet-stream} response declares a binary schema
 * ({@code type: string, format: binary}). Specs commonly leave the schema off
 * ({@code application/octet-stream: {}}), and without one the generator produces a void
 * client method that silently discards the body. With it, the method returns {@code File},
 * the operation is flagged {@code isResponseFile}, and the CLI template renders the
 * download behavior (--output option, save-and-report).
 */
final class FileResponseNormalizer {

    private static final String OCTET_STREAM = "application/octet-stream";

    private FileResponseNormalizer() {
    }

    static void normalize(OpenAPI openAPI) {
        if (openAPI.getPaths() != null) {
            for (PathItem pathItem : openAPI.getPaths().values()) {
                for (Operation op : pathItem.readOperations()) {
                    if (op.getResponses() != null) {
                        op.getResponses().values().forEach(FileResponseNormalizer::normalizeResponse);
                    }
                }
            }
        }
        if (openAPI.getComponents() != null && openAPI.getComponents().getResponses() != null) {
            openAPI.getComponents().getResponses().values()
                    .forEach(FileResponseNormalizer::normalizeResponse);
        }
    }

    private static void normalizeResponse(ApiResponse response) {
        if (response.getContent() == null) {
            return;
        }
        MediaType mediaType = response.getContent().get(OCTET_STREAM);
        if (mediaType != null && isSchemaless(mediaType.getSchema())) {
            mediaType.setSchema(new Schema<>().type("string").format("binary"));
        }
    }

    /** No schema at all, or an empty/typeless one -- nothing that would map to a return type. */
    private static boolean isSchemaless(Schema<?> schema) {
        return schema == null || (schema.getType() == null && schema.get$ref() == null);
    }
}
