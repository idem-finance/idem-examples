package finance.idem.examples.support

import finance.idem.sdk.IdemClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * API-key and policy-rule creation are one-time ADMIN-scope tenant setup
 * actions, not ledger data-plane operations — `idem-sdk-kotlin` deliberately
 * doesn't expose them, so these go through the SDK client's underlying HTTP
 * client directly, the same pattern used for account creation before
 * `createAccount` was added to the SDK.
 */
data class MintedApiKey(
    val rawKey: String,
    val prefix: String,
)

suspend fun IdemClient.mintAgentApiKey(scopes: List<String>): MintedApiKey {
    val response: HttpResponse =
        httpClient.post("$baseUrl/api/v1/api-keys") {
            header("X-API-Key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(mapOf("scopes" to scopes))
        }
    check(response.status.isSuccess()) { "Failed to mint agent API key: HTTP ${response.status}" }
    val body: Map<String, Any?> = response.body()
    return MintedApiKey(rawKey = body["rawKey"] as String, prefix = body["prefix"] as String)
}

suspend fun IdemClient.allowAgentMaxDebitPerSession(
    agentKeyPrefix: String,
    amount: String,
) {
    val response: HttpResponse =
        httpClient.post("$baseUrl/api/v1/admin/policy-rules") {
            header("X-API-Key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "type" to "MAX_DEBIT_PER_SESSION",
                    "agentKeyPrefix" to agentKeyPrefix,
                    "amount" to amount,
                ),
            )
        }
    check(response.status.isSuccess()) { "Failed to create policy rule: HTTP ${response.status}" }
}