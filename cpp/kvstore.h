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

int64_t kv_get_long(void* handle, const char* key, int64_t default_value);
float   kv_get_float(void* handle, const char* key, float default_value);
bool    kv_get_bool(void* handle, const char* key, bool default_value);

// Returns a malloc'd, NUL-terminated copy of the stored string, or NULL if
// the key is absent / wrong type. Caller must release with kv_free.
char*  kv_get_string_dup(void* handle, const char* key);
void   kv_free(void* p);

#ifdef __cplusplus
}
#endif
