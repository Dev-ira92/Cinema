# Cinema

Android (Kotlin + Jetpack Compose) client for the VidAPI catalog and player.

- Catalog: `https://vidapi.ru/movies|tvshows/latest/page-N.json` (paginated, 24/page)
- Player: `https://vaplayer.ru/embed/...` loaded in a WebView
- Search: unofficial IMDb suggestion endpoint (no API key)
- Subtitles: handled by the player (`ds_lang` parameter)

No API keys are required to build.
