# Podder — Architectural Constitution
> The governing document for all technical decisions in this project.
> Treat this as the source of truth. When in doubt, consult here first.

---

## 1. Project Identity

**Podder** is a unified, all-format native audio application targeting iOS and Android. It handles:
- Podcast aggregation
- Music streaming
- Audiobook playback
- Live radio broadcasting

Each domain has distinct requirements. The architecture must serve all of them without compromise.

---

## 2. Architecture Overview

The project is structured around a **KMP-first shared core** with two platform-native layers sitting on top of it. The boundary is deliberate and fixed: everything that is not UI and not a platform audio API belongs in the shared module.

```
┌─────────────────────────┐     ┌─────────────────────────┐
│          iOS            │     │         Android          │
│                         │     │                          │
│   SwiftUI / UIKit       │     │    Jetpack Compose       │
│   (native UI only)      │     │    (native UI only)      │
│                         │     │                          │
│   AVPlayer /            │     │   Media3 ExoPlayer /     │
│   AVAudioEngine         │     │   Oboe (C++)             │
│   (platform audio only) │     │   (platform audio only)  │
│                         │     │                          │
└──────────┬──────────────┘     └──────────┬───────────────┘
           │                               │
           └─────────────┬─────────────────┘
                         │
          ┌──────────────▼──────────────────┐
          │        Shared KMP Module         │
          │                                  │
          │  - RSS feed parser               │
          │  - Smart Playlist engine         │
          │  - SQLDelight schema + queries   │
          │  - Playback state machine        │
          │  - Network layer (Ktor)          │
          │  - Custom C++ KV store (mmap)    │
          │  - kotlinx.serialization        │
          │  - Business logic / use cases    │
          └──────────────────────────────────┘
```

**The two things that stay platform-side, always:**
1. **Audio engine** — `AVPlayer`/`AVAudioEngine` on iOS, `Media3 ExoPlayer`/`Oboe` on Android. These are OS-coupled by nature and cannot be abstracted without losing access to critical platform APIs (audio focus, hardware decode offload, session management).
2. **UI** — SwiftUI on iOS, Jetpack Compose on Android. No shared UI framework.

Everything else is a candidate for the shared module.

---

## 3. Core Architectural Principles

### 3.1 One Audio Engine, Always
All audio formats are routed through a **single, centrally managed native audio service per platform**. No split-stack. No isolated engines per format.

**Why:** Multiple audio engines within the same app perimeter create unresolvable conflicts around:
- OS audio focus (`AVAudioSession` on iOS / `AudioManager.OnAudioFocusChangeListener` on Android)
- Hardware memory allocation
- Lock-screen media controls and notification sync

A permanent `AUDIOFOCUS_LOSS` on Android must halt playback and release resources immediately. If two engines are running, they race for that callback — producing zombie audio processes or silent failures to resume.

**Rule:** One engine. One session. One source of truth for playback state. The playback **state machine** that drives the engine lives in the KMP shared module; the engine itself does not.

### 3.2 KMP Shared Module is the Default Home for Logic
Before writing any business logic on a platform side, ask: *does this touch a platform API directly?* If no, it belongs in the shared module.

The shared module is written in Kotlin and compiled to:
- A standard Kotlin library on Android (JVM/ART, zero overhead)
- A native ARM binary (XCFramework) on iOS, called directly from Swift with no bridge or serialization cost

**What lives in the shared module:**
- Feed ingestion and RSS parsing
- Smart Playlist rule engine and SQL query generation
- SQLDelight database schema, migrations, and typed queries
- Playback state machine (playing / paused / buffering / error / idle transitions)
- Network layer (Ktor) for stream URL resolution, feed fetching, auth
- Resume position logic and custom KV store read/write wrappers
- All serialization (kotlinx.serialization)
- Domain models (Track, Episode, Chapter, Station, etc.)

