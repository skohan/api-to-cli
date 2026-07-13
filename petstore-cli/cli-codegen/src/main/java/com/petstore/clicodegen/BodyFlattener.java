package com.petstore.clicodegen;

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
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.OperationsMap;

/**
 * Expands every request-body model into a flat list of leaf fields, published on the body
 * parameter as the {@code x-cli-fields} vendor extension. Each leaf carries a dotted option
 * name (e.g. {@code customer.address.street}), a Java field name, a CLI-safe type, and flags;
 * the CLI template turns each leaf into an individual command-line option.
 *
 * Recursion descends into single nested object models only. Values that cannot map to a
 * fixed set of options -- arrays of models, maps, free-form objects -- remain a single JSON
 * option ({@code isJson}). Enum-typed values (scalar or list, inline or $ref'd) become
 * String-based options, since a model's inner enum type neither resolves nor parses in the
 * generated command class; Jackson coerces them back at assembly time. Reference cycles are
 * broken via a visited stack.
 */
final class BodyFlattener {

    private BodyFlattener() {
    }

    static void attachCliFields(OperationsMap operations, List<ModelMap> allModels) {
        Map<String, CodegenModel> modelsByName = new HashMap<>();
        for (ModelMap modelMap : allModels) {
            CodegenModel model = modelMap.getModel();
            if (model != null) {
                modelsByName.put(model.classname, model);
                modelsByName.put(model.name, model);
            }
        }

        for (CodegenOperation operation : operations.getOperations().getOperation()) {
            for (CodegenParameter body : bodyParamsOf(operation)) {
                if (!body.isModel) {
                    continue;
                }
                CodegenModel root = resolve(body, modelsByName);
                if (root == null) {
                    continue;
                }
                List<Map<String, Object>> leaves = new ArrayList<>();
                expand(root, modelsByName, "", "", leaves, new ArrayDeque<>());
                body.vendorExtensions.put("x-cli-fields", leaves);
            }
            // Model-typed non-body params -- typically the JSON part of a multipart/form-data
            // request -- get the same flattening, with option names prefixed by the part name
            // (e.g. --details.caption) so they cannot collide with other parameters. The leaf
            // "path" stays relative so the assembled map serializes as the part's own JSON.
            //
            // The param is also RETYPED to String: the stock native client sends non-file
            // multipart parts via addTextBody(param.toString()), and a model's toString() is
            // not JSON. With a String parameter the client transmits exactly the JSON text
            // the CLI assembles. The template keys on x-cli-form-model for these params.
            for (CodegenParameter param : nonBodyModelParamsOf(operation)) {
                CodegenModel root = resolve(param, modelsByName);
                if (root == null) {
                    continue;
                }
                List<Map<String, Object>> leaves = new ArrayList<>();
                expand(root, modelsByName, "", "", leaves, new ArrayDeque<>());
                for (Map<String, Object> leaf : leaves) {
                    leaf.put("optionName", param.baseName + "." + leaf.get("optionName"));
                    leaf.put("fieldName", param.paramName + capitalize((String) leaf.get("fieldName")));
                }
                param.vendorExtensions.put("x-cli-fields", leaves);
                param.vendorExtensions.put("x-cli-form-model", true);
                param.dataType = "String";
                param.datatypeWithEnum = "String";
                param.baseType = "String";
                param.isModel = false;
                param.isString = true;
            }
        }
    }

    private static CodegenModel resolve(CodegenParameter param, Map<String, CodegenModel> modelsByName) {
        CodegenModel root = modelsByName.get(param.dataType);
        return root != null ? root : modelsByName.get(param.baseType);
    }

    /**
     * Every representation of each model-typed, non-body, non-file parameter (allParams
     * plus the per-kind lists -- these may be distinct instances, like body params).
     */
    private static List<CodegenParameter> nonBodyModelParamsOf(CodegenOperation operation) {
        List<CodegenParameter> params = new ArrayList<>();
        for (List<CodegenParameter> list : List.of(
                operation.allParams == null ? List.<CodegenParameter>of() : operation.allParams,
                operation.formParams == null ? List.<CodegenParameter>of() : operation.formParams,
                operation.queryParams == null ? List.<CodegenParameter>of() : operation.queryParams,
                operation.headerParams == null ? List.<CodegenParameter>of() : operation.headerParams)) {
            for (CodegenParameter p : list) {
                if (p.isModel && !p.isBodyParam && !p.isFile && !p.isContainer) {
                    params.add(p);
                }
            }
        }
        return params;
    }

    /**
     * Every representation of the body parameter: bodyParam, bodyParams, and the allParams
     * entry the template iterates -- these may be distinct instances.
     */
    private static List<CodegenParameter> bodyParamsOf(CodegenOperation operation) {
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
        return bodyParams;
    }

    private static void expand(CodegenModel model, Map<String, CodegenModel> models,
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

            out.add(leaf(property, path, field, nested, models));
        }
    }

    private static Map<String, Object> leaf(CodegenProperty property, String path, String field,
                                            CodegenModel nested, Map<String, CodegenModel> models) {
        // property.items.isModel / isEnum are not reliably propagated for $ref'd items in
        // every generator version (a List<$ref-to-enum> can report both false). Resolve the
        // item type against the models map directly -- the same technique used for `nested`
        // above -- rather than trusting those flags.
        CodegenModel itemsModel = (property.items != null && property.items.complexType != null)
                ? models.get(property.items.complexType)
                : null;
        boolean itemsIsEnum = (property.items != null && property.items.isEnum)
                || (itemsModel != null && itemsModel.isEnum);
        boolean itemsIsModel = !itemsIsEnum
                && ((property.items != null && property.items.isModel) || itemsModel != null);

        boolean listOfModels = property.isContainer && itemsIsModel;
        boolean unflattenable = listOfModels || property.isMap
                || (property.isModel && property.isContainer)
                || (property.isModel && !property.isEnum && nested == null); // free-form / cyclic object

        // Depending on the generator version, a List of inline enums may report isEnum on
        // the property itself, only on items, or neither -- cover all of them.
        boolean scalarEnum = property.isEnum && !property.isContainer;
        boolean listOfEnums = property.isContainer && (property.isEnum || itemsIsEnum);

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
        return leaf;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
