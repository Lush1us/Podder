#include "kvstore.h"

#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <string>
#include <unordered_map>

#include <fcntl.h>
#include <pthread.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

// ---------------------------------------------------------------------------
// Bitcask-lite append-only KV store.
//
// File layout:
//   [ HEADER : 64 bytes ]
//   [ RECORD ]*
//
// Record layout (fixed 20-byte header + key + value):
//   u32  crc32    (over bytes [4 .. end of record])
//   u64  seq      (monotonic, ties broken in insertion order)
//   u8   type
//   u8   flags    (reserved)
//   u16  key_len
//   u32  val_len  (for STRING values, includes the trailing NUL byte)
//   u8[] key
//   u8[] value
// ---------------------------------------------------------------------------

namespace {

constexpr uint32_t MAGIC          = 0x504B5653u; // "PKVS"
constexpr uint8_t  VERSION        = 2;
constexpr uint8_t  ENCRYPT_NONE   = 0;
constexpr size_t   HEADER_SIZE    = 64;
constexpr size_t   RECORD_HEADER  = 20;
constexpr size_t   INITIAL_CAP    = 64 * 1024;
constexpr size_t   COMPACT_FLOOR  = 512 * 1024;

constexpr uint8_t TYPE_LONG      = 0x01;
constexpr uint8_t TYPE_FLOAT     = 0x02;
constexpr uint8_t TYPE_STRING    = 0x03;
constexpr uint8_t TYPE_BOOL      = 0x04;

struct IndexEntry {
    uint64_t value_offset;
    uint32_t value_len;
    uint32_t record_size; // full on-disk size (RECORD_HEADER + key_len + val_len)
    uint8_t  type;
};

struct KVStore {
    int              fd;
    uint8_t*         map;
    size_t           map_capacity;
    size_t           data_end;
    uint64_t         next_seq;
    size_t           live_bytes;
    size_t           dead_bytes;
    pthread_rwlock_t lock;
    std::unordered_map<std::string, IndexEntry> index;
};

// CRC32 (IEEE 802.3 polynomial, reflected). Small table-less implementation —
// used only for record validation, not a hot path.
uint32_t crc32(const uint8_t* data, size_t n) {
    uint32_t c = 0xFFFFFFFFu;
    for (size_t i = 0; i < n; ++i) {
        c ^= data[i];
        for (int k = 0; k < 8; ++k) {
            c = (c >> 1) ^ (0xEDB88320u & (-(int32_t)(c & 1)));
        }
    }
    return c ^ 0xFFFFFFFFu;
}

void write_header(uint8_t* p) {
    std::memset(p, 0, HEADER_SIZE);
    uint32_t magic = MAGIC;
    std::memcpy(p, &magic, 4);
    p[4] = VERSION;
    p[5] = ENCRYPT_NONE;
}

bool remap(KVStore* s, size_t new_capacity) {
    if (s->map && s->map != MAP_FAILED) {
        munmap(s->map, s->map_capacity);
        s->map = nullptr;
    }
    if (ftruncate(s->fd, static_cast<off_t>(new_capacity)) != 0) return false;
    void* m = mmap(nullptr, new_capacity, PROT_READ | PROT_WRITE, MAP_SHARED, s->fd, 0);
    if (m == MAP_FAILED) return false;
    s->map = static_cast<uint8_t*>(m);
    s->map_capacity = new_capacity;
    return true;
}

bool ensure_capacity(KVStore* s, size_t needed) {
    if (needed <= s->map_capacity) return true;
    size_t cap = s->map_capacity ? s->map_capacity : INITIAL_CAP;
    while (cap < needed) cap *= 2;
    return remap(s, cap);
}

// Append one record. Caller holds the write lock.
void append_record(KVStore* s, const char* key, size_t key_len,
                   uint8_t type, const void* value, size_t val_len) {
    size_t record_size = RECORD_HEADER + key_len + val_len;
    if (!ensure_capacity(s, s->data_end + record_size)) return;

    uint8_t* rec = s->map + s->data_end;
    uint64_t seq = s->next_seq++;
    uint8_t  flags = 0;
    uint16_t k16 = static_cast<uint16_t>(key_len);
    uint32_t v32 = static_cast<uint32_t>(val_len);

    std::memcpy(rec + 4,  &seq,   8);
    rec[12] = type;
    rec[13] = flags;
    std::memcpy(rec + 14, &k16,   2);
    std::memcpy(rec + 16, &v32,   4);
    std::memcpy(rec + RECORD_HEADER, key, key_len);
    std::memcpy(rec + RECORD_HEADER + key_len, value, val_len);

    uint32_t c = crc32(rec + 4, record_size - 4);
    std::memcpy(rec, &c, 4);

    std::string k(key, key_len);
    auto it = s->index.find(k);
    if (it != s->index.end()) {
        s->dead_bytes += it->second.record_size;
        s->live_bytes -= it->second.value_len;
    }
    IndexEntry e;
    e.value_offset = s->data_end + RECORD_HEADER + key_len;
    e.value_len    = v32;
    e.record_size  = static_cast<uint32_t>(record_size);
    e.type         = type;
    s->index[std::move(k)] = e;

    s->data_end   += record_size;
    s->live_bytes += v32;
}

// Scan the log on open. Tolerates a torn tail record: on CRC mismatch or a
// truncated/bogus header we truncate the file back to the last valid record.
void recover(KVStore* s, size_t file_size) {
    size_t off = HEADER_SIZE;
    while (off + RECORD_HEADER <= file_size) {
        const uint8_t* rec = s->map + off;
        uint32_t stored_crc;
        uint64_t seq;
        uint16_t key_len;
        uint32_t val_len;
        std::memcpy(&stored_crc, rec,      4);
        std::memcpy(&seq,        rec + 4,  8);
        uint8_t type = rec[12];
        std::memcpy(&key_len,    rec + 14, 2);
        std::memcpy(&val_len,    rec + 16, 4);

        size_t record_size = RECORD_HEADER + key_len + val_len;
        if (off + record_size > file_size) break;
        if (crc32(rec + 4, record_size - 4) != stored_crc) break;
        if (type != TYPE_LONG && type != TYPE_FLOAT &&
            type != TYPE_STRING && type != TYPE_BOOL) break;

        std::string k(reinterpret_cast<const char*>(rec + RECORD_HEADER), key_len);
        auto it = s->index.find(k);
        if (it != s->index.end()) {
            s->dead_bytes += it->second.record_size;
            s->live_bytes -= it->second.value_len;
        }
        IndexEntry e;
        e.value_offset = off + RECORD_HEADER + key_len;
        e.value_len    = val_len;
        e.record_size  = static_cast<uint32_t>(record_size);
        e.type         = type;
        s->index[std::move(k)] = e;
        s->live_bytes += val_len;
        if (seq >= s->next_seq) s->next_seq = seq + 1;

        off += record_size;
    }

    s->data_end = off;
    if (off < file_size) {
        // Torn / truncated tail — trim so future appends start at a clean boundary.
        ftruncate(s->fd, static_cast<off_t>(off));
    }
}

// Rewrite the log dropping overwritten records. Runs on open only, when
// dead_bytes exceeds the floor AND more than half the file is garbage.
bool compact(KVStore* s, const std::string& path) {
    std::string tmp = path + ".compact";
    int tfd = open(tmp.c_str(), O_RDWR | O_CREAT | O_TRUNC, 0600);
    if (tfd < 0) return false;

    size_t cap = INITIAL_CAP;
    size_t needed = HEADER_SIZE;
    for (auto& kv : s->index) {
        needed += RECORD_HEADER + kv.first.size() + kv.second.value_len;
    }
    while (cap < needed) cap *= 2;
    if (ftruncate(tfd, static_cast<off_t>(cap)) != 0) { close(tfd); unlink(tmp.c_str()); return false; }
    void* tm = mmap(nullptr, cap, PROT_READ | PROT_WRITE, MAP_SHARED, tfd, 0);
    if (tm == MAP_FAILED) { close(tfd); unlink(tmp.c_str()); return false; }
    uint8_t* tp = static_cast<uint8_t*>(tm);

    write_header(tp);
    size_t off = HEADER_SIZE;
    uint64_t seq = 0;
    std::unordered_map<std::string, IndexEntry> new_index;
    new_index.reserve(s->index.size());

    for (auto& kv : s->index) {
        const std::string& key = kv.first;
        IndexEntry& e = kv.second;
        size_t record_size = RECORD_HEADER + key.size() + e.value_len;
        uint8_t* rec = tp + off;

        uint16_t k16 = static_cast<uint16_t>(key.size());
        uint32_t v32 = e.value_len;
        std::memcpy(rec + 4,  &seq, 8);
        rec[12] = e.type;
        rec[13] = 0;
        std::memcpy(rec + 14, &k16, 2);
        std::memcpy(rec + 16, &v32, 4);
        std::memcpy(rec + RECORD_HEADER, key.data(), key.size());
        std::memcpy(rec + RECORD_HEADER + key.size(),
                    s->map + e.value_offset, e.value_len);
        uint32_t c = crc32(rec + 4, record_size - 4);
        std::memcpy(rec, &c, 4);

        IndexEntry ne = e;
        ne.value_offset = off + RECORD_HEADER + key.size();
        ne.record_size  = static_cast<uint32_t>(record_size);
        new_index[key] = ne;

        off += record_size;
        ++seq;
    }

    msync(tm, off, MS_SYNC);
    munmap(tm, cap);
    // Trim tail slack so data_end == file_size when we reopen.
    ftruncate(tfd, static_cast<off_t>(off));
    fsync(tfd);
    close(tfd);

    if (rename(tmp.c_str(), path.c_str()) != 0) { unlink(tmp.c_str()); return false; }

    if (s->map && s->map != MAP_FAILED) munmap(s->map, s->map_capacity);
    close(s->fd);

    int nfd = open(path.c_str(), O_RDWR, 0600);
    if (nfd < 0) { s->fd = -1; s->map = nullptr; return false; }
    struct stat st;
    fstat(nfd, &st);
    size_t new_cap = static_cast<size_t>(st.st_size);
    if (new_cap < INITIAL_CAP) new_cap = INITIAL_CAP;
    if (ftruncate(nfd, static_cast<off_t>(new_cap)) != 0) { close(nfd); return false; }
    void* nm = mmap(nullptr, new_cap, PROT_READ | PROT_WRITE, MAP_SHARED, nfd, 0);
    if (nm == MAP_FAILED) { close(nfd); return false; }

    s->fd           = nfd;
    s->map          = static_cast<uint8_t*>(nm);
    s->map_capacity = new_cap;
    s->data_end     = off;
    s->next_seq     = seq;
    s->dead_bytes   = 0;
    s->index        = std::move(new_index);
    return true;
}

} // namespace

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

