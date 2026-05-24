package com.scoova.weather

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeatherCodeTest {
    @Test fun mapsWmoToConditions() {
        assertEquals(WeatherCondition.CLEAR, decodeWeatherCode(0))
        assertEquals(WeatherCondition.CLOUDY, decodeWeatherCode(2))
        assertEquals(WeatherCondition.FOG, decodeWeatherCode(45))
        assertEquals(WeatherCondition.DRIZZLE, decodeWeatherCode(53))
        assertEquals(WeatherCondition.RAIN, decodeWeatherCode(63))
        assertEquals(WeatherCondition.RAIN, decodeWeatherCode(80))
        assertEquals(WeatherCondition.SNOW, decodeWeatherCode(73))
        assertEquals(WeatherCondition.THUNDERSTORM, decodeWeatherCode(96))
        assertEquals(WeatherCondition.UNKNOWN, decodeWeatherCode(null))
        assertEquals(WeatherCondition.UNKNOWN, decodeWeatherCode(999))
    }
}

class WeatherClientTest {
    @Test fun buildsForecastUrlWithSaneDefaults() = runTest {
        var capturedUrl = ""
        var capturedHeaders: Map<String, String> = emptyMap()
        val client = WeatherClient(baseUrl = "https://example.test", http = { url, headers ->
            capturedUrl = url
            capturedHeaders = headers
            200 to """{"latitude":30.0625,"longitude":31.25,"timezone":"Africa/Cairo",
                       "current":{"time":"2026-05-04T17:00","interval":900,"temperature_2m":24.1}}"""
        })

        val res = client.current(30.04, 31.24)
        assertTrue(capturedUrl.startsWith("https://example.test/v1/forecast?"), "URL was: $capturedUrl")
        assertTrue(capturedUrl.contains("latitude=30.04"))
        assertTrue(capturedUrl.contains("longitude=31.24"))
        assertTrue(capturedUrl.contains("current=temperature_2m"))
        assertTrue(capturedUrl.contains("timezone=auto"))
        assertNull(capturedHeaders["X-API-Key"])
        assertNull(capturedHeaders["Accept-Language"])
        assertEquals("Africa/Cairo", res.timezone)
        assertEquals(24.1, res.current?.optDouble("temperature_2m"))
    }

    @Test fun forwardsForecastDaysAndUnits() = runTest {
        var capturedUrl = ""
        val client = WeatherClient(baseUrl = "https://example.test", http = { url, _ ->
            capturedUrl = url
            200 to """{"latitude":0,"longitude":0,"timezone":"GMT"}"""
        })
        client.forecast(
            lat = 30.0, lon = 31.0,
            hourly = listOf(WeatherVar.temperature2m, WeatherVar.windSpeed10m),
            daily = listOf(WeatherVar.temperature2mMax),
            forecastDays = 3,
            pastDays = 1,
            temperatureUnit = TemperatureUnit.FAHRENHEIT,
            windSpeedUnit = WindSpeedUnit.MS,
        )
        assertTrue(capturedUrl.contains("hourly=temperature_2m%2Cwind_speed_10m"), capturedUrl)
        assertTrue(capturedUrl.contains("daily=temperature_2m_max"), capturedUrl)
        assertTrue(capturedUrl.contains("forecast_days=3"), capturedUrl)
        assertTrue(capturedUrl.contains("past_days=1"), capturedUrl)
        assertTrue(capturedUrl.contains("temperature_unit=fahrenheit"), capturedUrl)
        assertTrue(capturedUrl.contains("wind_speed_unit=ms"), capturedUrl)
    }

    @Test fun throwsOnNon2xx() = runTest {
        val client = WeatherClient(baseUrl = "https://example.test", http = { _, _ -> 502 to "boom" })
        val ex = assertFailsWith<WeatherException.Http> {
            client.current(30.0, 31.0)
        }
        assertEquals(502, ex.statusCode)
    }

    @Test fun attachesApiKeyAndLocaleHeaders() = runTest {
        var capturedUrl = ""
        var capturedHeaders: Map<String, String> = emptyMap()
        val client = WeatherClient(
            baseUrl = "https://api.scoo-va.info/v1/weather",
            apiKey = "sk_live_abc",
            locale = "fr",
            http = { url, headers ->
                capturedUrl = url
                capturedHeaders = headers
                200 to """{"latitude":0,"longitude":0,"timezone":"GMT"}"""
            },
        )
        client.current(30.0, 31.0)

        assertEquals("sk_live_abc", capturedHeaders["X-API-Key"])
        assertEquals("fr", capturedHeaders["Accept-Language"])
        assertTrue(capturedUrl.contains("locale=fr"), capturedUrl)
    }

    @Test fun perCallLocaleOverridesClientDefault() = runTest {
        var capturedUrl = ""
        var capturedHeaders: Map<String, String> = emptyMap()
        val client = WeatherClient(
            baseUrl = "https://example.test",
            locale = "en",
            http = { url, headers ->
                capturedUrl = url
                capturedHeaders = headers
                200 to """{"latitude":0,"longitude":0,"timezone":"GMT"}"""
            },
        )
        client.current(30.0, 31.0, locale = "ar-EG")

        assertEquals("ar-EG", capturedHeaders["Accept-Language"])
        assertTrue(capturedUrl.contains("locale=ar-EG"), capturedUrl)
    }
}
