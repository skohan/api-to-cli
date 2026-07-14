package com.petstore.clicodegen;

import static com.petstore.clicodegen.CliCodegenConstants.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        Map<String, CodegenModel> modelsByName = indexModels(allModels);
        operations.getOperations().getOperation().forEach(operation -> {
            attachBodyFields(operation, modelsByName);
            attachFormModelFields(operation, modelsByName);
        });
    }

    /** Indexes every model by both its classname and its name, for lookup during expansion. */
    private static Map<String, CodegenModel> indexModels(List<ModelMap> allModels) {
        Map<String, CodegenModel> byName = new HashMap<>();
        allModels.stream()
                .map(ModelMap::getModel)
                .filter(Objects::nonNull)
                .forEach(model -> {
                    byName.put(model.classname, model);
                    byName.put(model.name, model);
                });
        return byName;
    }

    /** Body models: one flattened option per (recursively) nested leaf field. */
    private static void attachBodyFields(CodegenOperation operation, Map<String, CodegenModel> modelsByName) {
        for (CodegenParameter body : bodyParamsOf(operation)) {
            CodegenModel root = body.isModel ? resolve(body, modelsByName) : null;
            if (root != null) {
                body.vendorExtensions.put(EXT_CLI_FIELDS, flatten(root, modelsByName));
            }
        }
    }

    /**
     * Model-typed non-body params -- typically the JSON part of a multipart/form-data request --
     * get the same flattening, with option names prefixed by the part name (e.g. --details.caption)
     * so they cannot collide with other parameters; the leaf "path" stays relative so the assembled
     * map serializes as the part's own JSON. The param is also retyped to String: the stock native
     * client sends non-file multipart parts via addTextBody(param.toString()), and a model's
     * toString() is not JSON, so a String parameter transmits exactly the JSON the CLI assembles.
     */
    private static void attachFormModelFields(CodegenOperation operation, Map<String, CodegenModel> modelsByName) {
        for (CodegenParameter param : nonBodyModelParamsOf(operation)) {
            CodegenModel root = resolve(param, modelsByName);
            if (root == null) {
                continue;
            }
            List<Map<String, Object>> leaves = flatten(root, modelsByName);
            leaves.forEach(leaf -> prefixWithPartName(leaf, param));
            param.vendorExtensions.put(EXT_CLI_FIELDS, leaves);
            param.vendorExtensions.put(EXT_CLI_FORM_MODEL, true);
            retypeToString(param);
        }
    }

    /** Flattens a model into its list of leaf-field maps. */
    private static List<Map<String, Object>> flatten(CodegenModel root, Map<String, CodegenModel> modelsByName) {
        List<Map<String, Object>> leaves = new ArrayList<>();
        expand(root, modelsByName, "", "", leaves, new ArrayDeque<>());
        return leaves;
    }

    /** Prefixes a leaf's option/field names with the multipart part name so they stay unique. */
    private static void prefixWithPartName(Map<String, Object> leaf, CodegenParameter param) {
        leaf.put(LEAF_OPTION_NAME, param.baseName + "." + leaf.get(LEAF_OPTION_NAME));
        leaf.put(LEAF_FIELD_NAME, param.paramName + capitalize((String) leaf.get(LEAF_FIELD_NAME)));
    }

    /** Retypes a form-model param to a plain JSON String so the client sends it verbatim. */
    private static void retypeToString(CodegenParameter param) {
        param.dataType = TYPE_STRING;
        param.datatypeWithEnum = TYPE_STRING;
        param.baseType = TYPE_STRING;
        param.isModel = false;
        param.isString = true;
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
        Map<String, Object> leaf = new LinkedHashMap<>();
        leaf.put(LEAF_OPTION_NAME, path);
        leaf.put(LEAF_PATH, path);
        leaf.put(LEAF_FIELD_NAME, field);
        leaf.put(LEAF_BASE_NAME, property.baseName);
        leaf.put(LEAF_REQUIRED, property.required);
        leaf.put(LEAF_DESCRIPTION, property.description == null ? "" : property.description);
        leaf.put(LEAF_IS_ENUM, property.isEnum);
        leaf.put(LEAF_IS_JSON, isUnflattenable(property, nested, models));
        leaf.put(LEAF_IS_MAP, isFlattenableMap(property));
        leaf.put(LEAF_DATA_TYPE, resolveCliType(property, nested, models));
        leaf.put(LEAF_JSON_TYPE, property.dataType);
        putAllowedValues(leaf, property, models);
        return leaf;
    }

    private static String resolveCliType(CodegenProperty property, CodegenModel nested,
                                         Map<String, CodegenModel> models) {
        if (isFlattenableMap(property)) {
            // OpenAPI map keys are always strings; the value type drives picocli's conversion.
            return "java.util.Map<String, " + mapValueSchema(property).dataType + ">";
        }
        if (isUnflattenable(property, nested, models) || isScalarEnum(property)) {
            return TYPE_STRING;
        }
        if (isListOfEnums(property, models)) {
            return TYPE_LIST_STRING;
        }
        return property.datatypeWithEnum;
    }

    /**
     * A value that cannot map onto a fixed set of options -- an array of models, a map of
     * non-scalars, or a free-form / cyclic object -- and so stays a single JSON option.
     */
    private static boolean isUnflattenable(CodegenProperty property, CodegenModel nested,
                                           Map<String, CodegenModel> models) {
        return (property.isContainer && itemsIsModel(property, models))
                || (property.isMap && !isFlattenableMap(property))
                || (property.isModel && property.isContainer)
                || (property.isModel && !property.isEnum && nested == null);
    }

    /**
     * A map whose values are a simple scalar (Map&lt;String,String&gt;, Map&lt;String,Integer&gt;, ...),
     * which becomes a repeatable picocli key=value option rather than one opaque JSON blob. Maps of
     * models / arrays / other maps / enums stay JSON -- key=value cannot express those.
     */
    private static boolean isFlattenableMap(CodegenProperty property) {
        CodegenProperty mapValue = mapValueSchema(property);
        return property.isMap
                && mapValue != null
                && mapValue.dataType != null
                && !mapValue.isModel
                && !mapValue.isContainer
                && !mapValue.isMap
                && !mapValue.isEnum;
    }

    private static boolean isScalarEnum(CodegenProperty property) {
        return property.isEnum && !property.isContainer;
    }

    /** A List of enums, whose isEnum may sit on the property, only on its items, or neither. */
    private static boolean isListOfEnums(CodegenProperty property, Map<String, CodegenModel> models) {
        return property.isContainer && (property.isEnum || itemsIsEnum(property, models));
    }

    // property.items.isModel / isEnum are not reliably propagated for $ref'd items in every
    // generator version (a List<$ref-to-enum> can report both false), so the helpers below
    // resolve the item type against the models map directly rather than trusting those flags.

    private static CodegenModel itemsModel(CodegenProperty property, Map<String, CodegenModel> models) {
        return (property.items != null && property.items.complexType != null)
                ? models.get(property.items.complexType)
                : null;
    }

    private static boolean itemsIsEnum(CodegenProperty property, Map<String, CodegenModel> models) {
        CodegenModel model = itemsModel(property, models);
        return (property.items != null && property.items.isEnum) || (model != null && model.isEnum);
    }

    private static boolean itemsIsModel(CodegenProperty property, Map<String, CodegenModel> models) {
        return !itemsIsEnum(property, models)
                && ((property.items != null && property.items.isModel) || itemsModel(property, models) != null);
    }

    /** Adds the enum's permitted values to the leaf, when the generator exposes them. */
    private static void putAllowedValues(Map<String, Object> leaf, CodegenProperty property,
                                         Map<String, CodegenModel> models) {
        if (!isScalarEnum(property) && !isListOfEnums(property, models)) {
            return;
        }
        Map<String, Object> allowableValues = property.allowableValues != null
                ? property.allowableValues
                : (property.items != null ? property.items.allowableValues : null);
        if (allowableValues == null) {
            return;
        }
        Object values = allowableValues.get(ALLOWABLE_VALUES);
        if (values != null) {
            leaf.put(LEAF_ALLOWED, String.valueOf(values));
        }
    }

    /**
     * The value schema of a map property. openapi-generator has, across versions, exposed the
     * {@code additionalProperties} value type via {@code CodegenProperty.items} and via
     * {@code CodegenProperty.additionalProperties}; return whichever is populated so map
     * detection does not silently regress to a JSON blob on a version that uses the other.
     */
    private static CodegenProperty mapValueSchema(CodegenProperty property) {
        if (!property.isMap) {
            return null;
        }
        return property.items != null ? property.items : property.getAdditionalProperties();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
