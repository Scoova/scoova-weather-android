package com.scoova.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Variable names you can request via `current` / `hourly` / `daily`. Subset of
 * open-meteo — pass `WeatherVar(rawValue = "...")` for anything not pre-defined.
 */
data class WeatherVar(val rawValue: String) {
    companion object {
        // current / hourly
        val temperature2m = WeatherVar("temperature_2m")
        val relativeHumidity2m = WeatherVar("relative_humidity_2m")
        val apparentTemperature = WeatherVar("apparent_temperature")
        val precipitation = WeatherVar("precipitation")
        val rain = WeatherVar("rain")
        val showers = WeatherVar("showers")
        val snowfall = WeatherVar("snowfall")
        val cloudCover = WeatherVar("cloud_cover")
        val windSpeed10m = WeatherVar("wind_speed_10m")
        val windDirection10m = WeatherVar("wind_direction_10m")
        val windGusts10m = WeatherVar("wind_gusts_10m")
        val weatherCode = WeatherVar("weather_code")
        val pressureMsl = WeatherVar("pressure_msl")
        val visibility = WeatherVar("visibility")
        val uvIndex = WeatherVar("uv_index")
        val isDay = WeatherVar("is_day")

        // daily
        val temperature2mMax = WeatherVar("temperature_2m_max")
        val temperature2mMin = WeatherVar("temperature_2m_min")
        val precipitationSum = WeatherVar("precipitation_sum")
        val precipitationHours = WeatherVar("precipitation_hours")
        val windSpeed10mMax = WeatherVar("wind_speed_10m_max")
        val sunrise = WeatherVar("sunrise")
        val sunset = WeatherVar("sunset")
        val uvIndexMax = WeatherVar("uv_index_max")
    }
}

enum class WindSpeedUnit(val wire: String) { KMH("kmh"), MS("ms"), MPH("mph"), KN("kn") }
enum class TemperatureUnit(val wire: String) { CELSIUS("celsius"), FAHRENHEIT("fahrenheit") }
enum class PrecipitationUnit(val wire: String) { MM("mm"), INCH("inch") }

data class ForecastResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val current: JSONObject?,
    val hourly: JSONObject?,
    val daily: JSONObject?,
    val currentUnits: JSONObject?,
    val hourlyUnits: JSONObject?,
    val dailyUnits: JSONObject?,
    val raw: JSONObject,
) {
    companion object {
        fun fromJson(j: JSONObject) = ForecastResponse(
            latitude = j.optDouble("latitude", 0.0),
            longitude = j.optDouble("longitude", 0.0),
            timezone = j.optString("timezone", "GMT"),
            current = j.optJSONObject("current"),
            hourly = j.optJSONObject("hourly"),
            daily = j.optJSONObject("daily"),
            currentUnits = j.optJSONObject("current_units"),
            hourlyUnits = j.optJSONObject("hourly_units"),
            dailyUnits = j.optJSONObject("daily_units"),
            raw = j,
        )
    }
}

sealed class WeatherException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Http(val statusCode: Int, body: String) : WeatherException("HTTP $statusCode: ${body.take(200)}")
    class Decode(message: String) : WeatherException(message)
    class Transport(cause: Throwable) : WeatherException(cause.message ?: "transport error", cause)
}

enum class WeatherCondition { CLEAR, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, THUNDERSTORM, UNKNOWN }

fun decodeWeatherCode(code: Int?): WeatherCondition {
    if (code == null) return WeatherCondition.UNKNOWN
    return when {
        code == 0 -> WeatherCondition.CLEAR
        code in 1..3 -> WeatherCondition.CLOUDY
        code == 45 || code == 48 -> WeatherCondition.FOG
        code in 51..57 -> WeatherCondition.DRIZZLE
        code in 61..67 || code in 80..82 -> WeatherCondition.RAIN
        code in 71..77 || code == 85 || code == 86 -> WeatherCondition.SNOW
        code in 95..99 -> WeatherCondition.THUNDERSTORM
        else -> WeatherCondition.UNKNOWN
    }
}

/** Pluggable HTTP fetcher: takes (url, headers), returns (statusCode, body). */
typealias WeatherHttp = suspend (url: String, headers: Map<String, String>) -> Pair<Int, String>

/**
 * Open-meteo compatible client for `weather.scoo-va.info`.
 *
 * Point [baseUrl] at the gateway (`https://api.scoo-va.info/v1/weather`) and
 * pass [apiKey] for key-enforced calls. Reads `SCOOVA_API_KEY` from the
 * environment when [apiKey] is null.
 *
 * [locale] sets the default locale (BCP-47 — `en`, `en-US`, `fr`, `es`,
 * `de`, `it`, `pt-BR`, `nl`, `ar`, `ar-EG`, `ar-SA`, plus regional
 * variants). It's sent as both `?locale=` and `Accept-Language`. Per-call
 * `locale` overrides the client default.
 */
