# CineHub for PS5

CineHub is a native PS5 homebrew media-center project built with SDL2, FFmpeg and libcurl. The current target is PS5 only.

## Current baseline

- SDL2 full-screen UI and DualSense/game-controller navigation.
- Local JSON movie library and poster loading.
- OMDb title search and metadata lookup (requires an OMDb API key).
- FFmpeg-based playback for direct media URLs supported by the linked FFmpeg build.
- HTTP range reader for media servers that support byte ranges.
- Optional source-resolver API and local source catalogue.
- TLS certificate verification enabled for metadata, artwork and playback requests.

This is an active development baseline, not a finished player. Subtitle selection, resume/history, advanced audio-track selection, robust source-provider plugins, and final on-console validation remain to be implemented.

## PS5 build

The GitHub Actions workflow downloads the PS5 Payload SDK and the pinned PacBrew v0.41.1 library bundle, then builds `cinehub.elf`. The build requires the PS5-targeted SDL2, SDL2_net, curl, FFmpeg and supporting static libraries from that bundle.

Run the workflow with **Actions → Build CineHub → Run workflow**. A successful CI build is not proof of runtime compatibility; test on the target console with the matching homebrew loader.

## Install/runtime data

The application data directory is `/data/homebrew/Cinema` (see `APP_DIR` in `src/main.c`). Place these files there as needed:

- `movies.json` — local movie catalogue.
- `omdb_key.txt` — your own OMDb API key, one line.
- `source_api.txt` — optional HTTPS resolver endpoint base URL (see `source_api.example.txt`).
- `sources.json` — optional local media-source overrides.
- `cacert.pem` — optional CA bundle if the PS5 environment has no usable system CA store.
- `font.ttf` — optional font file for full Unicode/Farsi glyph rendering (font shaping/RTL support is still being improved).

## Source resolver response format

The resolver is queried as `GET <base-url>/<imdb-id>`. It must return a JSON array. Each item has an `imdb_id` and a `sources` array. Only return media URLs that the operator is authorized to access and distribute. Example:

```json
[
  {
    "imdb_id": "tt0133093",
    "sources": [
      {
        "server": "Personal Media Server",
        "quality": "1080p",
        "url": "https://media.example.org/movies/the-matrix.mp4"
      }
    ]
  }
]
```

The current app does not bypass DRM, subscriptions, authentication, or access controls. The previous hard-coded third-party embed-site scraper has been removed; use a resolver for services you are licensed or otherwise authorized to use, or add your own direct URLs to `sources.json`.

## Local source catalogue

See `sources.example.json` for the expected format. Match sources to the exact IMDb ID to avoid confusing remakes or titles with the same name.

## Important limitations

- The app has not yet been validated on a physical PS5 in this development session.
- Playback requires a direct URL reachable by the console and a server compatible with the current HTTP Range reader; HLS and other protocols need separate runtime tests.
- 4K performance, hardware video decode, Persian subtitle rendering and long-duration playback need dedicated console testing.
- Do not use untrusted ELF or package files.
