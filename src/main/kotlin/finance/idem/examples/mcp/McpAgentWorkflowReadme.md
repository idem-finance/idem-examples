# Example 05 — MCP agent workflow (conceptual, not runnable Kotlin)

This is a setup guide + prompt reference, not code — the point is to drive
Idem through Claude Code (or any MCP client) directly, not through
`idem-sdk-kotlin`. It documents the MCP server exposed by the main `idem`
repo's `mcp` module (`IdemMcpServer.kt`).

## The 7 tools, as actually registered

Spring AI's `@Tool` registers MCP tools under their **Kotlin method name**,
which is camelCase — not the snake_case (`post_transaction`, etc.) used in
some of the main repo's older docs. Use the names below; they're read directly
from `mcp/src/main/kotlin/finance/idem/mcp/IdemMcpServer.kt`.

| Tool (real, registered name) | Required scope | Key parameters |
|---|---|---|
| `postTransaction` | `AGENTS_EXECUTE` | `entries` (journal lines), `idempotencyKey`, `intentDescription`, `agentId`, `sessionId` |
| `getBalance` | `AGENTS_EXECUTE` | `accountId`, `asOf?` |
| `listEntries` | `AGENTS_EXECUTE` | `accountId`, `from?`, `to?`, `limit?`, `cursor?` |
| `describeAccount` | `AGENTS_EXECUTE` | `accountId` |
| `rollbackWorkflow` | `AGENTS_ROLLBACK` | `workflowPlanId`, `reason`, `agentId`, `sessionId` |
| `reconcileBatch` | `AGENTS_EXECUTE` | `accountId?`, `from`, `to`, `tolerancePercent?` |
| `getAgentAuditLog` | `AGENTS_AUDIT_READ` | `sessionId?`, `from?`, `to?`, `limit?` |

`AGENTS_ROLLBACK` is intentionally a separate scope from `AGENTS_EXECUTE` — an
agent key that can post transactions cannot roll them back unless explicitly
granted this too.

Every `postTransaction` call is evaluated by `PolicyGuard` **before** it
commits — by default, a tenant with no configured policy rules gets a
deny-all rule (`MaxDebitPerSession(ZERO)`), so agent debits will be rejected
until you configure a permissive rule for your dev tenant.

## Connecting Claude Code

The server speaks the SSE transport (`GET /sse` + `POST /mcp/messages`,
Spring AI's `WebMvcSseServerTransportProvider`) — use `--transport sse`, not
`http`. If you're running the stack locally per this repo's README, expose it
first (e.g. `ngrok http 8081`), then:

```bash
claude mcp add --transport sse idem https://<your-ngrok-id>.ngrok.io/sse \
  --header "X-API-Key: <your sk_agent_... key>"
```

Verify with `claude mcp list` or `/mcp` inside a session — confirm `idem`
shows connected and all 7 tools are visible.

Use an agent-scoped key (`sk_agent_...`) with only the scopes you need for the
demo, e.g. `AGENTS_EXECUTE` + `AGENTS_ROLLBACK` + `AGENTS_AUDIT_READ` — not the
ADMIN dev-seed key from the rest of this repo's examples.

## Example prompts

Once connected, natural-language prompts to Claude Code exercise the tools
directly — no code required. A demo flow mirroring the one covered by the
main repo's `McpServerIntegrationTest` "demo scenario" test:

1. *"Post a transaction debiting account `<fiat-account-id>` and crediting
   `<usdc-account-id>` for 300 USD over WIRE, with idempotency key
   `demo-exec-001`."* → calls `postTransaction`, returns a `workflowPlanId`.
2. *"Reconcile account `<usdc-account-id>` for the last 24 hours."* → calls
   `reconcileBatch`, returns `matched`/`unmatched`/`exceptions`.
3. *"Roll back workflow `<workflowPlanId from step 1>` — reason: compliance
   review."* → calls `rollbackWorkflow`, returns the compensating
   transaction(s) and a `ROLLED_BACK` status.
4. *"Show me the agent audit log for this session."* → calls
   `getAgentAuditLog`, returns HMAC-signed events — note the `PENDING`
   event written *before* execution and the `COMPLETED`/`FAILED` event
   written after, per Idem's audit-before-execution rule.

## Further reading

- `docs/mcp-server.md` in the main `idem` repo for the full connection guide.
- `mcp/src/test/kotlin/finance/idem/mcp/McpServerIntegrationTest.kt` for the
  canonical end-to-end scenario this doc mirrors.
