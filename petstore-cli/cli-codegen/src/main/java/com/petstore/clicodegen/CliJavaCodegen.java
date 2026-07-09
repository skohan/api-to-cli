package com.petstore.clicodegen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
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

    /**
     * Strips {@code uniqueItems: true} from every array schema before code generation, so
     * arrays become {@code List} rather than {@code Set}. This sidesteps an upstream bug in
     * the stock model template: the {@code Set} branch of {@code toUrlQueryString()} declares
     * its loop variable as {@code String} regardless of the element type, which fails to
     * compile for arrays of enums (and mis-numbers the index). The {@code List} branch is
     * index-based and correct. Uniqueness is still enforced by the server; the client model
     * simply does not deduplicate -- an acceptable trade for a CLI that sends JSON bodies.
     */
    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
        super.preprocessOpenAPI(openAPI);
        if (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
            Set<Schema> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Schema<?> schema : openAPI.getComponents().getSchemas().values()) {
                clearUniqueItems(schema, visited);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private void clearUniqueItems(Schema schema, Set<Schema> visited) {
        if (schema == null || !visited.add(schema)) {
            return;
        }
        if (Boolean.TRUE.equals(schema.getUniqueItems())) {
            schema.setUniqueItems(null);
        }
        clearUniqueItems(schema.getItems(), visited);
        if (schema.getProperties() != null) {
            for (Object value : schema.getProperties().values()) {
                if (value instanceof Schema) {
                    clearUniqueItems((Schema) value, visited);
                }
            }
        }
        if (schema.getAdditionalProperties() instanceof Schema) {
            clearUniqueItems((Schema) schema.getAdditionalProperties(), visited);
        }
        clearUniqueItemsAll(schema.getAllOf(), visited);
        clearUniqueItemsAll(schema.getAnyOf(), visited);
        clearUniqueItemsAll(schema.getOneOf(), visited);
    }

    @SuppressWarnings("rawtypes")
    private void clearUniqueItemsAll(Collection<Schema> schemas, Set<Schema> visited) {
        if (schemas != null) {
            for (Schema s : schemas) {
                clearUniqueItems(s, visited);
            }
        }
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
            // A $ref to a standalone enum schema resolves to a CodegenModel that IS the
            // enum (no vars). Never recurse into it -- treat the property as an enum leaf.
            if (nested != null && nested.isEnum) {
                nested = null;
            }

            if (nested != null && !visiting.contains(nested.classname)) {
                visiting.push(nested.classname);
                expand(nested, models, path, field, out, visiting);
                visiting.pop();
                continue;
            }

            boolean listOfModels = property.isContainer
                    && property.items != null && property.items.isModel && !property.items.isEnum;
            boolean unflattenable = listOfModels || property.isMap
                    || (property.isModel && property.isContainer)
                    || (property.isModel && !property.isEnum && nested == null); // free-form / cyclic object

            // Whether the value is enum-typed in the model. Depending on the generator
            // version, a List of inline enums may report isEnum on the property itself,
            // only on items, or neither -- cover all of them so the CLI option is always
            // String-based (Jackson coerces to the enum at assembly time). Otherwise the
            // generated field would reference a model's inner enum type, which does not
            // resolve (and does not parse from the command line) in GeneratedCliCommands.
            boolean scalarEnum = property.isEnum && !property.isContainer;
            boolean listOfEnums = property.isContainer
                    && (property.isEnum || (property.items != null && property.items.isEnum));

            String cliType;
            if (unflattenable || scalarEnum) {
                cliType = "String";
            } else if (listOfEnums) {
                cliType = "java.util.List<String>";
            } else {
                cliType = property.datatypeWithEnum;
            }

            Map<String, Object> leaf = new LinkedHashMap<>();
            leaf.put("optionName", path);
            leaf.put("path", path);
            leaf.put("fieldName", field);
            leaf.put("baseName", property.baseName);
            leaf.put("required", property.required);
            leaf.put("description", property.description == null ? "" : property.description);
            leaf.put("isEnum", property.isEnum);
            leaf.put("isJson", unflattenable);
            leaf.put("dataType", cliType);
            leaf.put("jsonType", property.dataType);
            Map<String, Object> allowableValues = property.allowableValues != null
                    ? property.allowableValues
                    : (property.items != null ? property.items.allowableValues : null);
            if ((scalarEnum || listOfEnums) && allowableValues != null) {
                Object values = allowableValues.get("values");
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
