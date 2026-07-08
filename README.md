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

## Examples

| # | Example | What it shows |
|---|---|---|
| 01 | [`basic/BasicTransactionExample.kt`](src/main/kotlin/finance/idem/examples/basic/BasicTransactionExample.kt) | A simple fiat double-entry transaction — debit/credit, `postTransaction`, `getBalance` |
| 02 | [`onchain/StablecoinOnChainExample.kt`](src/main/kotlin/finance/idem/examples/onchain/StablecoinOnChainExample.kt) | A cross-border stablecoin transaction mixing on-chain entries in one transaction, with an explicit idempotency key |
| 03 | [`settlement/PendingSettlementReadme.md`](src/main/kotlin/finance/idem/examples/settlement/PendingSettlementReadme.md) | Conceptual doc — how server-side settlement actually works today (chain-reader/webhook-driven); not yet exposed via the SDK, so there's no runnable code here |
| 04 | [`reconciliation/ReconciliationExample.kt`](src/main/kotlin/finance/idem/examples/reconciliation/ReconciliationExample.kt) | Posting via the SDK, then reconciling via a direct REST call (no SDK method for this yet) |
| 05 | [`mcp/McpAgentWorkflowReadme.md`](src/main/kotlin/finance/idem/examples/mcp/McpAgentWorkflowReadme.md) | Connecting Claude Code to Idem's MCP server and driving the ledger with natural-language prompts instead of code |

Every code example creates its own accounts on first run — `idem-sdk-kotlin`
doesn't expose account creation, so each one bootstraps what it needs via
`support/ExampleAccounts.kt`, a small helper shared across examples 01, 02,
and 04.

## Running a specific example

Each example is a standalone `main()` — there is no single app to launch.
Run any of them via `exec:java`:

```bash
./mvnw compile exec:java -Dexec.mainClass=finance.idem.examples.basic.BasicTransactionExampleKt
./mvnw compile exec:java -Dexec.mainClass=finance.idem.examples.onchain.StablecoinOnChainExampleKt
./mvnw compile exec:java -Dexec.mainClass=finance.idem.examples.reconciliation.ReconciliationExampleKt
```

(Kotlin compiles a top-level `main()` in `Foo.kt` to a class named `FooKt`.)

## SDK dependency

```xml
<dependency>
    <groupId>finance.idem</groupId>
    <artifactId>idem-sdk-kotlin</artifactId>
    <version>0.0.11-test</version>
</dependency>
```

`idem-sdk-kotlin` hasn't had a stable release yet — `0.0.11-test` is the
latest pre-release build published to Maven Central while the `idem` release
pipeline is under active development
([idem-finance/idem#233](https://github.com/idem-finance/idem/issues/233)).
Update this version once a real `0.x`/`1.x` release ships.

## Links

- Main repo: [github.com/idem-finance/idem](https://github.com/idem-finance/idem)
- SDK source: [`sdk-kotlin/`](https://github.com/idem-finance/idem/tree/main/sdk-kotlin) in the main repo

## License

MIT — see [`LICENSE`](LICENSE). This is deliberately different from the main
`idem` repo, which is [FSL-1.1-Apache-2.0](https://fsl.software/FSL-1.1-Apache-2.0):
the ledger engine itself protects against competing managed services, but
example code you're meant to copy into your own project shouldn't carry that
restriction.
