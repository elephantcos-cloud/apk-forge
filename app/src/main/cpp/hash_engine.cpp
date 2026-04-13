/**
 * APK Forge — Hash Engine
 * Fast CRC32 & Adler32 for quick APK integrity checks
 */

#include <jni.h>
#include <cstdint>
#include <string>
#include <android/log.h>

#define LOG_TAG "ApkForge_Hash"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static uint32_t crc32_compute(const uint8_t* data, size_t length) {
    uint32_t crc = 0xFFFFFFFF;
    for (size_t i = 0; i < length; i++) {
        crc ^= data[i];
        for (int j = 0; j < 8; j++) {
            crc = (crc >> 1) ^ (0xEDB88320 & -(crc & 1));
        }
    }
    return ~crc;
}

static uint32_t adler32_compute(const uint8_t* data, size_t length) {
    uint32_t a = 1, b = 0;
    constexpr uint32_t MOD_ADLER = 65521;
    for (size_t i = 0; i < length; i++) {
        a = (a + data[i]) % MOD_ADLER;
        b = (b + a) % MOD_ADLER;
    }
    return (b << 16) | a;
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_apkforge_security_NativeCrypto_computeCrc32(
        JNIEnv* env, jobject, jbyteArray data) {
    jsize len = env->GetArrayLength(data);
    jbyte* raw = env->GetByteArrayElements(data, nullptr);
    uint32_t result = crc32_compute(reinterpret_cast<uint8_t*>(raw), len);
    env->ReleaseByteArrayElements(data, raw, JNI_ABORT);
    LOGD("CRC32 computed: 0x%08X", result);
    return static_cast<jlong>(result) & 0xFFFFFFFFL;
}

JNIEXPORT jlong JNICALL
Java_com_apkforge_security_NativeCrypto_computeAdler32(
        JNIEnv* env, jobject, jbyteArray data) {
    jsize len = env->GetArrayLength(data);
    jbyte* raw = env->GetByteArrayElements(data, nullptr);
    uint32_t result = adler32_compute(reinterpret_cast<uint8_t*>(raw), len);
    env->ReleaseByteArrayElements(data, raw, JNI_ABORT);
    return static_cast<jlong>(result) & 0xFFFFFFFFL;
}

} // extern "C"
