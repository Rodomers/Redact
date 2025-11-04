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
