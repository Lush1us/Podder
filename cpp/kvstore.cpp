#include "kvstore.h"

#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <string>
#include <unordered_map>
#include <vector>

#include <fcntl.h>
#include <pthread.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

// ---------------------------------------------------------------------------
// File format constants
// ---------------------------------------------------------------------------

static constexpr uint32_t MAGIC        = 0x504B5653u; // "PKVS"
static constexpr uint8_t  VERSION      = 1;
static constexpr uint8_t  ENCRYPT_NONE = 0;
static constexpr size_t   HEADER_SIZE  = 64;

// Entry type tags
static constexpr uint8_t TYPE_LONG   = 0x01;
static constexpr uint8_t TYPE_FLOAT  = 0x02;
static constexpr uint8_t TYPE_STRING = 0x03;
static constexpr uint8_t TYPE_BOOL   = 0x04;

// ---------------------------------------------------------------------------
// Internal store struct
// ---------------------------------------------------------------------------

struct KVStore {
    int   fd;
    void* map;
    size_t map_size;
    pthread_rwlock_t lock;
    std::unordered_map<std::string, std::vector<uint8_t>> cache;
};

// ---------------------------------------------------------------------------
// Helpers: serialise a single typed value into a byte vector stored in cache
// ---------------------------------------------------------------------------

static std::vector<uint8_t> encode_long(int64_t v) {
    std::vector<uint8_t> buf(1 + sizeof(int64_t));
    buf[0] = TYPE_LONG;
    memcpy(buf.data() + 1, &v, sizeof(int64_t));
    return buf;
}

static std::vector<uint8_t> encode_float(float v) {
    std::vector<uint8_t> buf(1 + sizeof(float));
    buf[0] = TYPE_FLOAT;
    memcpy(buf.data() + 1, &v, sizeof(float));
    return buf;
}

static std::vector<uint8_t> encode_string(const char* v) {
    size_t len = strlen(v);
    std::vector<uint8_t> buf(1 + len);
    buf[0] = TYPE_STRING;
    memcpy(buf.data() + 1, v, len);
    return buf;
}

static std::vector<uint8_t> encode_bool(bool v) {
    std::vector<uint8_t> buf(2);
    buf[0] = TYPE_BOOL;
    buf[1] = v ? 1 : 0;
    return buf;
}

// ---------------------------------------------------------------------------
// Write the entire cache to the file (header + all entries)
//
// Entry wire format: [type:1][key_len:2][val_len:4][key:key_len][value:val_len]
// where value bytes are the raw payload (no type byte — type is in its own field).
// ---------------------------------------------------------------------------

static void flush_to_file(KVStore* s) {
    // 1. Compute required size
    size_t data_size = HEADER_SIZE;
    for (auto& kv : s->cache) {
        // Each cache entry: buf[0] is the type tag; buf[1..] is the raw value.
        const std::string& key = kv.first;
        const std::vector<uint8_t>& val = kv.second;
        size_t val_payload = val.size() - 1; // strip the type byte
        data_size += 1 + 2 + 4 + key.size() + val_payload;
    }

    // 2. Truncate file to new size
    if (ftruncate(s->fd, static_cast<off_t>(data_size)) != 0) return;

    // 3. Remap
    if (s->map != MAP_FAILED && s->map != nullptr) {
        munmap(s->map, s->map_size);
    }
    s->map = mmap(nullptr, data_size, PROT_READ | PROT_WRITE, MAP_SHARED, s->fd, 0);
    s->map_size = data_size;
    if (s->map == MAP_FAILED) return;

    uint8_t* p = static_cast<uint8_t*>(s->map);

    // 4. Write header
    memset(p, 0, HEADER_SIZE);
    uint32_t magic = MAGIC;
    memcpy(p + 0, &magic, 4);
    p[4] = VERSION;
    p[5] = ENCRYPT_NONE;
    // bytes 6..63 remain zero

    // 5. Write entries
    uint8_t* cursor = p + HEADER_SIZE;
    for (auto& kv : s->cache) {
        const std::string& key = kv.first;
        const std::vector<uint8_t>& val = kv.second;

        uint8_t  type     = val[0];
        uint16_t key_len  = static_cast<uint16_t>(key.size());
        uint32_t val_len  = static_cast<uint32_t>(val.size() - 1);

        *cursor++ = type;
        memcpy(cursor, &key_len, 2); cursor += 2;
        memcpy(cursor, &val_len, 4); cursor += 4;
        memcpy(cursor, key.data(), key_len); cursor += key_len;
        memcpy(cursor, val.data() + 1, val_len); cursor += val_len;
    }

    // 6. Sync
    msync(s->map, data_size, MS_SYNC);
}

// ---------------------------------------------------------------------------
// Load all entries from the mmap into the in-memory cache
// ---------------------------------------------------------------------------

