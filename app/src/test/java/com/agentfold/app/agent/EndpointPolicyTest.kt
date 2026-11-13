package com.agentfold.app.agent

import org.junit.Assert.*
import org.junit.Test

class EndpointPolicyTest {
    @Test fun acceptsOwnerConfiguredHttpsProvider() {
        EndpointPolicy.validate("https://example.com/v1", "chosen-model")
    }
    @Test fun rejectsCredentialsQueriesFragmentsAndInsecureTransport() {
        for (url in listOf("http://example.com/v1", "https://key@example.com/v1",
            "https://example.com/v1?key=secret", "https://example.com/v1#secret", "file:///tmp/key")) {
            try { EndpointPolicy.validate(url, "model"); fail("Accepted $url") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
