package finance.idem.examples

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Entry point placeholder. Each example under this package is a standalone
 * `main()` function meant to be run individually — see the README's
 * "Running a specific example" section (`exec:java -Dexec.mainClass=...`).
 * Running this class just prints the same instructions.
 */
@SpringBootApplication
class IdemExamplesApplication

fun main(args: Array<String>) {
    println(
        """
        idem-examples doesn't run as a single app - each example is a standalone
        entry point. Run one directly, e.g.:

          ./mvnw compile exec:java -Dexec.mainClass=finance.idem.examples.basic.BasicTransactionExampleKt

        See README.md for the full list of examples.
        """.trimIndent(),
    )
    runApplication<IdemExamplesApplication>(*args)
}