**What stays platform-side:**
- Everything touching `AVPlayer`, `AVAudioEngine`, `ExoPlayer`, `Oboe`
- Audio session / focus management
- All UI code
- Platform notification and lock-screen controls (though driven by state from the shared module)

### 3.3 Storage is Bifurcated — No Exceptions
| Data Type | Store | Reason |
|---|---|---|
| High-frequency state (resume positions, playback settings) | **Custom C++ KV store** via KMP `expect/actual` | mmap-backed, fire-and-forget writes, zero third-party dependency |
| Relational media catalog (tracks, episodes, albums, chapters) | **SQLDelight** (shared KMP) | Type-safe, KMP-native, single schema for both platforms |

The custom KV store is a C++ mmap implementation written once and bridged to both platforms:
- iOS via Objective-C++ wrapper
- Android via JNI
- KMP shared module exposes a clean `expect/actual` Kotlin API

**Supported value types:** `Long`, `Float`, `String`, `Boolean` — covers all current use cases without over-engineering.
**Thread model:** readers-writer lock (`pthread_rwlock`) — multiple concurrent reads, writes briefly block readers. Matches the single-writer (playback state machine) / multi-reader (UI) access pattern.
**Encryption:** reserved in the file format header for v2. Not implemented in v1.

**Never use:**
- `UserDefaults` for anything written more than once per user action (backed by .plist, overwrites entire file on sync)
- `SharedPreferences` for anything performance-sensitive (silent failures under low storage, can block UI thread)
- `Room` or `CoreData` — SQLDelight replaces both within the KMP context
- `DataStore` for sub-millisecond write paths (modern and safe, but benchmarks slower than mmap for high-frequency operations)
- `MMKV` — the custom implementation gives us identical performance with full ownership and no external C++ dependency

### 3.4 UI Virtualization is Mandatory
Libraries can contain thousands of tracks. Rendering them naively destroys scroll performance.

- **iOS (SwiftUI):** `LazyVStack` or `List` — never plain `VStack` for dynamic collections
- **Android (Jetpack Compose):** `LazyColumn` with **explicit `key` parameter** on every item

The `key` parameter is non-negotiable on Android. Without it, Compose tracks items by index. Any insertion, deletion, or reorder triggers a full recomposition of the visible list. With a stable unique key (`key = { it.trackId }`), only changed items recompose.

### 3.5 All Parsing Lives on Background Threads
RSS/XML parsing is CPU-intensive and must never touch the main thread. In the KMP shared module, parsing runs on `Dispatchers.IO` via Kotlin Coroutines. The platform layer observes results via shared state flows or callbacks — it never blocks.

SAX/pull-style parsing is preferred over DOM. Documents can exceed 10MB; loading the full tree into memory risks OOM kills.

---

## 4. KMP Module Structure

```
shared/
├── commonMain/
│   ├── data/
│   │   ├── db/          # SQLDelight schema files (.sq) and generated queries
│   │   ├── network/     # Ktor client, feed fetching, stream resolution
│   │   └── store/       # Custom KV store expect/actual interface
│   ├── domain/
│   │   ├── model/       # Track, Episode, Chapter, Station, Playlist, etc.
│   │   ├── parser/      # RSS/XML feed parser
│   │   ├── playlist/    # Smart Playlist rule engine + SQL generator
│   │   └── player/      # Playback state machine (no platform APIs here)
│   └── util/
│       └── time/        # UTC timestamp helpers
├── iosMain/             # iOS-specific expect/actual implementations
└── androidMain/         # Android-specific expect/actual implementations
```

Platform-specific `expect/actual` is used sparingly — only where a true platform difference exists (e.g., file path resolution, KV store platform bridge initialization).

The C++ KV store lives alongside the KMP module:
```
cpp/
├── kvstore.h            # public API
├── kvstore.cpp          # mmap implementation, pthread_rwlock, typed read/write
├── kvstore_jni.cpp      # Android JNI bridge
└── kvstore_objc.mm      # iOS Objective-C++ wrapper
```