void* kv_open(const char* path) {
    if (!path) return nullptr;
    int fd = open(path, O_RDWR | O_CREAT, 0600);
    if (fd < 0) return nullptr;

    struct stat st;
    if (fstat(fd, &st) != 0) { close(fd); return nullptr; }

    KVStore* s = new KVStore();
    s->fd = fd;
    s->map = nullptr;
    s->map_capacity = 0;
    s->data_end = 0;
    s->next_seq = 0;
    s->live_bytes = 0;
    s->dead_bytes = 0;
    pthread_rwlock_init(&s->lock, nullptr);

    size_t file_size = static_cast<size_t>(st.st_size);
    bool fresh = false;

    if (file_size >= HEADER_SIZE) {
        // Peek header to decide whether to reuse or reset.
        void* hmap = mmap(nullptr, HEADER_SIZE, PROT_READ, MAP_SHARED, fd, 0);
        if (hmap == MAP_FAILED) { close(fd); delete s; return nullptr; }
        uint32_t m;
        std::memcpy(&m, hmap, 4);
        uint8_t v = static_cast<const uint8_t*>(hmap)[4];
        munmap(hmap, HEADER_SIZE);
        if (m != MAGIC || v != VERSION) {
            // Orphan v1 / foreign file — truncate and start over.
            ftruncate(fd, 0);
            file_size = 0;
            fresh = true;
        }
    } else {
        fresh = true;
    }

    if (fresh) {
        if (!remap(s, INITIAL_CAP)) { close(fd); delete s; return nullptr; }
        write_header(s->map);
        s->data_end = HEADER_SIZE;
    } else {
        // Map existing file; grow the mapping to at least INITIAL_CAP so the
        // first few appends don't immediately trigger a remap.
        size_t cap = file_size < INITIAL_CAP ? INITIAL_CAP : file_size;
        if (!remap(s, cap)) { close(fd); delete s; return nullptr; }
        recover(s, file_size);

        bool should_compact = s->dead_bytes >= COMPACT_FLOOR &&
                              s->dead_bytes * 2 >= (s->dead_bytes + s->live_bytes);
        if (should_compact) compact(s, path);
    }

    return s;
}

