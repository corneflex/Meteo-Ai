package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_weather")
data class CachedWeather(
    @PrimaryKey val cityName: String,
    val temp: Double,
    val tempMin: Double,
    val tempMax: Double,
    val humidity: Int,
    val pressure: Double,
    val windSpeed: Double,
    val condId: Int,
    val condMain: String,
    val condDesc: String,
    val condIcon: String,
    val timestamp: Long = System.currentTimeMillis()
)
