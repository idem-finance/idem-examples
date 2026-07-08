package finance.idem.examples.basic

import finance.idem.examples.support.createAccount
import finance.idem.sdk.IdemClient
import finance.idem.sdk.model.EntryType
import finance.idem.sdk.model.FiatCurrency
import finance.idem.sdk.model.FiatEntryRequest
import finance.idem.sdk.model.JournalLineRequest
import finance.idem.sdk.model.PaymentRail
import finance.idem.sdk.model.PostTransactionRequest
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.util.UUID

/**
 * Example 01 — a simple fiat double-entry transaction: debit an operating
 * account, credit a customer account, then read the resulting balance.
 *
 * Prerequisites: `docker compose up -d` + the dev-seed step in the README,
 * with IDEM_BASE_URL / IDEM_API_KEY set (see .env.example).
 *
 * Run with:
 *   ./mvnw compile exec:java -Dexec.mainClass=finance.idem.examples.basic.BasicTransactionExampleKt
 */
fun main() =
    runBlocking {
        val baseUrl = System.getenv("IDEM_BASE_URL") ?: error("IDEM_BASE_URL is not set — see .env.example")
        val apiKey = System.getenv("IDEM_API_KEY") ?: error("IDEM_API_KEY is not set — see .env.example")

        val client = IdemClient(baseUrl = baseUrl, apiKey = apiKey)
        client.use {
            // Accounts aren't implicitly opened on first use — the ledger requires
            // them to exist before a transaction can reference them. The SDK
            // doesn't expose account creation, so we bootstrap two here directly.
            val operatingAccountId = client.createAccount(name = "Operating Cash", currency = "USD", type = "ASSET")
            val customerAccountId = client.createAccount(name = "Customer Payable", currency = "USD", type = "LIABILITY")
            val amount = BigDecimal("500.00")

            val request =
                PostTransactionRequest(
                    lines =
                        listOf(
                            JournalLineRequest(
                                accountId = operatingAccountId,
                                entryType = EntryType.DEBIT,
                                monetaryEntry =
                                    FiatEntryRequest(
                                        amount = amount,
                                        currency = FiatCurrency.USD,
                                        rail = PaymentRail.ACH,
                                    ),
                                description = "Operating account payout",
                            ),
                            JournalLineRequest(
                                accountId = customerAccountId,
                                entryType = EntryType.CREDIT,
                                monetaryEntry =
                                    FiatEntryRequest(
                                        amount = amount,
                                        currency = FiatCurrency.USD,
                                        rail = PaymentRail.ACH,
                                    ),
                                description = "Customer account credit",
                            ),
                        ),
                )

            val response = client.postTransaction(request, idempotencyKey = UUID.randomUUID().toString())
            println("Posted transaction: ${response.transactionId}")

            val balance = client.getBalance(customerAccountId.toString())
            println(
                "Customer account balance: ${balance.amount} ${balance.currency} " +
                    "(normal balance: ${balance.normalBalance})",
            )
        }
    }
