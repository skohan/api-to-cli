package com.petstore.cli.command;

import java.util.concurrent.Callable;

import com.petstore.cli.CliContext;
import com.petstore.cli.auth.CredentialsStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Discards the cached bearer token for the current host (or all hosts with {@code --all}). */
@Command(
        name = "logout",
        description = "Discard the cached bearer token for the current host.",
        mixinStandardHelpOptions = true)
public final class LogoutCommand implements Callable<Integer> {

    @Option(names = "--all", description = "Clear cached tokens for every known host.")
    private boolean all;

    @Override
    public Integer call() {
        if (all) {
            CredentialsStore.hosts().keySet().forEach(CredentialsStore::clearToken);
            System.out.println("Logged out of all hosts.");
        } else {
            String host = CliContext.baseUrl();
            CredentialsStore.clearToken(host);
            System.out.println("Logged out of " + host + ".");
        }
        return 0;
    }
}
