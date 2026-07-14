package com.petstore.cli.command;

import java.io.Console;
import java.util.Arrays;
import java.util.concurrent.Callable;

import com.petstore.cli.CliContext;
import com.petstore.cli.auth.AuthClient;
import com.petstore.cli.auth.CredentialsStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/**
 * Authenticates against the SSO endpoints and caches the resulting bearer token, which
 * protected commands then send automatically.
 *
 * The host is accepted here only (ordinary commands take no {@code --base-url}): it is persisted
 * together with the bearer token to the hidden {@code .config} so later commands need no flags.
 * The password is never stored -- the cached bearer token (service ticket) is the durable
 * credential.
 *
 * The password is always read from an interactive console prompt and is deliberately NOT a
 * command-line option, so it can never leak into shell history or the process list. The username
 * may be supplied with {@code -u alice} or, when omitted, is prompted for.
 */
@Command(
        name = "login",
        description = "Authenticate and cache a bearer token for protected commands.",
        mixinStandardHelpOptions = true)
public final class LoginCommand implements Callable<Integer> {

    @Option(names = "--base-url",
            description = "Base URL of the Petstore API to log in to. "
                    + "Precedence: this flag > $PETSTORE_BASE_URL > stored host > http://localhost.")
    private String baseUrl;

    @Option(names = {"-u", "--username"},
            required = true,
            arity = "0..1",
            interactive = true,
            echo = true,
            prompt = "Username: ",
            description = "Username. Pass '-u' without a value to be prompted.")
    private String username;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        if (username == null || username.isBlank()) {
            throw new ParameterException(spec.commandLine(), "Username must not be empty.");
        }
        Console console = System.console();
        if (console == null) {
            spec.commandLine().getErr().println(
                    "No interactive console is available to read the password. Run login in a terminal.");
            return 2;
        }
        char[] password = console.readPassword("Password: ");
        if (password == null) {
            password = new char[0];
        }
        try {
            if (password.length == 0) {
                throw new ParameterException(spec.commandLine(), "Password must not be empty.");
            }
            String host = CliContext.resolveBaseUrl(baseUrl);
            String token = new AuthClient(host).login(username, new String(password));

            CredentialsStore.save(host, token, username);
            spec.commandLine().getOut().println("Login successful for " + host + ".");
            spec.commandLine().getOut().println("Credentials saved at " + CredentialsStore.location() + ".");
            return 0;
        } catch (RuntimeException e) {
            spec.commandLine().getErr().println("Login failed: " + e.getMessage());
            return 1;
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
