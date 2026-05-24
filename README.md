# scoova-weather-android (Kotlin / JVM)

Open-meteo compatible Kotlin client for `weather.scoo-va.info`. Plain JVM —
runs on Android (API 21+) and any JVM ≥ 17 without the Android platform.

## Install

Maven artifact: `info.scoo-va:scoova-weather:1.1.0`

```kotlin
dependencies {
    implementation("info.scoo-va:scoova-weather:1.1.0")
}
```

## Usage

```kotlin
import com.scoova.weather.*
import kotlinx.coroutines.runBlocking

// Unauthenticated against the raw subdomain.
val client = WeatherClient()

// Authenticated via the API gateway, French copy.
val gateway = WeatherClient(
    baseUrl = "https://api.scoo-va.info/v1/weather",
    apiKey  = System.getenv("SCOOVA_API_KEY") ?: "demo",
    locale  = "fr",
)

val now = gateway.current(lat = 30.04, lon = 31.24)
val condition = decodeWeatherCode(now.current?.optInt("weather_code"))

val daily = gateway.daily(
    lat = 30.04, lon = 31.24,
    vars = listOf(
        WeatherVar.temperature2mMax,
        WeatherVar.temperature2mMin,
        WeatherVar.precipitationSum,
    ),
    days = 5,
)

// Per-call locale overrides the client default.
val arabic = gateway.current(lat = 30.04, lon = 31.24, locale = "ar-EG")
```

## Locale

`locale` accepts BCP-47 codes: `en`, `en-US`, `en-GB`, `fr`, `es`, `de`, `it`,
`pt-BR`, `nl`, `ar`, `ar-EG`, `ar-SA`, plus regional variants. Sent as both
`?locale=` query string and `Accept-Language` header. Unsupported codes fall
back to `en` server-side.

## Tests

```sh
gradle test
```

## License

Apache-2.0.
