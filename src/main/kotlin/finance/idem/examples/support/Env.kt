package finance.idem.examples.support

import io.github.cdimascio.dotenv.dotenv

/**
 * Loaded once per JVM. Host environment variables always take precedence
 * over `.env` entries (dotenv-kotlin's own resolution order), so a real
 * shell export still wins over a stale `.env` file.
 */
private val dotenv = dotenv { ignoreIfMissing = true }

/**
 * Resolves an environment variable from the host environment or `.env`
 * (see `.env.example`), failing fast with a message pointing at the file
 * every example relies on for local configuration.
 */
fun requiredEnv(key: String): String = dotenv[key] ?: error("$key is not set — see .env.example")
