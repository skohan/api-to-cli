package com.petstore.clicodegen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.model.OperationsMap;

/**
 * Sends JSON multipart parts as {@code application/json} in the generated native API client.
 *
 * The stock native {@code api.mustache} emits {@code addTextBody(name, value.toString())} for
 * every non-file multipart part. Apache HttpMime stamps such a part as
 * {@code text/plain; charset=ISO-8859-1}, which servers that bind the part to a JSON object
 * reject ("Content-Type 'text/plain;charset=ISO-8859-1' is not supported").
 *
 * The parts that actually carry JSON are exactly the model-typed parts {@link BodyFlattener}
 * retyped to String and marked {@code x-cli-form-model}. For each of those, this rewrites the
 * generated {@code addTextBody(name, value.toString())} statement to the three-arg overload
 * with {@code ContentType.APPLICATION_JSON}. Plain string parts are left as text/plain, which
 * is correct for them.
 *
 * A source rewrite (rather than a template override) keeps the customization surface tiny: it
 * targets one stable, generated statement per JSON part instead of owning the whole stock
 * api.mustache. It is applied from {@code postProcessFile}; see {@link CliJavaCodegen}.
 */
final class MultipartJsonPartFixer {

    private MultipartJsonPartFixer() {
    }

    /**
     * Collects an old-&gt;new source replacement for each JSON (x-cli-form-model) form part in
     * the operations, into {@code rewrites}. Keyed by the exact generated statement so applying
     * it is an idempotent, unambiguous substring replace.
     */
    static void collectRewrites(OperationsMap operations, Map<String, String> rewrites) {
        for (CodegenOperation op : operations.getOperations().getOperation()) {
            if (op.formParams == null) {
                continue;
            }
            for (CodegenParameter part : op.formParams) {
                if (!Boolean.TRUE.equals(part.vendorExtensions.get("x-cli-form-model"))) {
                    continue;
                }
                String call = "multiPartBuilder.addTextBody(\"" + part.baseName + "\", " + part.paramName;
                rewrites.put(
                        call + ".toString());",
                        call + ", org.apache.http.entity.ContentType.APPLICATION_JSON);");
            }
        }
    }

    /** Applies the collected replacements to a generated source file, if any statement matches. */
    static void apply(Path file, Map<String, String> rewrites) {
        if (rewrites.isEmpty()) {
            return;
        }
        try {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            String updated = source;
            for (Map.Entry<String, String> rewrite : rewrites.entrySet()) {
                updated = updated.replace(rewrite.getKey(), rewrite.getValue());
            }
            if (!updated.equals(source)) {
                Files.writeString(file, updated, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not rewrite multipart JSON parts in " + file, e);
        }
    }
}