---

## 5. Audio Engine Specifics

### 5.1 iOS: AVPlayer + AVAudioEngine (Composable)
These are not mutually exclusive.

| API | Use For | Notes |
|---|---|---|
| `AVPlayer` / `AVQueuePlayer` | Streaming (music, radio, podcasts) | HLS natively, adaptive bitrate, network interruptions, KVO for buffer state |
| `AVAudioEngine` | Advanced DSP, pitch-corrected speed adjustment | Attach time-pitch nodes for audiobook/podcast speed; requires manual buffer management for network streams |
| Hybrid | EQ/normalization on top of streaming | `AVAudioEngine` can tap `AVPlayer` output — do this rather than rebuilding the networking layer |

The iOS audio layer observes the **playback state machine from the shared KMP module** and drives `AVPlayer`/`AVAudioEngine` accordingly. It pushes state changes back into the shared state flow.

### 5.2 Android: Media3 ExoPlayer + Oboe
| API | Use For | Notes |
|---|---|---|
| `Media3 ExoPlayer` | All streaming and playback | DRM, HLS, adaptive bitrate, `MediaSession`, `ConcatenatingMediaSource` for gapless |
| `Oboe` (C++) | Real-time DSP in the audio pipeline | AAudio/OpenSL ES wrapper, ~20ms latency; use for live EQ or normalization |

**Hardware offload caveat:** ExoPlayer's hardware-accelerated decoding only activates for specific codecs (typically AAC/MP3) on supported chipsets. Do not assume it applies universally — validate per target device profile.

Same principle as iOS: the Android audio layer is driven by the shared state machine, and pushes state back up.

---

## 6. Domain-Specific Requirements

### 6.1 Podcasts
- RSS ingestion via the shared KMP parser on `Dispatchers.IO`
- Episode state tracked in SQLDelight (played, downloaded, publication date, priority)
- Smart Playlist queries generated by the shared rule engine and executed against SQLDelight — never pull full episode lists into memory for manual filtering

### 6.2 Audiobooks
- Chapter metadata extracted platform-side: `AVAsset` (iOS) or `MediaMetadata` via ExoPlayer (Android) from ID3/QuickTime timed metadata — then passed into shared domain models
- Resume position written to the custom KV store **every second** during active playback — fire-and-forget, no blocking
- Speed adjustment with pitch correction: time-pitch node (iOS) / `PlaybackParameters` with pitch correction (Android) — triggered by shared state
- On crash or OS termination: shared module restores resume position from KV store immediately; platform layer initializes audio engine to that position before first render

### 6.3 Music Streaming
- Gapless playback: `AVQueuePlayer` (iOS) / `ConcatenatingMediaSource` in ExoPlayer (Android)
- Queue managed by the shared state machine — platform audio layer reads queue state, it does not own it

### 6.4 Live Radio
- HLS and Icecast supported
- Buffer strategy on reconnect must be explicitly configured — do not rely on player defaults
- Android: tune `DefaultLoadControl` parameters deliberately; defaults are not optimized for live streams
- iOS: monitor `AVPlayerItem.isPlaybackLikelyToKeepUp` for reconnection UX
- Live edge vs. delayed playback: decide explicitly per stream type and encode the decision in shared config, not in platform code

---

## 7. Smart Playlists

Smart Playlists are **dynamic SQL queries**, not static arrays of IDs. The rule engine lives entirely in the shared KMP module.

### 7.1 Schema (normalized, defined in SQLDelight)
Tables: `Artists`, `Albums`, `Tracks`, `Podcasts`, `Playlist_Rules`
Junction: `Playlist_Tracks` (many-to-many)

Store all timestamps as **UTC Unix epoch integers** — not formatted strings, not local time. SQLite's `datetime('now')` operates in UTC; comparisons against locally-stored times produce off-by-hours bugs.