void kv_close(void* handle) {
    if (!handle) return;
    KVStore* s = static_cast<KVStore*>(handle);
    if (s->map && s->map != MAP_FAILED) {
        msync(s->map, s->data_end, MS_SYNC);
        // Trim trailing capacity slack so the on-disk file matches data_end.
        munmap(s->map, s->map_capacity);
        if (s->fd >= 0) ftruncate(s->fd, static_cast<off_t>(s->data_end));
    }
    if (s->fd >= 0) close(s->fd);
    pthread_rwlock_destroy(&s->lock);
    delete s;
}

// ---------------------------------------------------------------------------
// Put
// ---------------------------------------------------------------------------

void kv_put_long(void* handle, const char* key, int64_t value) {
    if (!handle || !key) return;
    KVStore* s = static_cast<KVStore*>(handle);
    pthread_rwlock_wrlock(&s->lock);
    append_record(s, key, std::strlen(key), TYPE_LONG, &value, sizeof(int64_t));
    pthread_rwlock_unlock(&s->lock);
}

void kv_put_float(void* handle, const char* key, float value) {
    if (!handle || !key) return;
    KVStore* s = static_cast<KVStore*>(handle);
    pthread_rwlock_wrlock(&s->lock);
    append_record(s, key, std::strlen(key), TYPE_FLOAT, &value, sizeof(float));
    pthread_rwlock_unlock(&s->lock);
}

