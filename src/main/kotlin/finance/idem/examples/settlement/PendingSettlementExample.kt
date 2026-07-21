package finance.idem.examples.settlement

import finance.idem.examples.support.createAccount
import finance.idem.examples.support.requiredEnv
import finance.idem.sdk.IdemClient
import finance.idem.sdk.model.ChainId
import finance.idem.sdk.model.EntryType
import finance.idem.sdk.model.JournalLineRequest
import finance.idem.sdk.model.OnChainEntryRequest
import finance.idem.sdk.model.PostTransactionRequest
import finance.idem.sdk.model.RegisterSettlementRequest
import finance.idem.sdk.model.StablecoinToken
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.util.UUID

/**
 * Example 03 — pending settlement lifecycle: register an expectation, watch
 * it settle, and cancel one that never arrives.
 *
 * A `Settlement` is a `PENDING` expectation registered ahead of time
 * (`registerSettlement`) that Idem watches for a matching on-chain transfer.
 * In production that match happens automatically via the chain-reader/webhook
 * pipeline (see the main repo's `docs/domain-model.md`); locally, with no live
 * chain, `reconcileBatch` triggers the same matching logic on demand once the
 * matching transaction has been posted.
 *
 * Run with:
 *   ./mvnw compile exec:java -Dexec.mainClass=finance.idem.examples.settlement.PendingSettlementExampleKt
 */
fun main() =
    runBlocking {
        val baseUrl = requiredEnv("IDEM_BASE_URL")
        val apiKey = requiredEnv("IDEM_API_KEY")

        val client = IdemClient(baseUrl = baseUrl, apiKey = apiKey)
        client.use {
            val treasuryAccountId = client.createAccount(name = "Settlement Treasury", currency = "USD", type = "ASSET")
            val walletAddress = "0x5555555555555555555555555555555555555c"
            val amount = BigDecimal("2500.00")

            // --- PENDING -> SETTLED ---

            val settlement =
                client.registerSettlement(
                    RegisterSettlementRequest(
                        accountId = treasuryAccountId,
                        expectedToken = StablecoinToken.USDC,
                        expectedAmount = amount,
                        expectedWalletAddress = walletAddress,
                        expectedChainId = ChainId.EVM,
                    ),
                    idempotencyKey = UUID.randomUUID().toString(),
                )
            println("Registered settlement ${settlement.settlementId}: status=${settlement.status}, expiresAt=${settlement.expiresAt}")

            // Post the matching on-chain transaction — same amount/token/chain/wallet
            // as the registered expectation, so reconciliation can match it.
            val counterpartyAccountId = client.createAccount(name = "Settlement Counterparty", currency = "USD", type = "LIABILITY")
            val txHash = "0x" + "ef".repeat(32)
            // BasicReconciliationService matches settlements using the FIRST journal
            // line's OnChainEntry, so the CREDIT line into the treasury account (the
            // side carrying the registered wallet address) must come first.
            val postedTx =
                client.postTransaction(
                    PostTransactionRequest(
                        lines =
                            listOf(
                                JournalLineRequest(
                                    accountId = treasuryAccountId,
                                    entryType = EntryType.CREDIT,
                                    monetaryEntry =
                                        OnChainEntryRequest(
                                            amount = amount,
                                            token = StablecoinToken.USDC,
                                            chainId = ChainId.EVM,
                                            txHash = txHash,
                                            blockNumber = 21_000_200L,
                                            walletAddress = walletAddress,
                                            tokenContract = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
                                        ),
                                ),
                                JournalLineRequest(
                                    accountId = counterpartyAccountId,
                                    entryType = EntryType.DEBIT,
                                    monetaryEntry =
                                        OnChainEntryRequest(
                                            amount = amount,
                                            token = StablecoinToken.USDC,
                                            chainId = ChainId.EVM,
                                            txHash = txHash,
                                            blockNumber = 21_000_200L,
                                            walletAddress = "0x6666666666666666666666666666666666666d",
                                            tokenContract = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
                                        ),
                                ),
                            ),
                    ),
                    idempotencyKey = UUID.randomUUID().toString(),
                )
            println("Posted matching on-chain transaction: ${postedTx.transactionId}")

            // No live chain locally, so force the match rather than waiting on a
            // webhook/chain-reader sweep that will never fire in this environment.
            client.reconcileBatch(listOf(postedTx.transactionId))

            val settledView = client.getSettlement(settlement.settlementId)
            println(
                "Settlement ${settledView.settlementId} is now ${settledView.status} " +
                    "(matchedTransactionId=${settledView.matchedTransactionId}, txHash=${settledView.txHash}, confirmedAt=${settledView.confirmedAt})",
            )

            // --- PENDING -> CANCELLED ---
            // A second expectation is registered and cancelled before anything ever
            // arrives to match it — the other terminal path besides SETTLED. Reaching
            // a genuine UNMATCHED terminal status instead would require waiting out
            // the real settlement matching-window expiry (24h by default), which
            // isn't practical for a short-lived example run.
            val abandoned =
                client.registerSettlement(
                    RegisterSettlementRequest(
                        accountId = treasuryAccountId,
                        expectedToken = StablecoinToken.USDC,
                        expectedAmount = BigDecimal("100.00"),
                        expectedWalletAddress = "0x7777777777777777777777777777777777777e",
                        expectedChainId = ChainId.EVM,
                    ),
                    idempotencyKey = UUID.randomUUID().toString(),
                )
            println("Registered a second settlement ${abandoned.settlementId}: status=${abandoned.status}")

            val cancelled = client.cancelSettlement(abandoned.settlementId)
            println("Cancelled settlement ${cancelled.settlementId}: status=${cancelled.status}")
        }
    }