package com.petstore.cli.command;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Callable;

import com.petstore.cli.CliContext;
import com.petstore.cli.ConfigStore;
import com.petstore.cli.auth.AuthClient;
import com.petstore.cli.auth.TokenStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/**
 * Authenticates against the SSO endpoints and stores the resulting bearer token, which
 * protected commands then send automatically.
 *
 * Prompting is entirely picocli's built-in interactive mechanism: {@code login -u -p}
 * prompts for both values (password hidden, bound to a char[] wiped after use);
 * {@code -u alice -p} prompts only for the password. The options are required, so a bare
 * {@code login} fails fast with a usage error instead of a hand-rolled prompt.
 *
 * On success, the effective base URL, username, and api key (if any) are persisted to
 * {@code ~/.petstore-cli/.config} so subsequent commands need no flags. The password is
 * never persisted -- the cached bearer token is the durable credential.
 */
@Command(
        name = "login",
        description = "Authenticate and cache a bearer token for protected commands.",
        mixinStandardHelpOptions = true)
public final class LoginCommand implements Callable<Integer> {

    @Option(names = {"-u", "--username"},
            required = true,
            arity = "0..1",
            interactive = true,
            echo = true,
            prompt = "Username: ",
            description = "Username. Pass '-u' without a value to be prompted.")
    private String username;

    @Option(names = {"-p", "--password"},
            required = true,
            arity = "0..1",
            interactive = true,
            prompt = "Password: ",
            description = "Password. Pass '-p' without a value to be prompted (input hidden).")
    private char[] password;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        // Options are present (required=true); guard against empty values typed at the prompt.
        if (username == null || username.isBlank() || password == null || password.length == 0) {
            throw new ParameterException(spec.commandLine(), "Username and password must not be empty.");
        }
        try {
            String token = new AuthClient(CliContext.baseUrl()).login(username, new String(password));
            TokenStore.save(token);
            ConfigStore.save(Map.of(
                    ConfigStore.KEY_BASE_URL, CliContext.baseUrl(),
                    ConfigStore.KEY_USERNAME, username,
                    ConfigStore.KEY_API_KEY, CliContext.apiKey() == null ? "" : CliContext.apiKey()));
            System.out.println("Login successful.");
            System.out.println("Token cached at " + TokenStore.location());
            System.out.println("Config saved at " + ConfigStore.location());
            return 0;
        } catch (RuntimeException e) {
            System.err.println("Login failed: " + e.getMessage());
            return 1;
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
