# idem-examples

Usage examples for the [Idem](https://github.com/idem-finance/idem) ledger
SDK (`idem-sdk-kotlin`) — an open-source, event-sourced double-entry ledger
for institutions settling cross-border payments on stablecoin rails.

This repo is intentionally separate from the main `idem` monorepo and MIT
licensed (see [License](#license)) so every example here is copy-paste-safe
into your own project, unlike the main repo's FSL license.

## Prerequisites

- Docker (for Postgres, Redis, and the Idem app)
- Java 21
- Git

## Quick start

```bash
git clone https://github.com/idem-finance/idem-examples
cd idem-examples
cp .env.example .env

# Start Postgres + Redis + the published Idem image
docker compose up -d

# One-off: seed a dev tenant and print an ADMIN-scoped API key
docker compose run --rm -e SPRING_PROFILES_ACTIVE=dev,seed app
# copy the printed IDEM_API_KEY=... into .env

# Compile all examples
./mvnw compile

# Run one
./mvnw compile exec:java -Dexec.mainClass=finance.idem.examples.basic.BasicTransactionExampleKt
```

### Configuring `.env`

Every example reads two variables — `IDEM_BASE_URL` and `IDEM_API_KEY` — via a
shared `requiredEnv()` helper
([`support/Env.kt`](src/main/kotlin/finance/idem/examples/support/Env.kt))
backed by [`dotenv-kotlin`](https://github.com/cdimascio/dotenv-kotlin). It
loads `.env` from the project root automatically, so once the file is filled
in you can run any example directly — no manual `export`/`source` step
needed. A real environment variable, if set, always takes precedence over
the value in `.env`.

`.env` is gitignored; `.env.example` is the checked-in template:

```bash
cp .env.example .env
```

Then fill in:

| Variable | Value |
|---|---|
| `IDEM_BASE_URL` | `http://localhost:8081` when running the stack via `docker compose up -d` above |
| `IDEM_API_KEY` | The key printed by the `docker compose run --rm -e SPRING_PROFILES_ACTIVE=dev,seed app` seed step |

```bash
# .env
IDEM_BASE_URL=http://localhost:8081
IDEM_API_KEY=sk_live_...   # from the seed step's printed output
```

If `.env` is missing or a variable is blank, each example fails fast with
`IDEM_BASE_URL is not set — see .env.example` (or the equivalent for
`IDEM_API_KEY`) rather than a confusing SDK-level error.

## Examples

| # | Example | What it shows |
|---|---|---|
| 01 | [`basic/BasicTransactionExample.kt`](src/main/kotlin/finance/idem/examples/basic/BasicTransactionExample.kt) | A simple fiat double-entry transaction — debit/credit, `postTransaction`, `getBalance` |
| 02 | [`onchain/StablecoinOnChainExample.kt`](src/main/kotlin/finance/idem/examples/onchain/StablecoinOnChainExample.kt) | A cross-border stablecoin transaction mixing on-chain entries in one transaction, with an explicit idempotency key |
| 03 | [`settlement/PendingSettlementExample.kt`](src/main/kotlin/finance/idem/examples/settlement/PendingSettlementExample.kt) | The settlement lifecycle — `registerSettlement`, forcing a match via `reconcileBatch`, `getSettlement` showing `SETTLED`, and `cancelSettlement` showing `CANCELLED` |
| 04 | [`reconciliation/ReconciliationExample.kt`](src/main/kotlin/finance/idem/examples/reconciliation/ReconciliationExample.kt) | Posting via the SDK, then reconciling via `IdemClient.reconcileBatch` |
| 05 | [`mcp/McpAgentWorkflowExample.kt`](src/main/kotlin/finance/idem/examples/mcp/McpAgentWorkflowExample.kt) | A real MCP client (official `io.modelcontextprotocol.sdk:mcp`) driving `postTransaction` -> `reconcileBatch` -> `rollbackWorkflow` -> `getAgentAuditLog` over SSE — the same tools you can also drive via natural-language prompts in Claude Code/Desktop |

Every code example creates its own accounts on first run via
`support/ExampleAccounts.kt`, a small helper around `IdemClient.createAccount`
shared across examples 01, 02, 03, 04, and 05.

## Running a specific example

Each example is a standalone `main()` — there is no single app to launch.
Run any of them via `exec:java`:

```bash
./mvnw compile exec:java -Dexec.mainClass=finance.idem.examples.basic.BasicTransactionExampleKt
./mvnw compile exec:java -Dexec.mainClass=finance.idem.examples.onchain.StablecoinOnChainExampleKt
./mvnw compile exec:java -Dexec.mainClass=finance.idem.examples.settlement.PendingSettlementExampleKt
./mvnw compile exec:java -Dexec.mainClass=finance.idem.examples.reconciliation.ReconciliationExampleKt
./mvnw compile exec:java -Dexec.mainClass=finance.idem.examples.mcp.McpAgentWorkflowExampleKt
```

(Kotlin compiles a top-level `main()` in `Foo.kt` to a class named `FooKt`.)

## SDK dependency

```xml
<dependency>
    <groupId>finance.idem</groupId>
    <artifactId>idem-sdk-kotlin</artifactId>
    <version>0.0.12-test</version>
</dependency>
```

`idem-sdk-kotlin` hasn't had a stable release yet — `0.0.12-test` is the
latest pre-release build published to Maven Central while the `idem` release
pipeline is under active development
([idem-finance/idem#233](https://github.com/idem-finance/idem/issues/233)).
Update this version once a real `0.x`/`1.x` release ships.

Example 05 (`mcp/McpAgentWorkflowExample.kt`) also depends on the official
`io.modelcontextprotocol.sdk:mcp` client, pinned to the same version the main
repo's MCP server pulls in via `spring-ai-bom`.

## Links

- Main repo: [github.com/idem-finance/idem](https://github.com/idem-finance/idem)
- SDK source: [`sdk-kotlin/`](https://github.com/idem-finance/idem/tree/main/sdk-kotlin) in the main repo

## License

MIT — see [`LICENSE`](LICENSE). This is deliberately different from the main
`idem` repo, which is [FSL-1.1-Apache-2.0](https://fsl.software/FSL-1.1-Apache-2.0):
the ledger engine itself protects against competing managed services, but
example code you're meant to copy into your own project shouldn't carry that
restriction.
