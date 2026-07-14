package com.petstore.cli;

import com.petstore.cli.command.GeneratedCliCommands;
import com.petstore.cli.command.LoginCommand;
import com.petstore.cli.command.LogoutCommand;
import com.petstore.cli.command.WhoAmICommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
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

    // Host and api key are captured once at `login` (see LoginCommand) and read from the
    // stored config or PETSTORE_BASE_URL/PETSTORE_API_KEY thereafter; they are intentionally
    // not global flags on ordinary commands.

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        // No subcommand given: show usage on the command's configured output stream.
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new PetstoreCli()).execute(args));
    }
}
