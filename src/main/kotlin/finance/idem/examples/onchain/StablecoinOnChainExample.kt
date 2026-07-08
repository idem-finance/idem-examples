package finance.idem.examples.onchain

import finance.idem.examples.support.createAccount
import finance.idem.sdk.IdemClient
import finance.idem.sdk.model.ChainId
import finance.idem.sdk.model.EntryType
import finance.idem.sdk.model.JournalLineRequest
import finance.idem.sdk.model.OnChainEntryRequest
import finance.idem.sdk.model.OnChainEntryResponse
import finance.idem.sdk.model.PostTransactionRequest
import finance.idem.sdk.model.StablecoinToken
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.util.UUID

/**
 * Example 02 — a cross-border stablecoin transaction: debit a treasury
 * account, credit a customer account, both sides carrying full on-chain
 * settlement metadata (chain, token, tx hash).
 *
 * Note: the SDK's ChainId enum distinguishes networks at the EVM/SOLANA/TRON
 * level, not per-chain (Base vs. Ethereum vs. Polygon are all `ChainId.EVM`);
 * the specific network is inferred server-side from the token contract address.
 *
 * Every account still needs a nominal FiatCurrency at creation time (the
 * ledger's Account model requires one), but `getBalance()` only nets FiatEntry
 * lines — on-chain movement never affects it. To see the posted on-chain
 * entry you read the journal timeline instead (`listEntries`), which is what
 * this example does rather than calling getBalance and getting a misleading 0.
 *
 * A real cross-border flow usually has an on-chain entry settle *against* a
 * fiat leg (the "stablecoin sandwich") rather than two on-chain entries — this
 * example mixes both entry kinds in one transaction to demonstrate that the
 * ledger treats them uniformly, using an explicit idempotency key so a client
 * retry never double-posts.
 *
 * Run with:
 *   ./mvnw compile exec:java -Dexec.mainClass=finance.idem.examples.onchain.StablecoinOnChainExampleKt
 */
fun main() =
    runBlocking {
        val baseUrl = System.getenv("IDEM_BASE_URL") ?: error("IDEM_BASE_URL is not set — see .env.example")
        val apiKey = System.getenv("IDEM_API_KEY") ?: error("IDEM_API_KEY is not set — see .env.example")

        val client = IdemClient(baseUrl = baseUrl, apiKey = apiKey)
        client.use {
            val treasuryAccountId = client.createAccount(name = "Treasury USDC", currency = "USD", type = "ASSET")
            val customerAccountId = client.createAccount(name = "Customer Wallet", currency = "USD", type = "LIABILITY")
            val amount = BigDecimal("10000.00")
            val txHash = "0x" + "ab".repeat(32)

            val request =
                PostTransactionRequest(
                    lines =
                        listOf(
                            JournalLineRequest(
                                accountId = treasuryAccountId,
                                entryType = EntryType.DEBIT,
                                monetaryEntry =
                                    OnChainEntryRequest(
                                        amount = amount,
                                        token = StablecoinToken.USDC,
                                        chainId = ChainId.EVM,
                                        txHash = txHash,
                                        blockNumber = 21_000_000L,
                                        walletAddress = "0x1111111111111111111111111111111111111e",
                                        tokenContract = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
                                    ),
                                description = "Treasury USDC transfer out (Base)",
                            ),
                            JournalLineRequest(
                                accountId = customerAccountId,
                                entryType = EntryType.CREDIT,
                                monetaryEntry =
                                    OnChainEntryRequest(
                                        amount = amount,
                                        token = StablecoinToken.USDC,
                                        chainId = ChainId.EVM,
                                        txHash = txHash,
                                        blockNumber = 21_000_000L,
                                        walletAddress = "0x2222222222222222222222222222222222222f",
                                        tokenContract = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913",
                                    ),
                                description = "Customer USDC receipt (Base)",
                            ),
                        ),
                )

            // Explicit idempotency key: a client retry on network timeout replays
            // this exact key instead of risking a duplicate on-chain-backed entry.
            val idempotencyKey = UUID.randomUUID().toString()
            val response = client.postTransaction(request, idempotencyKey = idempotencyKey)
            println("Posted on-chain transaction: ${response.transactionId} (idempotencyKey=$idempotencyKey)")

            val entries = client.listEntries(customerAccountId.toString(), limit = 5)
            entries.entries.forEach { line ->
                val monetary = line.monetary
                if (monetary is OnChainEntryResponse) {
                    println(
                        "Customer entry: ${line.type} ${monetary.amount} ${monetary.token} " +
                            "on ${monetary.chainId} (tx=${monetary.txHash})",
                    )
                }
            }
        }
    }
