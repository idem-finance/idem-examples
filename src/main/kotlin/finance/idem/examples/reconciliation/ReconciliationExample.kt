package finance.idem.examples.reconciliation

import finance.idem.examples.support.createAccount
import finance.idem.sdk.IdemClient
import finance.idem.sdk.model.ChainId
import finance.idem.sdk.model.EntryType
import finance.idem.sdk.model.JournalLineRequest
import finance.idem.sdk.model.OnChainEntryRequest
import finance.idem.sdk.model.PostTransactionRequest
import finance.idem.sdk.model.StablecoinToken
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.util.UUID

/**
 * Example 04 — reconciliation, called directly against the REST API.
 *
 * `IdemClient`'s entire public surface is postTransaction/getBalance/
 * listEntries/getStatement — there is no `reconcileEntries()` or
 * `rollbackWorkflow()` on the SDK today. Reconciliation IS real and reachable
 * at `POST /api/v1/reconciliation/batch` (requires RECONCILIATION_WRITE), so
 * this example posts through the SDK as usual and then reconciles with a
 * plain call through the SDK client's underlying HTTP client — the same
 * pattern used for account creation in `support/ExampleAccounts.kt`.
 *
 * Rollback has NO REST or SDK path at all — it's exposed exclusively as the
 * MCP tool `rollbackWorkflow` (AGENTS_ROLLBACK scope). See
 * `mcp/McpAgentWorkflowReadme.md` for how to trigger it from Claude Code.
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

            val results: List<Map<String, Any?>> =
                client.httpClient
                    .post("$baseUrl/api/v1/reconciliation/batch") {
                        header("X-API-Key", apiKey)
                        contentType(ContentType.Application.Json)
                        setBody(mapOf("transactionIds" to listOf(unmatchedTx.transactionId)))
                    }.body()

            results.forEach { item ->
                println("Reconciliation outcome for ${item["transactionId"]}: ${item["outcome"]}")
            }
            println(
                "An UNMATCHED result is a reconciliation exception, not an automatic rollback - " +
                    "resolving/rolling back the underlying workflow is only available via the MCP " +
                    "rollbackWorkflow tool today (no REST/SDK path). See mcp/McpAgentWorkflowReadme.md.",
            )
        }
    }
