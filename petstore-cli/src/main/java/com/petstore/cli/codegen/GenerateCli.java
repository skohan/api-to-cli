package com.petstore.cli.codegen;

import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

/**
 * Build-time entry point that replaces the openapi-generator-maven-plugin. Run in a
 * forked JVM by exec-maven-plugin during generate-sources, after a dedicated compiler
 * execution has compiled this package (and nothing else).
 *
 * Deliberately settings-free: every generator option lives in
 * src/main/resources/openapi-generator-config.yaml (single source of truth). Only the
 * output directory comes from the pom, because Maven owns the target/ layout.
 *
 * Args: [0] generator config yaml, [1] output dir.
 */
public final class GenerateCli {

    private GenerateCli() {
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: GenerateCli <configFile> <outputDir>");
            System.exit(1);
        }
        CodegenConfigurator configurator = CodegenConfigurator.fromFile(args[0]);
        configurator.setOutputDir(args[1]);
        new DefaultGenerator().opts(configurator.toClientOptInput()).generate();
    }
}
