# Adding the API-to-CLI framework to a Spring Boot project

This guide turns any Spring Boot project's `openapi.yaml` into a picocli-based CLI at
**build time**: tag an operation with `cli` in the spec, rebuild, and a command appears.

What you copy from this repo:

| From this repo | Purpose |
|---|---|
| `petstore-cli/cli-codegen/` | Custom openapi-generator (`cli-java`) that flattens request-body models into per-field options (`--customer.address.street`), registered via `META-INF/services` |
| `petstore-cli/pom.xml` | Build wiring: generator plugin + shade |
| `petstore-cli/src/main/resources/openapi-templates/cli_commands.mustache` | Emits one picocli command class per `cli`-tagged operation |
| `petstore-cli/src/main/resources/openapi-generator-config.yaml` | Registers the template as an extra generated file |
| `petstore-cli/src/main/java/com/petstore/cli/**` | Hand-written glue: entry point, config/token stores, SSO login, example command |
| `petstore.sh`, `petstore.cmd` | Launcher scripts |

Throughout, `acme-service` is your existing Spring Boot module and `acme-cli` the new
CLI module. Keeping the Java package `com.petstore.cli` unchanged is the fastest path;
renaming is an optional last step.

---

## Prerequisites

- Java 17+ (this repo uses 21), Maven 3.6+
- Your spec at `acme-service/src/main/resources/openapi.yaml`
- Every operation you want as a command **must have an `operationId`**

## Step 1 — Make the repo a multi-module reactor

Maven plugins can't depend on the module being built, so the custom generator must be a
sibling module built first. If your repo is a single Spring Boot module, move it into a
subdirectory and add an aggregator pom at the root:

```
acme-repo/
├─ pom.xml                 <- NEW aggregator (packaging=pom)
├─ acme-service/           <- your existing app, moved down one level (pom untouched)
│  └─ src/main/resources/openapi.yaml
└─ acme-cli/               <- new (Step 2/3)
   └─ cli-codegen/
```

Root `pom.xml` (aggregation only — your app keeps `spring-boot-starter-parent` or
whatever parent it already has; the two poms coexist because Maven aggregation and
inheritance are independent):

```xml
<project ...>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.acme</groupId>
  <artifactId>acme-parent</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>pom</packaging>
  <modules>
    <module>acme-service</module>
    <module>acme-cli/cli-codegen</module>   <!-- MUST come before acme-cli -->
    <module>acme-cli</module>
  </modules>
</project>
```

If this repo's parent pom properties (`openapi-generator.version`, `jackson.version`,
`picocli.version`, compiler release, `dependencyManagement`) are not in your parent,
copy them from [pom.xml](pom.xml) into the aggregator — both CLI module poms inherit
from it in this repo's layout. Alternatively inline the versions into the module poms.

## Step 2 — Copy the `cli-codegen` module

Copy `petstore-cli/cli-codegen/` to `acme-cli/cli-codegen/` unchanged except:

- `pom.xml`: set your `<parent>` coordinates (keep `<relativePath>../../pom.xml</relativePath>` —
  the module sits two levels below the root pom).

Do **not** touch `src/main/resources/META-INF/services/org.openapitools.codegen.CodegenConfig`
— that file is how openapi-generator's ServiceLoader discovers the `cli-java` generator
by name.

## Step 3 — Copy the CLI module

Copy from `petstore-cli/` to `acme-cli/`:

- `pom.xml`
- `src/main/java/com/petstore/cli/**` (all of it)
- `src/main/resources/openapi-templates/cli_commands.mustache`
- `src/main/resources/openapi-generator-config.yaml`

Then edit `acme-cli/pom.xml`:

1. `<parent>` → your aggregator's coordinates; `<artifactId>` → `acme-cli`.
2. Point the generator at the spec **inside the app module's resources**:

```xml
<inputSpec>${project.basedir}/../acme-service/src/main/resources/openapi.yaml</inputSpec>
```

3. The plugin dependency must match cli-codegen's coordinates:

```xml
<dependency>
  <groupId>com.acme</groupId>          <!-- your groupId -->
  <artifactId>cli-codegen</artifactId>
  <version>${project.version}</version>
</dependency>
```

## Step 4 — Annotate your `openapi.yaml`