class WeatherClient(
    baseUrl: String = "https://weather.scoo-va.info",
    apiKey: String? = null,
    private val locale: String? = null,
    private val timeoutMs: Int = 30_000,
    /** Override the HTTP layer in tests; defaults to plain HttpURLConnection. */
    private val http: WeatherHttp? = null,
) {
    private val baseUrl: String = baseUrl.trimEnd('/')
    private val apiKey: String? = apiKey ?: System.getenv("SCOOVA_API_KEY")

    suspend fun current(
        lat: Double,
        lon: Double,
        vars: List<WeatherVar> = defaultCurrent,
        locale: String? = null,
    ): ForecastResponse = forecast(lat, lon, current = vars, forecastDays = 1, locale = locale)

    suspend fun hourly(
        lat: Double,
        lon: Double,
        vars: List<WeatherVar> = defaultHourly,
        days: Int = 7,
        locale: String? = null,
    ): ForecastResponse = forecast(lat, lon, hourly = vars, forecastDays = days, locale = locale)

    suspend fun daily(
        lat: Double,
        lon: Double,
        vars: List<WeatherVar> = defaultDaily,
        days: Int = 7,
        locale: String? = null,
    ): ForecastResponse = forecast(lat, lon, daily = vars, forecastDays = days, locale = locale)

    suspend fun forecast(
        lat: Double,
        lon: Double,
        current: List<WeatherVar>? = null,
        hourly: List<WeatherVar>? = null,
        daily: List<WeatherVar>? = null,
        timezone: String = "auto",
        forecastDays: Int? = null,
        pastDays: Int? = null,
        windSpeedUnit: WindSpeedUnit? = null,
        temperatureUnit: TemperatureUnit? = null,
        precipitationUnit: PrecipitationUnit? = null,
        /** Per-call locale override; overrides the client-level [locale]. */
        locale: String? = null,
    ): ForecastResponse {
        val effectiveLocale = locale ?: this.locale
        val params = LinkedHashMap<String, String>()
        params["latitude"] = lat.toString()
        params["longitude"] = lon.toString()
        params["timezone"] = timezone
        current?.takeIf { it.isNotEmpty() }?.let { params["current"] = it.joinToString(",") { v -> v.rawValue } }
        hourly?.takeIf { it.isNotEmpty() }?.let { params["hourly"] = it.joinToString(",") { v -> v.rawValue } }
        daily?.takeIf { it.isNotEmpty() }?.let { params["daily"] = it.joinToString(",") { v -> v.rawValue } }
        forecastDays?.let { params["forecast_days"] = it.toString() }
        pastDays?.let { params["past_days"] = it.toString() }
        windSpeedUnit?.let { params["wind_speed_unit"] = it.wire }
        temperatureUnit?.let { params["temperature_unit"] = it.wire }
        precipitationUnit?.let { params["precipitation_unit"] = it.wire }
        effectiveLocale?.let { params["locale"] = it }

        val json = getJson("/v1/forecast", params, effectiveLocale)
        return ForecastResponse.fromJson(json)
    }

    suspend fun raw(
        path: String,
        params: Map<String, String> = emptyMap(),
        locale: String? = null,
    ): JSONObject = getJson(path, params, locale ?: this.locale)

    private val defaultHttp: WeatherHttp = { url, headers ->
        withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
            }
            try {
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
                code to body
            } finally {
                conn.disconnect()
            }
        }
    }

    private suspend fun getJson(
        path: String,
        params: Map<String, String>,
        callLocale: String?,
    ): JSONObject {
        val qs = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, Charsets.UTF_8)}=${URLEncoder.encode(v, Charsets.UTF_8)}"
        }
        val url = if (qs.isEmpty()) "$baseUrl$path" else "$baseUrl$path?$qs"
        val headers = buildMap {
            apiKey?.let { put("X-API-Key", it) }
            callLocale?.let { put("Accept-Language", it) }
        }
        val (code, body) = try {
            (http ?: defaultHttp).invoke(url, headers)
        } catch (t: Throwable) {
            throw WeatherException.Transport(t)
        }
        if (code !in 200..299) throw WeatherException.Http(code, body)
        return try {
            JSONObject(body)
        } catch (t: Throwable) {
            throw WeatherException.Decode("Invalid JSON: ${t.message}")
        }
    }

    companion object {
        val defaultCurrent = listOf(
            WeatherVar.temperature2m,
            WeatherVar.relativeHumidity2m,
            WeatherVar.apparentTemperature,
            WeatherVar.precipitation,
            WeatherVar.windSpeed10m,
            WeatherVar.windDirection10m,
            WeatherVar.weatherCode,
            WeatherVar.isDay,
        )
        val defaultHourly = listOf(
            WeatherVar.temperature2m,
            WeatherVar.precipitation,
            WeatherVar.windSpeed10m,
            WeatherVar.weatherCode,
        )
        val defaultDaily = listOf(
            WeatherVar.temperature2mMax,
            WeatherVar.temperature2mMin,
            WeatherVar.precipitationSum,
            WeatherVar.windSpeed10mMax,
            WeatherVar.weatherCode,
            WeatherVar.sunrise,
            WeatherVar.sunset,
        )
    }
}
