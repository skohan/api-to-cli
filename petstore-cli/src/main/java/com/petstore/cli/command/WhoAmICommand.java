package com.petstore.cli.command;

import java.util.concurrent.Callable;

import com.petstore.cli.CliContext;
import com.petstore.cli.auth.CredentialsStore;
import picocli.CommandLine.Command;

/**
 * Prints the effective configuration: the resolved host, its stored username, whether a session
 * token is present, and the config file location. The token value is never printed -- only
 * whether it is set. Hand-written, not generated.
 */
@Command(
        name = "whoami",
        description = "Show the current host, stored username, and whether logged in.",
        mixinStandardHelpOptions = true)
public final class WhoAmICommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("host     : " + CliContext.baseUrl());
        System.out.println("username : " + valueOrDash(CredentialsStore.username()));
        System.out.println("token    : " + (CliContext.hasBearerToken() ? "cached" : "not logged in"));
        System.out.println("config   : " + CredentialsStore.location());
        return 0;
    }

    private static String valueOrDash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }
}
