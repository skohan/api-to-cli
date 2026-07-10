package com.petstore.cli;

import com.petstore.cli.command.GeneratedCliCommands;
import com.petstore.cli.command.LoginCommand;
import com.petstore.cli.command.LogoutCommand;
import com.petstore.cli.command.WhoAmICommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Spec;

/**
 * CLI entry point. Registers every generated command plus any hand-written ones.
 *
 * To expose another API operation as a command: add the "cli" tag to it in openapi.yaml
 * and rebuild -- a new GeneratedCliCommands.* class appears, which you then add to the
 * subcommands list below.
 *
 * To add a bespoke command: write a picocli @Command class (see {@link WhoAmICommand})
 * and add it to the subcommands list below.
 */
@Command(
        name = "petstore",
        description = "Command line interface for the Petstore API.",
        mixinStandardHelpOptions = true,
        version = "petstore-cli 1.0.0",
        subcommands = {
                // --- generated from operations tagged "cli" in openapi.yaml ---
                GeneratedCliCommands.FindPetsByStatus.class,
                GeneratedCliCommands.GetPetById.class,
                GeneratedCliCommands.UpdatePetWithForm.class,
                GeneratedCliCommands.DeletePet.class,
                GeneratedCliCommands.UploadFile.class,
                GeneratedCliCommands.DownloadPetPhoto.class,
                GeneratedCliCommands.CreateCustomerOrder.class,
                GeneratedCliCommands.PlaceOrder.class,
                // --- your own hand-written commands ---
                LoginCommand.class,
                LogoutCommand.class,
                WhoAmICommand.class,
        })
public final class PetstoreCli implements Runnable {

    @Option(names = "--base-url",
            scope = ScopeType.INHERIT,
            description = "Base URL of the Petstore API. Precedence: this flag > $PETSTORE_BASE_URL > current host in .petstore-cli.json > http://localhost.")
    private String baseUrl;

    @Option(names = "--api-key",
            scope = ScopeType.INHERIT,
            description = "Value sent as the api_key header. Precedence: this flag > $PETSTORE_API_KEY > .petstore-cli.json.")
    private String apiKey;

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        // No subcommand given: show usage on the command's configured output stream.
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    public static void main(String[] args) {
        PetstoreCli root = new PetstoreCli();
        CommandLine commandLine = new CommandLine(root);
        // Push the global options into the shared context before the subcommand runs.
        commandLine.setExecutionStrategy(parseResult -> {
            CliContext.configure(root.baseUrl, root.apiKey);
            return new CommandLine.RunLast().execute(parseResult);
        });
        System.exit(commandLine.execute(args));
    }
}
