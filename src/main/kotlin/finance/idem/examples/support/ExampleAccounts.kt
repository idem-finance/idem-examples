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
import java.util.UUID

/**
 * idem-sdk-kotlin does not expose account creation (POST /api/v1/accounts) —
 * its public surface is limited to posting transactions and read queries
 * (postTransaction/getBalance/listEntries/getStatement). Accounts must exist
 * before a transaction can reference them (PostTransactionService rejects
 * unknown account IDs), so every example bootstraps its own accounts via a
 * direct call through the SDK client's already-configured Ktor HttpClient,
 * reusing its content negotiation and X-API-Key header pattern.
 *
 * Requires the ACCOUNTS_WRITE scope — the dev-seeded key from the README has
 * every scope, so this works out of the box against a local stack.
 */
suspend fun IdemClient.createAccount(
    name: String,
    currency: String,
    type: String,
): UUID {
    val response: HttpResponse =
        httpClient.post("$baseUrl/api/v1/accounts") {
            header("X-API-Key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(mapOf("name" to name, "currency" to currency, "type" to type))
        }
    check(response.status.isSuccess()) { "Failed to create account '$name': HTTP ${response.status}" }
    // Deserialize as a loose map rather than a full response DTO — we only need
    // the generated id, and the SDK deliberately has no CreateAccountResponse model.
    val body: Map<String, Any?> = response.body()
    return UUID.fromString(body["id"] as String)
}
