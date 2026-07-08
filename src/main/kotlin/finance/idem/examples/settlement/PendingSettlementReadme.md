# Example 03 — Pending settlement (conceptual, not runnable)

This example is documentation only — there's no `PendingSettlementExample.kt`
in this directory. A search of the whole `idem` codebase (core, application,
infrastructure, `idem-sdk-kotlin`) confirms there is no `PendingSettlement`
class and no `WATCHING` state anywhere; it's not implemented, and neither
`idem-sdk-kotlin` nor the REST API expose a settlement lifecycle a client can
observe directly. This doc describes how settlement actually works today —
entirely server-side — so it's clear what's real versus aspirational.

## How settlement actually works

Idem's ledger has an internal `EntryStatus` on settlements:
`PENDING → SETTLED` or `PENDING → UNMATCHED`. There is no `WATCHING` state.

The flow, end to end:

1. A fiat leg of a cross-border transaction is posted (e.g. via
   [`BasicTransactionExample`](../basic/BasicTransactionExample.kt)), and
   separately an expected on-chain settlement is registered server-side as a
   `Settlement` record with status `PENDING`.
2. Idem watches the relevant chain for the matching on-chain transfer through
   one of three mechanisms, depending on the network:
   - **EVM** (Ethereum, Base, Polygon): `AlchemyWebhookReceiver` — Alchemy
     Notify pushes matching ERC-20 `Transfer` events (primary), with
     `EvmChainReader` (Web3j `getLogs`) as a startup-recovery fallback.
   - **Solana**: `QuickNodeWebhookService` — QuickNode Streams pushes matching
     transfers (primary), with `SolanaChainReader` (raw JSON-RPC) as a
     startup-recovery fallback.
   - **Tron**: `TronChainReader` polls the Tronscan REST API on a schedule
     (primary and only mechanism — Tron has no webhook support).
3. When a matching on-chain transfer arrives, `BasicReconciliationService`
   attempts to match it against `PENDING` settlement candidates (by amount,
   with sender-address confirmation preferred over FIFO — see
   `BasicReconciliationService.findMatch` in the main repo). A match flips the
   settlement to `SETTLED` and fires a `transaction.settled` webhook event via
   the transactional outbox. No match flips it to `UNMATCHED` instead — see
   [`ReconciliationExample`](../reconciliation/ReconciliationExample.kt) for
   how that exception case looks from the client side.

## What you can observe today

- `idem-sdk-kotlin` doesn't expose `EntryStatus` or a settlement resource at
  all — `BalanceResponse`, `JournalLineResponse`, and `StatementResponse`
  carry no settlement-state field.
- The closest thing to visibility today is the `transaction.settled` /
  reconciliation-related webhook events delivered via the tenant's configured
  webhook endpoint (`webhook_outbox` → `WebhookOutboxPoller`), or manually
  triggering `POST /api/v1/reconciliation/batch` for a known transaction ID
  (see example 04).
- If you need to watch a specific transaction settle in real time today,
  configure a webhook receiver rather than polling — there is no client-side
  polling primitive for this in the SDK or REST API.

If a future `idem-sdk-kotlin` release adds a settlement-status field or
polling endpoint, this doc should be replaced with a real, runnable example.
