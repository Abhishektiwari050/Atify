# Room 3.0 Architecture & Migration Plan for Atify

## 1. Executive Summary
Currently, Atify relies on Android `SharedPreferences` with custom string delimiters or JSON serialization (`Gson`) across multiple preference files:
- `LikedSongs` / `LikedAlbums` (comma-separated ID sets & serialized JSON models)
- `ListeningHistory` (serialized JSON history list)
- `Downloads` (download file records and IDs)
- `RecentItems` (recent searches)
- `StreamCache` (YouTube playback URLs and expiry timestamps)

While functional for small catalogs, this approach creates I/O bottlenecks when parsing large collections on the main thread and lacks query capabilities (indexing, relational joins, complex filtering).

Per project architectural guidelines (`AGENTS.md`), we intentionally skip Room 2.x to avoid legacy annotation processing dependencies and target **Room 3.0** (`androidx.room3`), which is KSP-only, Kotlin-multiplatform-ready, and brings zero-reflection Kotlin Symbol Processing.

---

## 2. Proposed Database Schema

### Table: `liked_songs`
```sql
CREATE TABLE liked_songs (
    id INTEGER PRIMARY KEY,
    spotify_id TEXT NOT NULL,
    title TEXT NOT NULL,
    singer TEXT NOT NULL,
    album TEXT NOT NULL,
    cover_uri TEXT NOT NULL,
    url TEXT NOT NULL,
    added_at INTEGER NOT NULL
);
CREATE INDEX idx_liked_songs_spotify_id ON liked_songs(spotify_id);
CREATE INDEX idx_liked_songs_added_at ON liked_songs(added_at DESC);
```

### Table: `liked_albums`
```sql
CREATE TABLE liked_albums (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    artists TEXT NOT NULL,
    cover_uri TEXT NOT NULL,
    time TEXT NOT NULL,
    spotify_id TEXT NOT NULL,
    added_at INTEGER NOT NULL
);
CREATE INDEX idx_liked_albums_name ON liked_albums(name);
```

### Table: `listening_history`
```sql
CREATE TABLE listening_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    song_id INTEGER NOT NULL,
    spotify_id TEXT NOT NULL,
    title TEXT NOT NULL,
    artist TEXT NOT NULL,
    album TEXT NOT NULL,
    cover_uri TEXT NOT NULL,
    played_at INTEGER NOT NULL,
    play_duration_ms INTEGER NOT NULL
);
CREATE INDEX idx_history_played_at ON listening_history(played_at DESC);
```

### Table: `downloads`
```sql
CREATE TABLE downloads (
    song_id INTEGER PRIMARY KEY,
    spotify_id TEXT NOT NULL,
    title TEXT NOT NULL,
    artist TEXT NOT NULL,
    album TEXT NOT NULL,
    cover_uri TEXT NOT NULL,
    file_path TEXT NOT NULL,
    content_uri TEXT,
    size_bytes INTEGER NOT NULL,
    downloaded_at INTEGER NOT NULL,
    audio_format TEXT NOT NULL -- 'FLAC', 'OPUS', 'M4A'
);
```

### Table: `stream_cache`
```sql
CREATE TABLE stream_cache (
    query_key TEXT PRIMARY KEY, -- e.g., 'spotify:track:xxx' or 'artist - title'
    youtube_id TEXT NOT NULL,
    stream_url TEXT NOT NULL,
    itag INTEGER NOT NULL,
    cached_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL
);
CREATE INDEX idx_stream_cache_expires ON stream_cache(expires_at);
```

---

## 3. Migration Strategy from SharedPreferences

### Step 1: One-Time Data Importer Worker
On first startup with Room 3.0 enabled, an idempotent `SharedPreferencesMigrationHelper` reads existing SharedPreferences:
1. `getLikedSongs(context)` -> `liked_songs` table with `added_at = System.currentTimeMillis()`.
2. `getLikedAlbums(context)` -> `liked_albums` table.
3. `getHistory(context)` -> `listening_history` table.
4. `getDownloads(context)` -> `downloads` table.
5. Set `SharedPreferences.putBoolean("room_migrated_v1", true)`.

### Step 2: Repository Layer Abstraction
Replace direct preference calls with `AppRepository` flows:
- `repository.observeLikedSongs(): Flow<List<SongsModel>>`
- `repository.toggleLikeSong(song: SongsModel)`
- `repository.recordPlayHistory(song: SongsModel, durationMs: Long)`

---

## 4. Dependencies to Add (Upon Room 3.0 Stable Release)

In `gradle/libs.versions.toml`:
```toml
[versions]
room3 = "3.0.0"

[libraries]
room3-runtime = { group = "androidx.room3", name = "room-runtime", version.ref = "room3" }
room3-compiler = { group = "androidx.room3", name = "room-compiler", version.ref = "room3" }
room3-ktx = { group = "androidx.room3", name = "room-ktx", version.ref = "room3" }
```

In `app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(libs.room3.runtime)
    implementation(libs.room3.ktx)
    ksp(libs.room3.compiler)
}
```
