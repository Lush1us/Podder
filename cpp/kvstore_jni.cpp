#include <jni.h>
#include "kvstore.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_dev_podder_data_store_KVStore_nativeOpen(JNIEnv* env, jobject, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    void* handle = kv_open(p);
    env->ReleaseStringUTFChars(path, p);
    return reinterpret_cast<jlong>(handle);
}

JNIEXPORT void JNICALL
Java_dev_podder_data_store_KVStore_nativeClose(JNIEnv*, jobject, jlong handle) {
    kv_close(reinterpret_cast<void*>(handle));
}

JNIEXPORT void JNICALL
Java_dev_podder_data_store_KVStore_nativePutLong(JNIEnv* env, jobject, jlong h, jstring key, jlong value) {
    const char* k = env->GetStringUTFChars(key, nullptr);
    kv_put_long(reinterpret_cast<void*>(h), k, value);
    env->ReleaseStringUTFChars(key, k);
}

JNIEXPORT jlong JNICALL
Java_dev_podder_data_store_KVStore_nativeGetLong(JNIEnv* env, jobject, jlong h, jstring key, jlong def) {
    const char* k = env->GetStringUTFChars(key, nullptr);
    jlong result = kv_get_long(reinterpret_cast<void*>(h), k, def);
    env->ReleaseStringUTFChars(key, k);
    return result;
}

JNIEXPORT void JNICALL
Java_dev_podder_data_store_KVStore_nativePutFloat(JNIEnv* env, jobject, jlong h, jstring key, jfloat value) {
    const char* k = env->GetStringUTFChars(key, nullptr);
    kv_put_float(reinterpret_cast<void*>(h), k, value);
    env->ReleaseStringUTFChars(key, k);
}

JNIEXPORT jfloat JNICALL
Java_dev_podder_data_store_KVStore_nativeGetFloat(JNIEnv* env, jobject, jlong h, jstring key, jfloat def) {
    const char* k = env->GetStringUTFChars(key, nullptr);
    jfloat result = kv_get_float(reinterpret_cast<void*>(h), k, def);
    env->ReleaseStringUTFChars(key, k);
    return result;
}

JNIEXPORT void JNICALL
Java_dev_podder_data_store_KVStore_nativePutString(JNIEnv* env, jobject, jlong h, jstring key, jstring value) {
    const char* k = env->GetStringUTFChars(key, nullptr);
    const char* v = env->GetStringUTFChars(value, nullptr);
    kv_put_string(reinterpret_cast<void*>(h), k, v);
    env->ReleaseStringUTFChars(key, k);
    env->ReleaseStringUTFChars(value, v);
}

JNIEXPORT jstring JNICALL
Java_dev_podder_data_store_KVStore_nativeGetString(JNIEnv* env, jobject, jlong h, jstring key, jstring def) {
    const char* k = env->GetStringUTFChars(key, nullptr);
    char* owned = kv_get_string_dup(reinterpret_cast<void*>(h), k);
    env->ReleaseStringUTFChars(key, k);
    jstring jresult;
    if (owned) {
        jresult = env->NewStringUTF(owned);
        kv_free(owned);
    } else {
        jresult = def;
    }
    return jresult;
}

JNIEXPORT void JNICALL
Java_dev_podder_data_store_KVStore_nativePutBool(JNIEnv* env, jobject, jlong h, jstring key, jboolean value) {
    const char* k = env->GetStringUTFChars(key, nullptr);
    kv_put_bool(reinterpret_cast<void*>(h), k, value);
    env->ReleaseStringUTFChars(key, k);
}

JNIEXPORT jboolean JNICALL
Java_dev_podder_data_store_KVStore_nativeGetBool(JNIEnv* env, jobject, jlong h, jstring key, jboolean def) {
    const char* k = env->GetStringUTFChars(key, nullptr);
    jboolean result = kv_get_bool(reinterpret_cast<void*>(h), k, def);
    env->ReleaseStringUTFChars(key, k);
    return result;
}

} // extern "C"
