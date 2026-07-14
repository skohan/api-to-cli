package com.petstore.cli.command;

import java.util.concurrent.Callable;

import com.petstore.cli.auth.CredentialsStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/** Logs out by deleting the stored config file (host, token, and username). */
@Command(
        name = "logout",
        description = "Log out by deleting the stored config file.",
        mixinStandardHelpOptions = true)
public final class LogoutCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        CredentialsStore.clear();
        spec.commandLine().getOut().println("Logged out and removed " + CredentialsStore.location() + ".");
        return 0;
    }
}
