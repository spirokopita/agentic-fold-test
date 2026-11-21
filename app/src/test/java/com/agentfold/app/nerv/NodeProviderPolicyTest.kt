package com.agentfold.app.nerv

import com.agentfold.app.agent.ProviderConfig
import org.junit.Assert.*
import org.junit.Test

class NodeProviderPolicyTest {
    private val shared = ProviderConfig("https://shared.example/v1", "shared-model", "shared-key")

    @Test fun nodeKeyWorksWithoutSharedConfiguration() {
        val config = NodeProviderPolicy.resolveCompatible(
            "https://node.example/v1", "node-model", "node-key", null)
        assertEquals(ProviderConfig("https://node.example/v1", "node-model", "node-key"), config)
    }

    @Test fun nodeSettingsOverrideSharedDefaults() {
        val config = NodeProviderPolicy.resolveCompatible(
            "https://node.example/v1", "node-model", "node-key", shared)
        assertEquals(ProviderConfig("https://node.example/v1", "node-model", "node-key"), config)
    }

    @Test fun sharedSettingsRemainAvailableAsFallback() {
        assertEquals(shared, NodeProviderPolicy.resolveCompatible(
            "http://localhost:11434/v1", "", null, shared))
    }

    @Test fun incompleteConfigurationIsUnavailable() {
        for ((endpoint, model, key) in listOf(
            Triple("https://node.example/v1", "model", null),
            Triple("http://localhost:11434/v1", "model", "node-key"),
            Triple("https://node.example/v1", "", "node-key"),
            Triple("https://key@node.example/v1", "model", "node-key"))) {
            try {
                NodeProviderPolicy.resolveCompatible(endpoint, model, key, null)
                fail("Accepted incomplete or invalid configuration")
            } catch (_: IllegalArgumentException) {
            } catch (_: IllegalStateException) {
            }
        }
    }
}
