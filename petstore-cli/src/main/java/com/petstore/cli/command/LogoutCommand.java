package com.petstore.cli.command;

import java.util.concurrent.Callable;

import com.petstore.cli.auth.TokenStore;
import picocli.CommandLine.Command;

/** Clears the cached bearer token. */
@Command(
        name = "logout",
        description = "Discard the cached bearer token.",
        mixinStandardHelpOptions = true)
public final class LogoutCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        TokenStore.clear();
        System.out.println("Logged out. Cached token removed.");
        return 0;
    }
}
