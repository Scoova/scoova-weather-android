# Changelog

All notable changes to `info.scoo-va:scoova-weather` are documented here.

## 1.1.1 — 2026-05-25
- Default `baseUrl` switched from the retired `https://weather.scoo-va.info` subdomain to the central gateway at `https://api.scoo-va.info/api/v1/weather`. Callers who explicitly set `baseUrl` are unaffected. The old subdomain returns `ENDPOINT_RETIRED`.

## 1.1.0 — 2026-05-25

- **New:** built-in `locale` option on the client and per-call on every
  `current` / `hourly` / `daily` / `forecast` / `raw` invocation. Accepts
  BCP-47 codes (`en`, `en-US`, `fr`, `es`, `de`, `it`, `pt-BR`, `nl`,
  `ar`, `ar-EG`, `ar-SA`, plus regional variants). Sent as both
  `?locale=` and `Accept-Language`. Per-call value wins.
- **New:** built-in `apiKey` constructor parameter; auto-attaches
  `X-API-Key` to every request. Falls back to the `SCOOVA_API_KEY`
  environment variable when no value is passed.
- Verified endpoint surface against the live gateway: `current()`,
  `hourly()`, `daily()`, `forecast()`, and `raw()` all hit
  `/v1/forecast`.
- Publishing: Monitor-style POM + GitHub Packages + Maven Central
  targets, pointed at `Scoova/scoova-weather-android`.
- License: switched from MIT to Apache-2.0 to match the rest of the
  Scoova platform SDKs.

## 1.0.0 — 2026-04-12

- Initial release.
