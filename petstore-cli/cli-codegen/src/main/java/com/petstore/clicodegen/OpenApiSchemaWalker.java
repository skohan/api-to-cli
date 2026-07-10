package com.petstore.clicodegen;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;

/**
 * The single depth-first traversal over every {@link Schema} in an OpenAPI document:
 * operation parameters, request bodies, responses (including headers), and nested schema
 * structure (properties, items, additionalProperties, allOf/anyOf/oneOf/not). {@code $ref}s
 * to components (schemas, parameters, requestBodies, responses) are resolved and followed.
 * Cycle-safe via an identity-based visited set; each schema object is visited exactly once.
 */
final class OpenApiSchemaWalker {

    static final String SCHEMA_REF_PREFIX = "#/components/schemas/";

    private final Components components;
    private final Consumer<Schema<?>> visitor;
    private final Set<Schema<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());

    private OpenApiSchemaWalker(Components components, Consumer<Schema<?>> visitor) {
        this.components = components;
        this.visitor = visitor;
    }

    /** Visits every schema in the document: all component schemas plus everything under paths. */
    static void walkDocument(OpenAPI openAPI, Consumer<Schema<?>> visitor) {
        OpenApiSchemaWalker walker = new OpenApiSchemaWalker(openAPI.getComponents(), visitor);
        walker.componentSchemas();
        walker.paths(openAPI);
    }

    /** Visits only schemas reachable from operations under paths (following $refs into components). */
    static void walkOperations(OpenAPI openAPI, Consumer<Schema<?>> visitor) {
        new OpenApiSchemaWalker(openAPI.getComponents(), visitor).paths(openAPI);
    }

    static String refName(String ref) {
        return ref.substring(ref.lastIndexOf('/') + 1);
    }

    private void componentSchemas() {
        if (components != null && components.getSchemas() != null) {
            components.getSchemas().values().forEach(this::schema);
        }
    }

    private void paths(OpenAPI openAPI) {
        if (openAPI.getPaths() == null) {
            return;
        }
        for (PathItem pathItem : openAPI.getPaths().values()) {
            if (pathItem.getParameters() != null) {
                pathItem.getParameters().forEach(this::parameter);
            }
            for (Operation op : pathItem.readOperations()) {
                if (op.getParameters() != null) {
                    op.getParameters().forEach(this::parameter);
                }
                requestBody(op.getRequestBody());
                if (op.getResponses() != null) {
                    op.getResponses().values().forEach(this::response);
                }
            }
        }
    }

    private void parameter(Parameter parameter) {
        if (parameter == null) {
            return;
        }
        if (parameter.get$ref() != null && components != null && components.getParameters() != null) {
            parameter(components.getParameters().get(refName(parameter.get$ref())));
        }
        schema(parameter.getSchema());
        content(parameter.getContent());
    }

    private void requestBody(RequestBody body) {
        if (body == null) {
            return;
        }
        if (body.get$ref() != null && components != null && components.getRequestBodies() != null) {
            requestBody(components.getRequestBodies().get(refName(body.get$ref())));
        }
        content(body.getContent());
    }

    private void response(ApiResponse response) {
        if (response == null) {
            return;
        }
        if (response.get$ref() != null && components != null && components.getResponses() != null) {
            response(components.getResponses().get(refName(response.get$ref())));
        }
        content(response.getContent());
        if (response.getHeaders() != null) {
            for (Header header : response.getHeaders().values()) {
                schema(header.getSchema());
                content(header.getContent());
            }
        }
    }

    private void content(Content content) {
        if (content != null) {
            for (MediaType mediaType : content.values()) {
                schema(mediaType.getSchema());
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void schema(Schema schema) {
        if (schema == null || !visited.add(schema)) {
            return;
        }
        visitor.accept(schema);
        if (schema.get$ref() != null && schema.get$ref().startsWith(SCHEMA_REF_PREFIX)
                && components != null && components.getSchemas() != null) {
            schema(components.getSchemas().get(refName(schema.get$ref())));
        }
        schema(schema.getItems());
        if (schema.getProperties() != null) {
            for (Object value : schema.getProperties().values()) {
                if (value instanceof Schema<?> s) {
                    schema(s);
                }
            }
        }
        if (schema.getAdditionalProperties() instanceof Schema<?> s) {
            schema(s);
        }
        schemas(schema.getAllOf());
        schemas(schema.getAnyOf());
        schemas(schema.getOneOf());
        schema(schema.getNot());
    }

    @SuppressWarnings("rawtypes")
    private void schemas(Collection<Schema> schemas) {
        if (schemas != null) {
            schemas.forEach(this::schema);
        }
    }
}
