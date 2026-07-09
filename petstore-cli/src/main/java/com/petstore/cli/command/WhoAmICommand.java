package com.petstore.cli.command;

import java.util.concurrent.Callable;

import com.petstore.cli.CliContext;
import com.petstore.cli.ConfigStore;
import com.petstore.cli.auth.TokenStore;
import picocli.CommandLine.Command;

/**
 * Example of a hand-written command. It is NOT generated from the OpenAPI spec and lives
 * in the module's own source tree, yet it is registered in {@link com.petstore.cli.PetstoreCli}
 * exactly like the generated ones. This is the extension point: write a picocli command,
 * add it to the root command's {@code subcommands}, done.
 */
@Command(
        name = "whoami",
        description = "Prints the effective CLI configuration (a hand-written, non-generated command).",
        mixinStandardHelpOptions = true)
public final class WhoAmICommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("base-url : " + CliContext.baseUrl());
        System.out.println("username : " + valueOrDash(ConfigStore.get(ConfigStore.KEY_USERNAME)));
        System.out.println("api-key  : " + (CliContext.apiKey() == null ? "-" : "(set)"));
        System.out.println("token    : " + (TokenStore.load().isPresent() ? "cached" : "not logged in"));
        System.out.println("config   : " + ConfigStore.location());
        return 0;
    }

    private static String valueOrDash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }
}
