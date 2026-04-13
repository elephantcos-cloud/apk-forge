/**
 * APK Forge — Native Bridge
 * JNI interface connecting Android (Kotlin/Java) ↔ C++ native layer
 *
 * Provides: token encryption, SHA-256 hashing, file integrity checks
 */

#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <iomanip>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "ApkForge_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// ─── XOR Cipher for token obfuscation ───────────────────────────────────────
static std::string xor_cipher(const std::string& input, uint8_t key) {
    std::string result = input;
    for (size_t i = 0; i < result.size(); i++) {
        result[i] ^= (key + static_cast<uint8_t>(i % 7));
    }
    return result;
}

// ─── Simple SHA-256 constants ────────────────────────────────────────────────
static const uint32_t K[64] = {
    0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,
    0x923f82a4,0xab1c5ed5,0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,
    0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,0xe49b69c1,0xefbe4786,
    0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
    0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,
    0x06ca6351,0x14292967,0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,
    0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,0xa2bfe8a1,0xa81a664b,
    0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
    0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,
    0x5b9cca4f,0x682e6ff3,0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,
    0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
};

#define ROTR(x, n) (((x) >> (n)) | ((x) << (32 - (n))))
#define CH(x,y,z)  (((x) & (y)) ^ (~(x) & (z)))
#define MAJ(x,y,z) (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))
#define EP0(x)     (ROTR(x,2)  ^ ROTR(x,13) ^ ROTR(x,22))
#define EP1(x)     (ROTR(x,6)  ^ ROTR(x,11) ^ ROTR(x,25))
#define SIG0(x)    (ROTR(x,7)  ^ ROTR(x,18) ^ ((x) >> 3))
#define SIG1(x)    (ROTR(x,17) ^ ROTR(x,19) ^ ((x) >> 10))

static std::string sha256_compute(const std::string& input) {
    uint32_t h0=0x6a09e667, h1=0xbb67ae85, h2=0x3c6ef372, h3=0xa54ff53a;
    uint32_t h4=0x510e527f, h5=0x9b05688c, h6=0x1f83d9ab, h7=0x5be0cd19;

    std::vector<uint8_t> msg(input.begin(), input.end());
    uint64_t bit_len = msg.size() * 8;
    msg.push_back(0x80);
    while (msg.size() % 64 != 56) msg.push_back(0x00);
    for (int i = 7; i >= 0; i--) msg.push_back((bit_len >> (i * 8)) & 0xFF);

    for (size_t i = 0; i < msg.size(); i += 64) {
        uint32_t w[64];
        for (int j = 0; j < 16; j++) {
            w[j] = (msg[i+j*4]<<24)|(msg[i+j*4+1]<<16)|(msg[i+j*4+2]<<8)|msg[i+j*4+3];
        }
        for (int j = 16; j < 64; j++) {
            w[j] = SIG1(w[j-2]) + w[j-7] + SIG0(w[j-15]) + w[j-16];
        }
        uint32_t a=h0,b=h1,c=h2,d=h3,e=h4,f=h5,g=h6,h=h7;
        for (int j = 0; j < 64; j++) {
            uint32_t t1 = h + EP1(e) + CH(e,f,g) + K[j] + w[j];
            uint32_t t2 = EP0(a) + MAJ(a,b,c);
            h=g; g=f; f=e; e=d+t1; d=c; c=b; b=a; a=t1+t2;
        }
        h0+=a; h1+=b; h2+=c; h3+=d; h4+=e; h5+=f; h6+=g; h7+=h;
    }

    std::ostringstream oss;
    oss << std::hex << std::setfill('0');
    for (uint32_t v : {h0,h1,h2,h3,h4,h5,h6,h7}) oss << std::setw(8) << v;
    return oss.str();
}

// ─────────────────────────────────────────────────────────────────────────────
//  JNI EXPORTS
// ─────────────────────────────────────────────────────────────────────────────

extern "C" {

/**
 * Compute SHA-256 hash of a string
 */
JNIEXPORT jstring JNICALL
Java_com_apkforge_security_NativeCrypto_computeSha256(
        JNIEnv* env, jobject /* this */, jstring input) {
    const char* raw = env->GetStringUTFChars(input, nullptr);
    std::string result = sha256_compute(std::string(raw));
    env->ReleaseStringUTFChars(input, raw);
    LOGD("SHA256 computed successfully");
    return env->NewStringUTF(result.c_str());
}

/**
 * Obfuscate a token using XOR cipher
 */
JNIEXPORT jbyteArray JNICALL
Java_com_apkforge_security_NativeCrypto_obfuscateToken(
        JNIEnv* env, jobject /* this */, jstring token, jint key) {
    const char* raw = env->GetStringUTFChars(token, nullptr);
    std::string result = xor_cipher(std::string(raw), static_cast<uint8_t>(key));
    env->ReleaseStringUTFChars(token, raw);

    jbyteArray arr = env->NewByteArray(static_cast<jsize>(result.size()));
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(result.size()),
                            reinterpret_cast<const jbyte*>(result.data()));
    return arr;
}

/**
 * Deobfuscate token
 */
JNIEXPORT jstring JNICALL
Java_com_apkforge_security_NativeCrypto_deobfuscateToken(
        JNIEnv* env, jobject /* this */, jbyteArray data, jint key) {
    jsize len = env->GetArrayLength(data);
    jbyte* raw = env->GetByteArrayElements(data, nullptr);
    std::string s(reinterpret_cast<char*>(raw), len);
    env->ReleaseByteArrayElements(data, raw, JNI_ABORT);
    std::string result = xor_cipher(s, static_cast<uint8_t>(key));
    return env->NewStringUTF(result.c_str());
}

/**
 * Verify file integrity by comparing hash
 */
JNIEXPORT jboolean JNICALL
Java_com_apkforge_security_NativeCrypto_verifyIntegrity(
        JNIEnv* env, jobject /* this */, jstring content, jstring expectedHash) {
    const char* raw = env->GetStringUTFChars(content, nullptr);
    const char* expected = env->GetStringUTFChars(expectedHash, nullptr);
    std::string computed = sha256_compute(std::string(raw));
    bool match = (computed == std::string(expected));
    env->ReleaseStringUTFChars(content, raw);
    env->ReleaseStringUTFChars(expectedHash, expected);
    LOGD("Integrity check: %s", match ? "PASS" : "FAIL");
    return static_cast<jboolean>(match);
}

/**
 * Get native library version info
 */
JNIEXPORT jstring JNICALL
Java_com_apkforge_security_NativeCrypto_getNativeVersion(
        JNIEnv* env, jobject /* this */) {
    return env->NewStringUTF("APK-Forge-Native/1.0.0 (C++17/SHA256/XOR)");
}

} // extern "C"
