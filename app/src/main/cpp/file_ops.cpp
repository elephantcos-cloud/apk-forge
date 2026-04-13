/**
 * APK Forge — Native File Operations
 * High-performance file I/O operations via C++ NDK
 */

#include <jni.h>
#include <string>
#include <vector>
#include <fstream>
#include <sstream>
#include <sys/stat.h>
#include <android/log.h>

#define LOG_TAG "ApkForge_FileOps"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

/**
 * Check if a file path exists and is readable
 */
JNIEXPORT jboolean JNICALL
Java_com_apkforge_utils_NativeFileOps_fileExists(
        JNIEnv* env, jobject, jstring path) {
    const char* raw = env->GetStringUTFChars(path, nullptr);
    struct stat st{};
    bool exists = (stat(raw, &st) == 0);
    env->ReleaseStringUTFChars(path, raw);
    return static_cast<jboolean>(exists);
}

/**
 * Get file size in bytes using native stat
 */
JNIEXPORT jlong JNICALL
Java_com_apkforge_utils_NativeFileOps_getFileSizeNative(
        JNIEnv* env, jobject, jstring path) {
    const char* raw = env->GetStringUTFChars(path, nullptr);
    struct stat st{};
    jlong size = -1;
    if (stat(raw, &st) == 0) {
        size = static_cast<jlong>(st.st_size);
    }
    env->ReleaseStringUTFChars(path, raw);
    return size;
}

/**
 * Fast binary read for APK validation
 */
JNIEXPORT jbyteArray JNICALL
Java_com_apkforge_utils_NativeFileOps_readApkHeader(
        JNIEnv* env, jobject, jstring path) {
    const char* raw = env->GetStringUTFChars(path, nullptr);
    std::ifstream file(raw, std::ios::binary);
    env->ReleaseStringUTFChars(path, raw);

    if (!file.is_open()) {
        LOGE("Failed to open file for APK header read");
        return nullptr;
    }

    // Read first 4 bytes (APK/ZIP magic: PK\x03\x04)
    constexpr int HEADER_SIZE = 64;
    std::vector<char> header(HEADER_SIZE);
    file.read(header.data(), HEADER_SIZE);
    jsize bytesRead = static_cast<jsize>(file.gcount());
    file.close();

    jbyteArray result = env->NewByteArray(bytesRead);
    env->SetByteArrayRegion(result, 0, bytesRead,
                            reinterpret_cast<const jbyte*>(header.data()));
    return result;
}

/**
 * Validate APK magic bytes (ZIP format: PK\x03\x04)
 */
JNIEXPORT jboolean JNICALL
Java_com_apkforge_utils_NativeFileOps_isValidApk(
        JNIEnv* env, jobject, jstring path) {
    const char* raw = env->GetStringUTFChars(path, nullptr);
    std::ifstream file(raw, std::ios::binary);
    env->ReleaseStringUTFChars(path, raw);

    if (!file.is_open()) return JNI_FALSE;

    char magic[4];
    file.read(magic, 4);
    file.close();

    // ZIP magic: 50 4B 03 04
    bool valid = (magic[0] == 0x50 && magic[1] == 0x4B &&
                  magic[2] == 0x03 && magic[3] == 0x04);
    LOGD("APK validation: %s", valid ? "VALID" : "INVALID");
    return static_cast<jboolean>(valid);
}

/**
 * Native string formatting for build logs
 */
JNIEXPORT jstring JNICALL
Java_com_apkforge_utils_NativeFileOps_formatBuildLog(
        JNIEnv* env, jobject, jstring rawLog) {
    const char* raw = env->GetStringUTFChars(rawLog, nullptr);
    std::string log(raw);
    env->ReleaseStringUTFChars(rawLog, raw);

    // Strip ANSI escape codes natively
    std::string cleaned;
    bool inEscape = false;
    for (size_t i = 0; i < log.size(); i++) {
        if (log[i] == '\033') { inEscape = true; continue; }
        if (inEscape && log[i] == 'm') { inEscape = false; continue; }
        if (!inEscape) cleaned += log[i];
    }
    return env->NewStringUTF(cleaned.c_str());
}

} // extern "C"
