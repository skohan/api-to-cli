package com.petstore.clicodegen;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.models.OpenAPI;
import org.openapitools.codegen.languages.JavaClientCodegen;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.OperationsMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stock 'java' client generator specialized for CLI generation. It orchestrates three
 * small collaborators; see each for the details:
 *
 * <ul>
 *   <li>{@link OperationFilter} -- keeps only operations tagged for the CLI, so the rest
 *       of the document never anchors generated code.</li>
 *   <li>{@link SchemaPruner} -- drops component schemas not reachable from the remaining
 *       operations, so no unused model classes are generated.</li>
 *   <li>{@link BodyFlattener} -- expands each request-body model into the
 *       {@code x-cli-fields} vendor extension that the CLI template renders as one
 *       picocli option per (nested) leaf field.</li>
 *   <li>{@link MultipartJsonPartFixer} -- rewrites the generated client so JSON multipart
 *       parts are sent as {@code application/json} rather than the stock template's
 *       {@code text/plain}, which servers reject.</li>
 * </ul>
 *
 * It also strips {@code uniqueItems} from all array schemas, so they generate as List
 * rather than Set: the stock model template's Set branch of {@code toUrlQueryString()}
 * hardcodes a String loop variable, which does not compile for arrays of enums.
 * Uniqueness remains the server's concern.
 */
public class CliJavaCodegen extends JavaClientCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(CliJavaCodegen.class);

    public static final String NAME = "cli-java";

    /** Tag that marks an operation as a CLI command; override with configOption cliTag. */
    protected String cliTag = "cli";

    /**
     * Per-JSON-part source replacements the {@link MultipartJsonPartFixer} applies to the
     * generated API file. Populated while post-processing operations (before the file is
     * rendered) and consumed in {@link #postProcessFile} (after it is written).
     */
    private final Map<String, String> multipartJsonRewrites = new LinkedHashMap<>();

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getHelp() {
        return "Java client generator that turns tagged operations into per-field CLI options.";
    }

    @Override
    public void processOpts() {
        super.processOpts();
        if (additionalProperties.containsKey("cliTag")) {
            cliTag = additionalProperties.get("cliTag").toString();
        }
        // Enable the postProcessFile hook so we can fix JSON multipart part Content-Types.
        setEnablePostProcessFile(true);
    }

    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
        super.preprocessOpenAPI(openAPI);
        // Unambiguous proof this class (not a stale jar) is running: search the build
        // log for "[cli-java]". If it's missing, the plugin resolved a different jar --
        // most commonly a stale one in ~/.m2, since `mvn package` never re-installs it.
        LOGGER.info("[cli-java] CliJavaCodegen active (cliTag=\"{}\")", cliTag);
        OperationFilter.retainTagged(openAPI, cliTag);
        FileResponseNormalizer.normalize(openAPI);
        SchemaPruner.pruneUnreachable(openAPI);
        OpenApiSchemaWalker.walkDocument(openAPI, schema -> {
            if (Boolean.TRUE.equals(schema.getUniqueItems())) {
                schema.setUniqueItems(null);
            }
        });
    }

    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) {
        OperationsMap result = super.postProcessOperationsWithModels(objs, allModels);
        BodyFlattener.attachCliFields(result, allModels);
        MultipartJsonPartFixer.collectRewrites(result, multipartJsonRewrites);
        return result;
    }

    @Override
    public void postProcessFile(File file, String fileType) {
        // Not delegating to super: its only behavior is spawning an external formatter
        // (JAVA_POST_PROCESS_FILE), which this project does not use. We only need the hook
        // to correct the Content-Type of JSON multipart parts in the generated API source.
        if ("api".equals(fileType)) {
            MultipartJsonPartFixer.apply(file.toPath(), multipartJsonRewrites);
        }
    }
}