1. Add `cli` to the `tags` of each operation to expose (it must also keep its original
   tag, and must have an `operationId`):

```yaml
/order/{id}:
  get:
    tags:
    - order
    - cli          # <- this makes it a CLI command
    operationId: getOrderById
```

2. For bearer-protected endpoints, add the scheme once under `components` and reference
   it per operation — protected commands then refuse to run without `login` first:

```yaml
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
```
```yaml
    security:
    - bearerAuth: []
```

The SSO endpoints used by `login` (`/sso/caf/authenticate/DB` →
`/sso/caf/authenticate/serviceticket`) are deliberately **not** in the spec; they are
hard-coded in `auth/AuthClient.java` — adjust the paths there if yours differ.

## Step 5 — Build

```
mvn clean install
```

Always `install`, not just `package`: Maven resolves **plugin** dependencies from the
local repository, not from the reactor, so `cli-codegen` must be installed before the
CLI module's generator plugin can load it. (After the first install, incremental
`mvn package -pl acme-cli` works until you change cli-codegen.)

Generated output lands in `acme-cli/target/generated-sources/openapi/`, including
`.../com/petstore/cli/command/GeneratedCliCommands.java` with one nested class per
`cli`-tagged operation.

## Step 6 — Register commands in the entry point

Open `src/main/java/com/petstore/cli/PetstoreCli.java` and list each generated class
(and any hand-written ones) in `subcommands`:

```java
subcommands = {
    GeneratedCliCommands.GetOrderById.class,   // nested class = capitalized operationId
    LoginCommand.class,
    LogoutCommand.class,
    WhoAmICommand.class,
}
```

Rebuild. This is the one manual step per new command; everything else about the
command (options, types, prompting, auth gate) is generated.

## Step 7 — Launcher script

Copy `petstore.sh` / `petstore.cmd` to your repo root, rename to `acme.sh` / `acme.cmd`,
and update the jar path inside:

```
acme-cli/target/acme-cli-1.0.0-SNAPSHOT.jar
```

The script builds the jar on first use, then execs it:

```
./acme.sh login -u -p                # picocli prompts; password hidden
./acme.sh getOrderById --id 42
./acme.sh --base-url http://staging:8080 getOrderById --id 42
```

Config precedence everywhere: `--flag` > `PETSTORE_BASE_URL`/`PETSTORE_API_KEY` env >
`~/.petstore-cli/.config` (written by `login`) > default.

## Day-2 workflow: exposing another API

1. Add `- cli` to the operation's tags in `openapi.yaml`.
2. `mvn clean install`
3. Add `GeneratedCliCommands.<OperationId>.class` to `PetstoreCli.subcommands`.

## Optional: renaming the `com.petstore.cli` packages

The generated code and glue are wired by name in several places; to rename, change all
of them consistently:

- `acme-cli/pom.xml`: `apiPackage`, `modelPackage`, `invokerPackage`, shade `mainClass`
- `cli_commands.mustache`: the `package` declaration and the `CliContext` import
- `openapi-generator-config.yaml`: the `folder:` of the generated commands file
- Physical packages of the hand-written classes (`CliContext`, `ConfigStore`,
  `PetstoreCli`, `auth/*`, `command/*`)
- `TokenStore`/`ConfigStore` paths if you want a different dot-directory than
  `~/.petstore-cli`

## Troubleshooting

- **`javac ... CompletionFailure: io.swagger.v3.oas.models... not found`** while
  compiling cli-codegen: corrupt/missing `swagger-models` jar in the local repo. Run
  `rm -rf ~/.m2/repository/io/swagger/core/v3` and rebuild; verify with
  `mvn -pl acme-cli/cli-codegen dependency:tree -Dincludes=io.swagger.core.v3:*`.
- **`Could not resolve ... cli-codegen` on the generator plugin**: you ran `package`
  before ever running `install`. Run `mvn clean install` from the root.
- **`package jakarta.annotation does not exist`**: keep `useJakartaEe=true` in
  configOptions paired with `jakarta.annotation-api` 2.x (already in the copied pom).
- **New command class missing after tagging**: the operation lacks the `cli` tag in the
  right place, or you edited a different copy of the spec than `inputSpec` points to.
