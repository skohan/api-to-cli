package com.petstore.cli.command;

import java.util.concurrent.Callable;

import com.petstore.cli.CliContext;
import com.petstore.cli.auth.CredentialsStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Discards the cached bearer token for the current host (or all hosts with {@code --all}). */
@Command(
        name = "logout",
        description = "Discard the cached bearer token for the current host.",
        mixinStandardHelpOptions = true)
public final class LogoutCommand implements Callable<Integer> {

    @Option(names = "--all", description = "Clear cached tokens for every known host.")
    private boolean all;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        if (all) {
            CredentialsStore.hosts().keySet().forEach(CredentialsStore::clearToken);
            spec.commandLine().getOut().println("Logged out of all hosts.");
        } else {
            String host = CliContext.baseUrl();
            CredentialsStore.clearToken(host);
            spec.commandLine().getOut().println("Logged out of " + host + ".");
        }
        return 0;
    }
}