### 7.2 Example Dynamic Query
```sql
SELECT Tracks.id, Tracks.title, Tracks.url, Podcasts.priority
FROM Tracks
JOIN Podcasts ON Tracks.podcast_id = Podcasts.id
WHERE Tracks.media_type = 'podcast'
  AND Tracks.play_count = 0
  AND Tracks.is_downloaded = 1
  AND Tracks.publication_date >= strftime('%s', 'now', '-2 days')
ORDER BY Podcasts.priority DESC, Tracks.publication_date ASC;
```

### 7.3 Shuffle
Apply **Fisher-Yates (Knuth) shuffle** to the resulting ID array in the shared module. O(n), mathematically uniform, non-repeating. Do not use `ORDER BY RANDOM()` in SQLite for large result sets.

---

## 8. What We Deliberately Avoid

| Thing | Why |
|---|---|
| Split audio engines per format | Audio focus collisions, zombie processes, duplicated business logic |
| Flutter / React Native for UI | Skia/Impeller overhead, JS VM, bridge latency |
| Full C++ shared core for business logic | KMP handles business logic; C++ is scoped specifically to the KV store and optionally DSP |
| `UserDefaults` / `SharedPreferences` for frequent writes | Slow, risky, wrong tool |
| `Room` or `CoreData` as primary DB | SQLDelight in KMP replaces both; no reason to maintain two separate schemas |
| DOM XML parsers for RSS | Full tree in memory, OOM risk on large feeds |
| Plain `VStack` / `Column` for lists | No virtualization, destroys performance at scale |
| `ORDER BY RANDOM()` for shuffle | Inefficient on large sets; use Fisher-Yates on the ID array |
| Assuming hardware offload decoding on all devices | Codec/chipset dependent — verify per target |
| Business logic written platform-side | Doubles the work and creates drift; if it doesn't touch a platform API, it goes in KMP |

---

## 9. Key Dependencies

| Purpose | Library | Where |
|---|---|---|
| Database | SQLDelight | Shared KMP |
| Networking | Ktor | Shared KMP |
| Serialization | kotlinx.serialization | Shared KMP |
| Async | kotlinx.coroutines | Shared KMP |
| Key-value store | Custom C++ mmap (owned) | KMP expect/actual + platform bridge |
| Dependency injection | Koin | Shared KMP |
| iOS audio | AVPlayer / AVAudioEngine | iOS only |
| Android audio | Media3 ExoPlayer | Android only |
| Android DSP | Oboe (C++) | Android only, if needed |

---

## 10. Resolved Decisions

- [x] **KMP for all business logic** — Kotlin shared module; C++ scoped to KV store only
- [x] **SQLDelight over Room + CoreData** — single schema, KMP-native, type-safe
- [x] **Custom C++ mmap KV store over MMKV** — identical performance, full ownership, no external dependency
- [x] **KV store: typed (Long, Float, String, Boolean)** — covers all current use cases
- [x] **KV store: readers-writer lock** — single writer (playback state machine), multiple readers (UI)
- [x] **KV store: encryption deferred to v2** — header format reserves space for it; no migration needed when added
- [x] **UTC Unix epoch integers for all timestamps** — no local time in the database, ever
- [x] **No shared UI framework** — native UI on both platforms, no exceptions

## 11. Open Questions

- [ ] **Swift Export readiness** — JetBrains' direct Swift interop is in active development. Monitor for production readiness; it will significantly clean up the iOS ↔ KMP boundary when it lands.
- [ ] **Oboe / C++ DSP at launch?** — If EQ or normalization is a launch feature, the C++ layer needs to be scoped now. If post-launch, defer.
- [ ] **Compose Multiplatform for UI later?** — Not in scope now, but the KMP foundation makes it a future option if the team wants UI sharing down the road.

---

*Last updated: 2026-02-21 — KV store decision: custom C++ mmap over MMKV*
*This document should be updated whenever a foundational architectural decision is made or reversed.*
