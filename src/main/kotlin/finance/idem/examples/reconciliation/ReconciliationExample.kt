package finance.idem.examples.reconciliation

import finance.idem.examples.support.createAccount
import finance.idem.sdk.IdemClient
import finance.idem.sdk.model.ChainId
import finance.idem.sdk.model.EntryType
import finance.idem.sdk.model.JournalLineRequest
import finance.idem.sdk.model.OnChainEntryRequest
import finance.idem.sdk.model.PostTransactionRequest
import finance.idem.sdk.model.StablecoinToken
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.util.UUID

/**
 * Example 04 — reconciliation via the real SDK method.
 *
 * `IdemClient.reconcileBatch` wraps `POST /api/v1/reconciliation/batch`
 * (requires RECONCILIATION_WRITE) directly — no raw HTTP workaround needed.
 *
 * Rollback has NO REST or SDK path at all — it's exposed exclusively as the
 * MCP tool `rollbackWorkflow` (AGENTS_ROLLBACK scope). See
 * `mcp/McpAgentWorkflowExample.kt` for how to trigger it as a real MCP client.
 *
 * Run with:
 *   ./mvnw compile exec:java -Dexec.mainClass=finance.idem.examples.reconciliation.ReconciliationExampleKt
 */
fun main() =
    runBlocking {
        val baseUrl = System.getenv("IDEM_BASE_URL") ?: error("IDEM_BASE_URL is not set — see .env.example")
        val apiKey = System.getenv("IDEM_API_KEY") ?: error("IDEM_API_KEY is not set — see .env.example")

        val client = IdemClient(baseUrl = baseUrl, apiKey = apiKey)
        client.use {
            val treasuryAccountId = client.createAccount(name = "Reconciliation Treasury", currency = "USD", type = "ASSET")
            val customerAccountId = client.createAccount(name = "Reconciliation Customer", currency = "USD", type = "LIABILITY")

            // A transaction carrying on-chain entries with no matching PENDING
            // settlement registered ahead of time — reconciliation has nothing to
            // match it against, so it comes back UNMATCHED, the "exception" case.
            val unmatchedTx =
                client.postTransaction(
                    PostTransactionRequest(
                        lines =
                            listOf(
                                JournalLineRequest(
                                    accountId = treasuryAccountId,
                                    entryType = EntryType.DEBIT,
                                    monetaryEntry =
                                        OnChainEntryRequest(
                                            amount = BigDecimal("2500.00"),
                                            token = StablecoinToken.USDC,
                                            chainId = ChainId.EVM,
                                            txHash = "0x" + "cd".repeat(32),
                                            blockNumber = 21_000_100L,
                                            walletAddress = "0x3333333333333333333333333333333333333a",
                                            tokenContract = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
                                        ),
                                ),
                                JournalLineRequest(
                                    accountId = customerAccountId,
                                    entryType = EntryType.CREDIT,
                                    monetaryEntry =
                                        OnChainEntryRequest(
                                            amount = BigDecimal("2500.00"),
                                            token = StablecoinToken.USDC,
                                            chainId = ChainId.EVM,
                                            txHash = "0x" + "cd".repeat(32),
                                            blockNumber = 21_000_100L,
                                            walletAddress = "0x4444444444444444444444444444444444444b",
                                            tokenContract = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
                                        ),
                                ),
                            ),
                    ),
                    idempotencyKey = UUID.randomUUID().toString(),
                )
            println("Posted on-chain transaction with no PENDING match: ${unmatchedTx.transactionId}")

            val results = client.reconcileBatch(listOf(unmatchedTx.transactionId))

            results.forEach { item ->
                println("Reconciliation outcome for ${item.transactionId}: ${item.outcome}")
            }
            println(
                "An UNMATCHED result is a reconciliation exception, not an automatic rollback - " +
                    "resolving/rolling back the underlying workflow is only available via the MCP " +
                    "rollbackWorkflow tool today (no REST/SDK path). See mcp/McpAgentWorkflowExample.kt.",
            )
        }
    }
