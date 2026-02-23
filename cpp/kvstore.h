#pragma once
#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

void*  kv_open(const char* path);
void   kv_close(void* handle);

void   kv_put_long(void* handle, const char* key, int64_t value);
void   kv_put_float(void* handle, const char* key, float value);
void   kv_put_string(void* handle, const char* key, const char* value);
void   kv_put_bool(void* handle, const char* key, bool value);

int64_t     kv_get_long(void* handle, const char* key, int64_t default_value);
float       kv_get_float(void* handle, const char* key, float default_value);
const char* kv_get_string(void* handle, const char* key, const char* default_value);
bool        kv_get_bool(void* handle, const char* key, bool default_value);

#ifdef __cplusplus
}
#endif
