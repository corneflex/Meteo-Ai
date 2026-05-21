package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CurrentWeatherResponse(
    val name: String,
    val dt: Long,
    val main: MainData,
    val weather: List<WeatherDescription>,
    val wind: WindData,
    val sys: SysData,
    val cod: Int
)

@JsonClass(generateAdapter = true)
data class MainData(
    val temp: Double,
    @Json(name = "feels_like") val feelsLike: Double,
    @Json(name = "temp_min") val tempMin: Double,
    @Json(name = "temp_max") val tempMax: Double,
    val pressure: Double,
    val humidity: Int
)

@JsonClass(generateAdapter = true)
data class WeatherDescription(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String
)

@JsonClass(generateAdapter = true)
data class WindData(
    val speed: Double,
    val deg: Double
)

@JsonClass(generateAdapter = true)
data class SysData(
    val country: String?,
    val sunrise: Long?,
    val sunset: Long?
)
