package com.rds.mews

interface ApiKeyProvider {
    fun getKey(): String
}

class GeminiApiKeyProvider: ApiKeyProvider {
    companion object {
        init { System.loadLibrary("mews-lib") }
    }

    private external fun getGeminiApiKey(): String

    override fun getKey(): String =getGeminiApiKey()
}

class RssHubApiKeyProvider: ApiKeyProvider {
    companion object {
        init { System.loadLibrary("mews-lib") }
    }

    private external fun getRssHubKey(): String

    override fun getKey(): String = getRssHubKey()
}

class ServerAddressProvider: ApiKeyProvider {
    companion object {
        init { System.loadLibrary("mews-lib") }
    }

    private external fun getServerAddress(): String

    override fun getKey(): String = getServerAddress()
}