void kv_put_string(void* handle, const char* key, const char* value) {
    if (!handle || !key || !value) return;
    KVStore* s = static_cast<KVStore*>(handle);
    pthread_rwlock_wrlock(&s->lock);
    // Store the trailing NUL so reads can hand the mmap bytes straight to
    // NewStringUTF if we ever reintroduce zero-copy reads.
    append_record(s, key, std::strlen(key), TYPE_STRING, value, std::strlen(value) + 1);
    pthread_rwlock_unlock(&s->lock);
}

void kv_put_bool(void* handle, const char* key, bool value) {
    if (!handle || !key) return;
    KVStore* s = static_cast<KVStore*>(handle);
    uint8_t b = value ? 1 : 0;
    pthread_rwlock_wrlock(&s->lock);
    append_record(s, key, std::strlen(key), TYPE_BOOL, &b, 1);
    pthread_rwlock_unlock(&s->lock);
}

// ---------------------------------------------------------------------------
// Get
// ---------------------------------------------------------------------------

int64_t kv_get_long(void* handle, const char* key, int64_t default_value) {
    if (!handle || !key) return default_value;
    KVStore* s = static_cast<KVStore*>(handle);
    pthread_rwlock_rdlock(&s->lock);
    int64_t result = default_value;
    auto it = s->index.find(key);
    if (it != s->index.end() && it->second.type == TYPE_LONG &&
        it->second.value_len == sizeof(int64_t)) {
        std::memcpy(&result, s->map + it->second.value_offset, sizeof(int64_t));
    }
    pthread_rwlock_unlock(&s->lock);
    return result;
}

float kv_get_float(void* handle, const char* key, float default_value) {
    if (!handle || !key) return default_value;
    KVStore* s = static_cast<KVStore*>(handle);
    pthread_rwlock_rdlock(&s->lock);
    float result = default_value;
    auto it = s->index.find(key);
    if (it != s->index.end() && it->second.type == TYPE_FLOAT &&
        it->second.value_len == sizeof(float)) {
        std::memcpy(&result, s->map + it->second.value_offset, sizeof(float));
    }
    pthread_rwlock_unlock(&s->lock);
    return result;
}

bool kv_get_bool(void* handle, const char* key, bool default_value) {
    if (!handle || !key) return default_value;
    KVStore* s = static_cast<KVStore*>(handle);
    pthread_rwlock_rdlock(&s->lock);
    bool result = default_value;
    auto it = s->index.find(key);
    if (it != s->index.end() && it->second.type == TYPE_BOOL &&
        it->second.value_len == 1) {
        result = s->map[it->second.value_offset] != 0;
    }
    pthread_rwlock_unlock(&s->lock);
    return result;
}

char* kv_get_string_dup(void* handle, const char* key) {
    if (!handle || !key) return nullptr;
    KVStore* s = static_cast<KVStore*>(handle);
    pthread_rwlock_rdlock(&s->lock);
    char* out = nullptr;
    auto it = s->index.find(key);
    if (it != s->index.end() && it->second.type == TYPE_STRING &&
        it->second.value_len > 0) {
        size_t n = it->second.value_len;
        out = static_cast<char*>(std::malloc(n));
        if (out) {
            std::memcpy(out, s->map + it->second.value_offset, n);
            out[n - 1] = '\0'; // defensive: the stored byte is already \0
        }
    }
    pthread_rwlock_unlock(&s->lock);
    return out;
}

void kv_free(void* p) {
    std::free(p);
}
