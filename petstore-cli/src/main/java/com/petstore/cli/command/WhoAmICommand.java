package com.petstore.cli.command;

import java.util.Map;
import java.util.concurrent.Callable;

import com.petstore.cli.CliContext;
import com.petstore.cli.auth.CredentialsStore;
import com.petstore.cli.auth.CredentialsStore.HostCredentials;
import picocli.CommandLine.Command;

/**
 * Prints the effective configuration: the resolved current host and its cached
 * credentials, plus every other host known to the store. Hand-written, not generated.
 */
@Command(
        name = "whoami",
        description = "Show the current host, its cached credentials, and all known hosts.",
        mixinStandardHelpOptions = true)
public final class WhoAmICommand implements Callable<Integer> {

    @Override
    public Integer call() {
        String host = CliContext.baseUrl();
        HostCredentials creds = CredentialsStore.get(host);

        System.out.println("current host : " + host);
        System.out.println("username     : " + valueOrDash(creds == null ? null : creds.username));
        System.out.println("api-key      : " + (CliContext.apiKey() == null ? "-" : "(set)"));
        System.out.println("token        : " + (CliContext.hasBearerToken() ? "cached" : "not logged in"));
        System.out.println("store        : " + CredentialsStore.location());

        Map<String, HostCredentials> hosts = CredentialsStore.hosts();
        if (!hosts.isEmpty()) {
            System.out.println();
            System.out.println("known hosts:");
            hosts.forEach((h, c) -> System.out.printf("  %s %s (%s)%n",
                    h.equals(host) ? "*" : " ",
                    h,
                    c.token != null && !c.token.isBlank() ? "logged in" : "no token"));
        }
        return 0;
    }

    private static String valueOrDash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }
}
