package finance.idem.examples.support

import finance.idem.sdk.IdemClient
import finance.idem.sdk.model.AccountType
import finance.idem.sdk.model.CreateAccountRequest
import finance.idem.sdk.model.FiatCurrency
import java.util.UUID

/**
 * Accounts aren't implicitly opened on first use — the ledger requires them
 * to exist before a transaction can reference them. This wraps the real
 * `IdemClient.createAccount` with String params and a bare UUID return, since
 * that's what every example's call site expects.
 *
 * Requires the ACCOUNTS_WRITE scope — the dev-seeded key from the README has
 * every scope, so this works out of the box against a local stack.
 */
suspend fun IdemClient.createAccount(
    name: String,
    currency: String,
    type: String,
): UUID {
    val response =
        createAccount(
            CreateAccountRequest(
                name = name,
                currency = FiatCurrency.valueOf(currency.uppercase()),
                type = AccountType.valueOf(type.uppercase()),
            ),
        )
    return response.id
}