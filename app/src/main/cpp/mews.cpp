#include <jni.h>
#include <string>
#include <vector>

std::string getDecodedApiKey() {
    std::vector<char> obfuscatedKey = {0};

    char xorKey = '@';

    std::string apiKey;
    apiKey.reserve(obfuscatedKey.size());
    for (char c : obfuscatedKey) {
        apiKey += c ^ xorKey;
    }
    return apiKey;
}

std::string getDecodedServerKey() {
    std::vector<char> obfuscatedKey = {0};

    char xorKey = '@';

    std::string key;
    key.reserve(obfuscatedKey.size());
    for (char c : obfuscatedKey) {
        key += c ^ xorKey;
    }
    return key;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_rds_mews_GeminiApiKeyProvider_getGeminiApiKey (
        JNIEnv* env,
        jobject /* this */) {
    std::string apiKey = getDecodedApiKey();
    return env->NewStringUTF(apiKey.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_rds_mews_RssHubApiKeyProvider_getRssHubKey (
        JNIEnv* env,
        jobject /* this */) {
    std::string apiKey = getDecodedServerKey();
    return env->NewStringUTF(apiKey.c_str());
}