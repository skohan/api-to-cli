package com.petstore.clicodegen;

/**
 * String constants shared between the codegen post-processors and the {@code cli_commands}
 * template: the vendor-extension names published on parameters, the keys of each flattened
 * leaf map, and the CLI-safe Java types. Centralised here so the two sides stay in step and the
 * literals are not repeated across the generator.
 */
final class CliCodegenConstants {

    private CliCodegenConstants() {
    }

    // --- Vendor extensions attached to parameters ---
    /** List of flattened leaf fields the template renders as individual options. */
    static final String EXT_CLI_FIELDS = "x-cli-fields";
    /** Marks a model-typed multipart part retyped to a JSON String (see BodyFlattener). */
    static final String EXT_CLI_FORM_MODEL = "x-cli-form-model";

    // --- Keys of a flattened leaf map (consumed by cli_commands.mustache) ---
    static final String LEAF_OPTION_NAME = "optionName";
    static final String LEAF_PATH = "path";
    static final String LEAF_FIELD_NAME = "fieldName";
    static final String LEAF_BASE_NAME = "baseName";
    static final String LEAF_REQUIRED = "required";
    static final String LEAF_DESCRIPTION = "description";
    static final String LEAF_IS_ENUM = "isEnum";
    static final String LEAF_IS_JSON = "isJson";
    static final String LEAF_IS_MAP = "isMap";
    static final String LEAF_DATA_TYPE = "dataType";
    static final String LEAF_JSON_TYPE = "jsonType";
    static final String LEAF_ALLOWED = "allowed";

    /** Key under which openapi-generator exposes an enum's permitted values. */
    static final String ALLOWABLE_VALUES = "values";

    // --- CLI-safe Java types emitted for options ---
    static final String TYPE_STRING = "String";
    static final String TYPE_LIST_STRING = "java.util.List<String>";
}