static void load_from_file(KVStore* s) {
    if (s->map_size <= HEADER_SIZE) return;

    const uint8_t* p      = static_cast<const uint8_t*>(s->map);
    const uint8_t* end    = p + s->map_size;
    const uint8_t* cursor = p + HEADER_SIZE;

    while (cursor + 7 <= end) { // minimum entry: 1+2+4 bytes header
        uint8_t  type    = cursor[0];
        uint16_t key_len;
        uint32_t val_len;
        memcpy(&key_len, cursor + 1, 2);
        memcpy(&val_len, cursor + 3, 4);
        cursor += 7;

        if (cursor + key_len + val_len > end) break; // corrupt / truncated

        std::string key(reinterpret_cast<const char*>(cursor), key_len);
        cursor += key_len;

        std::vector<uint8_t> val(1 + val_len);
        val[0] = type;
        memcpy(val.data() + 1, cursor, val_len);
        cursor += val_len;

        s->cache[key] = std::move(val);
    }
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

void* kv_open(const char* path) {
    int fd = open(path, O_RDWR | O_CREAT, 0600);
    if (fd < 0) return nullptr;

    struct stat st;
    fstat(fd, &st);
    bool is_new = (st.st_size == 0);

    KVStore* s = new KVStore();
    s->fd  = fd;
    s->map = MAP_FAILED;
    s->map_size = 0;
    pthread_rwlock_init(&s->lock, nullptr);

    if (is_new) {
        // Write a blank header so we have something to mmap
        if (ftruncate(fd, static_cast<off_t>(HEADER_SIZE)) != 0) {
            close(fd);
            delete s;
            return nullptr;
        }
        s->map      = mmap(nullptr, HEADER_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
        s->map_size = HEADER_SIZE;
        if (s->map == MAP_FAILED) {
            close(fd);
            delete s;
            return nullptr;
        }
        uint8_t* p = static_cast<uint8_t*>(s->map);
        memset(p, 0, HEADER_SIZE);
        uint32_t magic = MAGIC;
        memcpy(p + 0, &magic, 4);
        p[4] = VERSION;
        p[5] = ENCRYPT_NONE;
        msync(s->map, HEADER_SIZE, MS_SYNC);
    } else {
        size_t map_size = static_cast<size_t>(st.st_size);
        s->map      = mmap(nullptr, map_size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
        s->map_size = map_size;
        if (s->map == MAP_FAILED) {
            close(fd);
            delete s;
            return nullptr;
        }
        load_from_file(s);
    }

    return static_cast<void*>(s);
}

void kv_close(void* handle) {
    if (!handle) return;
    KVStore* s = static_cast<KVStore*>(handle);
    if (s->map != MAP_FAILED && s->map != nullptr) {
        msync(s->map, s->map_size, MS_SYNC);
        munmap(s->map, s->map_size);
    }
    close(s->fd);
    pthread_rwlock_destroy(&s->lock);
    delete s;
}

// ---------------------------------------------------------------------------
// Put operations
// ---------------------------------------------------------------------------

void kv_put_long(void* handle, const char* key, int64_t value) {
    if (!handle || !key) return;
    KVStore* s = static_cast<KVStore*>(handle);
    pthread_rwlock_wrlock(&s->lock);
    s->cache[key] = encode_long(value);
    flush_to_file(s);
    pthread_rwlock_unlock(&s->lock);
}

void kv_put_float(void* handle, const char* key, float value) {
    if (!handle || !key) return;
    KVStore* s = static_cast<KVStore*>(handle);
    pthread_rwlock_wrlock(&s->lock);
    s->cache[key] = encode_float(value);
    flush_to_file(s);
    pthread_rwlock_unlock(&s->lock);
}

void kv_put_string(void* handle, const char* key, const char* value) {
    if (!handle || !key || !value) return;
    KVStore* s = static_cast<KVStore*>(handle);
    pthread_rwlock_wrlock(&s->lock);
    s->cache[key] = encode_string(value);
    flush_to_file(s);
    pthread_rwlock_unlock(&s->lock);
}

void kv_put_bool(void* handle, const char* key, bool value) {
    if (!handle || !key) return;
    KVStore* s = static_cast<KVStore*>(handle);
    pthread_rwlock_wrlock(&s->lock);
    s->cache[key] = encode_bool(value);
    flush_to_file(s);
    pthread_rwlock_unlock(&s->lock);
}

// ---------------------------------------------------------------------------
// Get operations
// ---------------------------------------------------------------------------

int64_t kv_get_long(void* handle, const char* key, int64_t default_value) {
    if (!handle || !key) return default_value;
    KVStore* s = static_cast<KVStore*>(handle);
    pthread_rwlock_rdlock(&s->lock);
    auto it = s->cache.find(key);
    int64_t result = default_value;
    if (it != s->cache.end() && it->second[0] == TYPE_LONG && it->second.size() == 1 + sizeof(int64_t)) {
        memcpy(&result, it->second.data() + 1, sizeof(int64_t));
    }
    pthread_rwlock_unlock(&s->lock);
    return result;
}

float kv_get_float(void* handle, const char* key, float default_value) {
    if (!handle || !key) return default_value;
    KVStore* s = static_cast<KVStore*>(handle);
    pthread_rwlock_rdlock(&s->lock);
    auto it = s->cache.find(key);
    float result = default_value;
    if (it != s->cache.end() && it->second[0] == TYPE_FLOAT && it->second.size() == 1 + sizeof(float)) {
        memcpy(&result, it->second.data() + 1, sizeof(float));
    }
    pthread_rwlock_unlock(&s->lock);
    return result;
}

const char* kv_get_string(void* handle, const char* key, const char* default_value) {
    if (!handle || !key) return default_value;
    KVStore* s = static_cast<KVStore*>(handle);
    pthread_rwlock_rdlock(&s->lock);
    auto it = s->cache.find(key);
    const char* result = default_value;
    if (it != s->cache.end() && it->second[0] == TYPE_STRING) {
        // Return pointer into the cache vector's string payload.
        // The caller must not free this — it is valid until the next write or kv_close.
        result = reinterpret_cast<const char*>(it->second.data() + 1);
    }
    pthread_rwlock_unlock(&s->lock);
    return result;
}

bool kv_get_bool(void* handle, const char* key, bool default_value) {
    if (!handle || !key) return default_value;
    KVStore* s = static_cast<KVStore*>(handle);
    pthread_rwlock_rdlock(&s->lock);
    auto it = s->cache.find(key);
    bool result = default_value;
    if (it != s->cache.end() && it->second[0] == TYPE_BOOL && it->second.size() == 2) {
        result = (it->second[1] != 0);
    }
    pthread_rwlock_unlock(&s->lock);
    return result;
}
