package com.petstore.cli.command;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
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
 * Prompting uses picocli's built-in interactive options: {@code login -u -p} (flags
 * without values) makes picocli prompt for both, with password input hidden and bound to
 * a char[] that is wiped after use. If a flag is omitted entirely, the command falls back
 * to an equivalent manual prompt, so a bare {@code login} also works.
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
            arity = "0..1",
            interactive = true,
            echo = true,
            prompt = "Username: ",
            description = "Username. Pass without a value to be prompted.")
    private String username;

    @Option(names = {"-p", "--password"},
            arity = "0..1",
            interactive = true,
            prompt = "Password: ",
            description = "Password. Pass without a value to be prompted (input hidden).")
    private char[] password;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        if (username == null || username.isBlank()) {
            username = promptLine("Username");
        }
        if (password == null || password.length == 0) {
            password = promptSecret("Password");
        }
        if (username == null || username.isBlank() || password == null || password.length == 0) {
            throw new ParameterException(spec.commandLine(), "Username and password are required.");
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

    /** Manual prompt for when the option was omitted entirely (mirrors the interactive UX). */
    private static String promptLine(String label) {
        Console console = System.console();
        if (console != null) {
            return console.readLine("%s: ", label);
        }
        return readStdin(label);
    }

    private static char[] promptSecret(String label) {
        Console console = System.console();
        if (console != null) {
            return console.readPassword("%s: ", label);
        }
        String line = readStdin(label);
        return line == null ? new char[0] : line.toCharArray();
    }

    /** Single shared reader: a fresh BufferedReader per prompt would buffer ahead and
     *  starve later prompts when stdin is piped. */
    private static BufferedReader stdin;

    private static String readStdin(String label) {
        System.out.print(label + ": ");
        System.out.flush();
        try {
            if (stdin == null) {
                stdin = new BufferedReader(new InputStreamReader(System.in));
            }
            return stdin.readLine();
        } catch (IOException e) {
            return null;
        }
    }
}
