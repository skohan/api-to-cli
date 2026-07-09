package com.petstore.clicodegen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;

/** Removes every operation that does not carry the given tag; drops paths left empty. */
final class OperationFilter {

    private OperationFilter() {
    }

    static void retainTagged(OpenAPI openAPI, String tag) {
        if (openAPI.getPaths() == null) {
            return;
        }
        List<String> emptyPaths = new ArrayList<>();
        for (Map.Entry<String, PathItem> entry : openAPI.getPaths().entrySet()) {
            PathItem pathItem = entry.getValue();
            for (Map.Entry<PathItem.HttpMethod, Operation> op : pathItem.readOperationsMap().entrySet()) {
                List<String> tags = op.getValue().getTags();
                if (tags == null || !tags.contains(tag)) {
                    pathItem.operation(op.getKey(), null);
                }
            }
            if (pathItem.readOperations().isEmpty()) {
                emptyPaths.add(entry.getKey());
            }
        }
        emptyPaths.forEach(openAPI.getPaths()::remove);
    }
}
