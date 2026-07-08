package finance.idem.examples.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import finance.idem.examples.support.allowAgentMaxDebitPerSession
import finance.idem.examples.support.createAccount
import finance.idem.examples.support.mintAgentApiKey
import finance.idem.sdk.IdemClient
import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport
import io.modelcontextprotocol.spec.McpSchema
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Example 05 — a real MCP client driving Idem's agent tools, mirroring the
 * "demo scenario" covered by the main repo's `McpServerIntegrationTest`:
 * post -> reconcile -> rollback -> audit log.
 *
 * Unlike every other example here, this one does NOT go through
 * `idem-sdk-kotlin` for the ledger operations — the MCP server
 * (`IdemMcpServer` in the main repo's `mcp` module) is a separate protocol
 * surface (SSE/JSON-RPC), reached with the official MCP Java SDK
 * (`io.modelcontextprotocol.sdk:mcp`) instead of an HTTP client. `IdemClient`
 * is only used here for one-time setup: bootstrapping accounts and minting
 * the agent-scoped API key.
 *
 * Every `postTransaction` call is evaluated by `PolicyGuard` before it
 * commits, and the default policy is deny-all — so this example configures a
 * permissive `MAX_DEBIT_PER_SESSION` rule for the minted agent key first.
 *
 * You can drive the exact same 4 tools through natural-language prompts in
 * Claude Code/Desktop instead of this Kotlin client — see the connection
 * instructions in the main repo's `docs/mcp-server.md`.
 *
 * Run with:
 *   ./mvnw compile exec:java -Dexec.mainClass=finance.idem.examples.mcp.McpAgentWorkflowExampleKt
 */
fun main() =
    runBlocking {
        val baseUrl = System.getenv("IDEM_BASE_URL") ?: error("IDEM_BASE_URL is not set — see .env.example")
        val apiKey = System.getenv("IDEM_API_KEY") ?: error("IDEM_API_KEY is not set — see .env.example")
        val objectMapper = ObjectMapper()

        val client = IdemClient(baseUrl = baseUrl, apiKey = apiKey)
        val (fiatAccountId, usdcAccountId, agentApiKey) =
            client.use {
                val fiatAccountId = client.createAccount(name = "MCP Agent Fiat", currency = "USD", type = "ASSET")
                val usdcAccountId = client.createAccount(name = "MCP Agent USDC", currency = "USD", type = "ASSET")

                val agentApiKey = client.mintAgentApiKey(listOf("AGENTS_EXECUTE", "AGENTS_ROLLBACK", "AGENTS_AUDIT_READ"))
                println("Minted agent API key with prefix ${agentApiKey.prefix}")

                client.allowAgentMaxDebitPerSession(agentApiKey.prefix, amount = "100000.00")
                println("Configured a permissive MAX_DEBIT_PER_SESSION policy rule for ${agentApiKey.prefix}")

                Triple(fiatAccountId, usdcAccountId, agentApiKey)
            }

        val sessionId = UUID.randomUUID().toString()
        val agentId = "idem-examples-mcp-demo"

        val transport =
            HttpClientSseClientTransport
                .builder(baseUrl)
                .sseEndpoint("/sse")
                .customizeRequest { it.header("X-API-Key", agentApiKey.rawKey) }
                .build()

        val mcpClient = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build()
        try {
            mcpClient.initialize()
            println("Connected to Idem MCP server — ${mcpClient.listTools().tools().size} tools available")

            val postResult =
                mcpClient.callTool(
                    McpSchema.CallToolRequest(
                        "postTransaction",
                        mapOf(
                            "entries" to
                                listOf(
                                    mapOf(
                                        "accountId" to fiatAccountId.toString(),
                                        "entryType" to "DEBIT",
                                        "monetaryEntryType" to "FIAT",
                                        "amount" to "300.00",
                                        "currency" to "USD",
                                        "rail" to "WIRE",
                                    ),
                                    mapOf(
                                        "accountId" to usdcAccountId.toString(),
                                        "entryType" to "CREDIT",
                                        "monetaryEntryType" to "FIAT",
                                        "amount" to "300.00",
                                        "currency" to "USD",
                                        "rail" to "WIRE",
                                    ),
                                ),
                            "idempotencyKey" to UUID.randomUUID().toString(),
                            "intentDescription" to "idem-examples MCP agent workflow demo",
                            "agentId" to agentId,
                            "sessionId" to sessionId,
                        ),
                    ),
                )
            val workflowPlanId = printToolResult("postTransaction", postResult, objectMapper).get("workflowPlanId").asText()

            val reconcileResult =
                mcpClient.callTool(
                    McpSchema.CallToolRequest(
                        "reconcileBatch",
                        mapOf(
                            "accountId" to usdcAccountId.toString(),
                            "from" to Instant.now().minus(1, ChronoUnit.DAYS).toString(),
                            "to" to Instant.now().toString(),
                        ),
                    ),
                )
            printToolResult("reconcileBatch", reconcileResult, objectMapper)

            val rollbackResult =
                mcpClient.callTool(
                    McpSchema.CallToolRequest(
                        "rollbackWorkflow",
                        mapOf(
                            "workflowPlanId" to workflowPlanId,
                            "reason" to "idem-examples MCP agent workflow demo — compensating the demo transaction",
                            "agentId" to agentId,
                            "sessionId" to sessionId,
                        ),
                    ),
                )
            printToolResult("rollbackWorkflow", rollbackResult, objectMapper)

            val auditResult =
                mcpClient.callTool(
                    McpSchema.CallToolRequest(
                        "getAgentAuditLog",
                        mapOf("sessionId" to sessionId, "limit" to 10),
                    ),
                )
            val auditJson = printToolResult("getAgentAuditLog", auditResult, objectMapper)
            println("Audit trail has ${auditJson.get("total").asInt()} event(s)")
        } finally {
            mcpClient.closeGracefully()
        }
    }

private fun printToolResult(
    toolName: String,
    result: McpSchema.CallToolResult,
    objectMapper: ObjectMapper,
): com.fasterxml.jackson.databind.JsonNode {
    val text = (result.content().first() as McpSchema.TextContent).text()
    check(result.isError != true) { "$toolName returned an error: $text" }
    println("$toolName -> $text")
    return objectMapper.readTree(text)
}