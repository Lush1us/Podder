#import <Foundation/Foundation.h>
#include "kvstore.h"

@interface PodderKVStore : NSObject
- (instancetype)initWithPath:(NSString*)path;
- (void)putLong:(int64_t)value forKey:(NSString*)key;
- (int64_t)getLong:(NSString*)key default:(int64_t)def;
- (void)putFloat:(float)value forKey:(NSString*)key;
- (float)getFloat:(NSString*)key default:(float)def;
- (void)putString:(NSString*)value forKey:(NSString*)key;
- (NSString*)getString:(NSString*)key default:(NSString*)def;
- (void)putBool:(BOOL)value forKey:(NSString*)key;
- (BOOL)getBool:(NSString*)key default:(BOOL)def;
- (void)close;
@end

@implementation PodderKVStore {
    void* _handle;
}

- (instancetype)initWithPath:(NSString*)path {
    self = [super init];
    if (self) { _handle = kv_open(path.UTF8String); }
    return self;
}
- (void)putLong:(int64_t)v forKey:(NSString*)k  { kv_put_long(_handle, k.UTF8String, v); }
- (int64_t)getLong:(NSString*)k default:(int64_t)d { return kv_get_long(_handle, k.UTF8String, d); }
- (void)putFloat:(float)v forKey:(NSString*)k   { kv_put_float(_handle, k.UTF8String, v); }
- (float)getFloat:(NSString*)k default:(float)d { return kv_get_float(_handle, k.UTF8String, d); }
- (void)putString:(NSString*)v forKey:(NSString*)k { kv_put_string(_handle, k.UTF8String, v.UTF8String); }
- (NSString*)getString:(NSString*)k default:(NSString*)d {
    char* owned = kv_get_string_dup(_handle, k.UTF8String);
    if (!owned) return d;
    NSString* s = [NSString stringWithUTF8String:owned];
    kv_free(owned);
    return s ?: d;
}
- (void)putBool:(BOOL)v forKey:(NSString*)k     { kv_put_bool(_handle, k.UTF8String, v); }
- (BOOL)getBool:(NSString*)k default:(BOOL)d    { return kv_get_bool(_handle, k.UTF8String, d); }
- (void)close { kv_close(_handle); _handle = nullptr; }
@end
