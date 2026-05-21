package com.example.data.repository

import com.example.data.database.CachedWeather
import com.example.data.database.FavoriteCity
import com.example.data.database.WeatherDao
import com.example.data.network.CurrentWeatherResponse
import com.example.data.network.ForecastResponse
import com.example.data.network.RetrofitClient
import kotlinx.coroutines.flow.Flow

class WeatherRepository(private val weatherDao: WeatherDao) {

    val favoriteCities: Flow<List<FavoriteCity>> = weatherDao.getFavoriteCities()

    suspend fun getCurrentWeather(city: String, apiKey: String): CurrentWeatherResponse {
        val response = RetrofitClient.apiService.getCurrentWeather(city = city, apiKey = apiKey)
        // Cache weather data
        val cached = CachedWeather(
            cityName = response.name,
            temp = response.main.temp,
            tempMin = response.main.tempMin,
            tempMax = response.main.tempMax,
            humidity = response.main.humidity,
            pressure = response.main.pressure,
            windSpeed = response.wind.speed,
            condId = response.weather.firstOrNull()?.id ?: 800,
            condMain = response.weather.firstOrNull()?.main ?: "Clear",
            condDesc = response.weather.firstOrNull()?.description ?: "clair",
            condIcon = response.weather.firstOrNull()?.icon ?: "01d"
        )
        weatherDao.insertCachedWeather(cached)
        return response
    }

    suspend fun getLocalCachedWeather(city: String): CachedWeather? {
        return weatherDao.getCachedWeather(city)
    }

    suspend fun getForecast(city: String, apiKey: String): ForecastResponse {
        return RetrofitClient.apiService.getForecast(city = city, apiKey = apiKey)
    }

    suspend fun addFavorite(cityName: String) {
        val formattedCityName = cityName.trim()
        if (formattedCityName.isNotEmpty()) {
            weatherDao.insertFavoriteCity(FavoriteCity(name = formattedCityName))
        }
    }

    suspend fun removeFavorite(cityName: String) {
        val formattedCityName = cityName.trim()
        if (formattedCityName.isNotEmpty()) {
            weatherDao.deleteFavoriteCity(FavoriteCity(name = formattedCityName))
        }
    }
}
