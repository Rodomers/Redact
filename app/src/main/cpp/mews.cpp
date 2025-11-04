#include <jni.h>
#include <string>
#include <vector>

std::string getDecodedApiKey() {
    std::vector<char> obfuscatedKey = {0, 0, 0, 0, 0, 0, 0, 0};

    char xorKey = '@';

    std::string apiKey;
    apiKey.reserve(obfuscatedKey.size());
    for (char c : obfuscatedKey) {
        apiKey += c ^ xorKey;
    }
    return apiKey;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_rds_mews_GeminiApiKeyProvider_getGeminiApiKey (
        JNIEnv* env,
        jobject /* this */) {
    std::string apiKey = getDecodedApiKey();
    return env->NewStringUTF(apiKey.c_str());
}