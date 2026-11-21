package com.agentfold.app.nerv

import com.agentfold.app.agent.EndpointPolicy
import com.agentfold.app.agent.ProviderConfig

internal object NodeProviderPolicy {
    fun resolveCompatible(
        endpoint: String,
        model: String,
        nodeKey: String?,
        shared: ProviderConfig?
    ): ProviderConfig {
        val apiKey = nodeKey?.takeIf { it.isNotBlank() }
            ?: shared?.apiKey?.takeIf { it.isNotBlank() }
            ?: error("No API key configured. Add a node key or shared provider key in SETUP.")
        val resolvedEndpoint = if (endpoint.startsWith("https://")) endpoint
            else shared?.endpoint ?: error("No HTTPS provider endpoint configured. Set it in SETUP.")
        val resolvedModel = model.ifBlank { shared?.model.orEmpty() }
        EndpointPolicy.validate(resolvedEndpoint, resolvedModel)
        return ProviderConfig(resolvedEndpoint, resolvedModel, apiKey)
    }
}
