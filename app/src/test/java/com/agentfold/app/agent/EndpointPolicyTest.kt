package com.agentfold.app.agent

import org.junit.Assert.*
import org.junit.Test

class EndpointPolicyTest {
    @Test fun localEndpointsStayOnThisDevice() {
        for (url in listOf("http://localhost:11434/v1", "https://localhost/v1",
            "http://127.0.0.1:11434/v1", "http://[::1]:11434/v1")) {
            assertTrue(url, EndpointPolicy.isLoopbackEndpoint(url))
            EndpointPolicy.validate(url, "model")
        }
        for (url in listOf("https://example.com/v1", "http://192.168.1.2/v1",
            "https://localhost.example.com/v1", "https://localhost@example.com/v1",
            "http://localhost/v1?key=secret", "http://localhost/v1#secret",
            "file:///tmp/model", "not a URL")) {
            assertFalse(url, EndpointPolicy.isLoopbackEndpoint(url))
        }
    }
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
