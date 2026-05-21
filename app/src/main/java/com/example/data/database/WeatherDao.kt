package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {
    @Query("SELECT * FROM favorite_cities ORDER BY dateAdded DESC")
    fun getFavoriteCities(): Flow<List<FavoriteCity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteCity(city: FavoriteCity)

    @Delete
    suspend fun deleteFavoriteCity(city: FavoriteCity)

    @Query("SELECT * FROM cached_weather WHERE LOWER(cityName) = LOWER(:cityName)")
    suspend fun getCachedWeather(cityName: String): CachedWeather?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedWeather(cached: CachedWeather)
}
