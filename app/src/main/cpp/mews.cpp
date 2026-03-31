#include <jni.h>
#include <string>
#include <vector>

std::string getDecodedApiKey() {
    std::vector<char> obfuscatedKey = {};

    char xorKey = '@';

    std::string apiKey;
    apiKey.reserve(obfuscatedKey.size());
    for (char c : obfuscatedKey) {
        apiKey += c ^ xorKey;
    }
    return apiKey;
}

std::string getDecodedServerKey() {
    std::vector<char> obfuscatedKey = {};

    char xorKey = '@';

    std::string key;
    key.reserve(obfuscatedKey.size());
    for (char c : obfuscatedKey) {
        key += c ^ xorKey;
    }
    return key;
}

std::string getDecodedMinifluxKey() {
    std::vector<char> obfuscatedKey = {};

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

extern "C" JNIEXPORT jstring JNICALL
Java_com_rds_mews_MinifluxApiKeyProvider_getDecodedMinifluxKey (
        JNIEnv* env,
        jobject /* this */) {
    std::string apiKey = getDecodedMinifluxKey();
    return env->NewStringUTF(apiKey.c_str());
}

std::string getDecodedProxyAddress() {
    std::vector<char> obfuscatedKey = {};

    char xorKey = '@';

    std::string address;
    address.reserve(obfuscatedKey.size());
    for (char c : obfuscatedKey) {
        address += c ^ xorKey;
    }
    return address;
}

std::string getDecodedHubAddress() {
    std::vector<char> obfuscatedKey = {};

    char xorKey = '@';

    std::string address;
    address.reserve(obfuscatedKey.size());
    for (char c : obfuscatedKey) {
        address += c ^ xorKey;
    }
    return address;
}

std::string getDecodedMinifluxAddress() {
    std::vector<char> obfuscatedKey = {};

    char xorKey = '@';

    std::string address;
    address.reserve(obfuscatedKey.size());
    for (char c : obfuscatedKey) {
        address += c ^ xorKey;
    }
    return address;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_rds_mews_ProxyAddressProvider_getProxyAddress (
        JNIEnv* env,
        jobject /* this */) {
    std::string apiKey = getDecodedProxyAddress();
    return env->NewStringUTF(apiKey.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_rds_mews_RSSHubAddressProvider_getHubAddress (
        JNIEnv* env,
        jobject /* this */) {
    std::string apiKey = getDecodedHubAddress();
    return env->NewStringUTF(apiKey.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_rds_mews_MinifluxAddressProvider_getMinifluxAddress (
        JNIEnv* env,
        jobject /* this */) {
    std::string apiKey = getDecodedMinifluxAddress();
    return env->NewStringUTF(apiKey.c_str());
}