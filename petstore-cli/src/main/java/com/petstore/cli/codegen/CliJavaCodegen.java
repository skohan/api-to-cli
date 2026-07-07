package com.petstore.cli.codegen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.languages.JavaClientCodegen;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.OperationMap;
import org.openapitools.codegen.model.OperationsMap;

/**
 * Stock 'java' client generator, plus one enhancement: every request-body model is
 * recursively expanded into a flat list of leaf fields, published on the body parameter
 * as the {@code x-cli-fields} vendor extension. Each leaf carries a dotted option name
 * (e.g. {@code customer.address.street}), a Java field name, its type, and flags. The CLI
 * template turns each leaf into an individual command-line option, so a caller fills in
 * nested fields one by one instead of pasting JSON.
 *
 * Recursion descends into single nested object models only. Values that cannot map to a
 * fixed set of options -- arrays of models, maps, free-form objects -- remain a single
 * JSON option. Reference cycles are broken via a visited stack.
 *
 * BUILD-TIME ONLY: this package is compiled early by a dedicated compiler execution and
 * run by {@link GenerateCli} during generate-sources. It is excluded from the shaded CLI
 * jar, and its openapi-generator dependency is 'provided' so nothing leaks into runtime.
 */
public class CliJavaCodegen extends JavaClientCodegen {

    public static final String NAME = "cli-java";

    public CliJavaCodegen() {
        super();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getHelp() {
        return "Java client generator that also flattens request-body models into per-field CLI options.";
    }

    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) {
        OperationsMap result = super.postProcessOperationsWithModels(objs, allModels);

        Map<String, CodegenModel> modelsByName = new HashMap<>();
        for (ModelMap modelMap : allModels) {
            CodegenModel model = modelMap.getModel();
            if (model != null) {
                modelsByName.put(model.classname, model);
                modelsByName.put(model.name, model);
            }
        }

        OperationMap operations = result.getOperations();
        for (CodegenOperation operation : operations.getOperation()) {
            // Attach to every representation of the body parameter (bodyParam, bodyParams,
            // and the allParams entry the template iterates -- these may be distinct instances).
            List<CodegenParameter> bodyParams = new ArrayList<>();
            if (operation.bodyParam != null) {
                bodyParams.add(operation.bodyParam);
            }
            if (operation.bodyParams != null) {
                bodyParams.addAll(operation.bodyParams);
            }
            if (operation.allParams != null) {
                for (CodegenParameter p : operation.allParams) {
                    if (p.isBodyParam) {
                        bodyParams.add(p);
                    }
                }
            }
            for (CodegenParameter body : bodyParams) {
                if (!body.isModel) {
                    continue;
                }
                CodegenModel root = modelsByName.get(body.dataType);
                if (root == null) {
                    root = modelsByName.get(body.baseType);
                }
                if (root == null) {
                    continue;
                }
                List<Map<String, Object>> leaves = new ArrayList<>();
                expand(root, modelsByName, "", "", leaves, new ArrayDeque<>());
                body.vendorExtensions.put("x-cli-fields", leaves);
            }
        }
        return result;
    }

    private void expand(CodegenModel model, Map<String, CodegenModel> models,
                        String pathPrefix, String fieldPrefix,
                        List<Map<String, Object>> out, Deque<String> visiting) {
        if (model == null || model.vars == null) {
            return;
        }
        for (CodegenProperty property : model.vars) {
            String path = pathPrefix.isEmpty() ? property.baseName : pathPrefix + "." + property.baseName;
            String field = fieldPrefix.isEmpty()
                    ? property.name
                    : fieldPrefix + capitalize(property.name);

            CodegenModel nested = (property.isModel && !property.isContainer)
                    ? models.get(property.complexType)
                    : null;

            if (nested != null && !visiting.contains(nested.classname)) {
                visiting.push(nested.classname);
                expand(nested, models, path, field, out, visiting);
                visiting.pop();
                continue;
            }

            boolean listOfModels = property.isContainer
                    && property.items != null && property.items.isModel;
            boolean unflattenable = listOfModels || property.isMap
                    || (property.isModel && property.isContainer)
                    || (property.isModel && nested == null); // free-form / cyclic object

            Map<String, Object> leaf = new LinkedHashMap<>();
            leaf.put("optionName", path);
            leaf.put("path", path);
            leaf.put("fieldName", field);
            leaf.put("baseName", property.baseName);
            leaf.put("required", property.required);
            leaf.put("description", property.description == null ? "" : property.description);
            leaf.put("isEnum", property.isEnum);
            leaf.put("isJson", unflattenable);
            leaf.put("dataType", (property.isEnum || unflattenable) ? "String" : property.datatypeWithEnum);
            leaf.put("jsonType", property.dataType);
            if (property.isEnum && property.allowableValues != null) {
                Object values = property.allowableValues.get("values");
                if (values != null) {
                    leaf.put("allowed", String.valueOf(values));
                }
            }
            out.add(leaf);
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
