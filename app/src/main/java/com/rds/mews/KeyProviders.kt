package com.rds.mews

interface ApiKeyProvider {
    fun getKey(): String
}

class GeminiApiKeyProvider: ApiKeyProvider {
    companion object {
        init { System.loadLibrary("mews-lib") }
    }

    private external fun getGeminiApiKey(): String

    override fun getKey(): String = getGeminiApiKey()
}

class RssHubApiKeyProvider: ApiKeyProvider {
    companion object {
        init { System.loadLibrary("mews-lib") }
    }

    private external fun getRssHubKey(): String

    override fun getKey(): String = getRssHubKey()
}

class ProxyAddressProvider: ApiKeyProvider {
    companion object {
        init { System.loadLibrary("mews-lib") }
    }

    private external fun getProxyAddress(): String

    override fun getKey(): String = getProxyAddress()
}

class RSSHubAddressProvider: ApiKeyProvider {
    companion object {
        init { System.loadLibrary("mews-lib") }
    }

    private external fun getHubAddress(): String

    override fun getKey(): String = getHubAddress()
}