package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.database.CachedWeather
import com.example.data.database.FavoriteCity
import com.example.data.database.WeatherDatabase
import com.example.data.network.CurrentWeatherResponse
import com.example.data.network.ForecastResponse
import com.example.data.repository.WeatherRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface WeatherUiState {
    object Loading : WeatherUiState
    data class Success(
        val city: String,
        val current: CurrentWeatherResponse,
        val forecast: ForecastResponse,
        val isFavorite: Boolean,
        val isOffline: Boolean = false
    ) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WeatherDatabase.getDatabase(application)
    private val repository = WeatherRepository(db.weatherDao())

    // UI state
    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    // Favorite cities from DB
    val favorites: StateFlow<List<FavoriteCity>> = repository.favoriteCities
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active searched city name in UI
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        // Prepopulate database with default caches so the app is instantly usable offline or without key!
        viewModelScope.launch {
            try {
                if (db.weatherDao().getCachedWeather("Paris") == null) {
                    db.weatherDao().insertCachedWeather(
                        CachedWeather("Paris", 18.5, 14.0, 22.0, 65, 1015.0, 4.2, 800, "Clear", "ciel dégagé", "01d")
                    )
                }
                if (db.weatherDao().getCachedWeather("Lyon") == null) {
                    db.weatherDao().insertCachedWeather(
                        CachedWeather("Lyon", 20.2, 16.0, 24.0, 58, 1013.0, 3.8, 801, "Clouds", "quelques nuages", "02d")
                    )
                }
                if (db.weatherDao().getCachedWeather("Marseille") == null) {
                    db.weatherDao().insertCachedWeather(
                        CachedWeather("Marseille", 24.0, 19.0, 28.0, 45, 1012.0, 8.5, 800, "Clear", "ciel dégagé", "01d")
                    )
                }
                if (db.weatherDao().getCachedWeather("Toulouse") == null) {
                    db.weatherDao().insertCachedWeather(
                        CachedWeather("Toulouse", 19.0, 15.0, 22.5, 72, 1014.0, 5.1, 500, "Rain", "pluie modérée", "10d")
                    )
                }
                // Prepopulate standard favorites
                repository.addFavorite("Paris")
                repository.addFavorite("Lyon")
                repository.addFavorite("Marseille")
                repository.addFavorite("Toulouse")
            } catch (e: Exception) {
                Log.e("WeatherViewModel", "Failed to prepopulate DB", e)
            }
            loadWeather("Paris")
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun loadWeather(city: String) {
        if (city.trim().isEmpty()) return
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            
            val isFav = checkIsFavorite(city)

            // Check API Key
            val apiKey = BuildConfig.OPENWEATHER_API_KEY
            if (apiKey.isEmpty() || apiKey == "YOUR_OPENWEATHER_API_KEY") {
                val offlineData = repository.getLocalCachedWeather(city)
                if (offlineData != null) {
                    _uiState.value = buildSuccessFromCache(offlineData, isFavorite = isFav)
                    return@launch
                }
                _uiState.value = WeatherUiState.Error(
                    "Aucune clé API configurée ou clé de démonstration détectée.\n\nVeuillez ajouter votre clé OpenWeatherMap dans le panneau des Secrets de AI Studio sous la clé : 'OPENWEATHER_API_KEY'.\n\nEn attendant, vous pouvez rechercher 'Paris' pour voir un aperçu en cache."
                )
                return@launch
            }

            try {
                val current = repository.getCurrentWeather(city, apiKey)
                val forecast = repository.getForecast(city, apiKey)
                val finalFav = checkIsFavorite(current.name)

                _uiState.value = WeatherUiState.Success(
                    city = current.name,
                    current = current,
                    forecast = forecast,
                    isFavorite = finalFav,
                    isOffline = false
                )
            } catch (e: Exception) {
                Log.e("WeatherViewModel", "Error fetching weather", e)
                val cached = repository.getLocalCachedWeather(city)
                if (cached != null) {
                    _uiState.value = buildSuccessFromCache(cached, isFavorite = isFav)
                } else {
                    _uiState.value = WeatherUiState.Error(
                        "Impossible de charger la météo pour '$city'. Vérifiez votre connexion Internet et assurez-vous que la clé API configurée est valide.\n\nDétails : ${e.localizedMessage ?: e.message}"
                    )
                }
            }
        }
    }

    fun toggleFavorite(cityName: String) {
        viewModelScope.launch {
            val isFav = checkIsFavorite(cityName)
            if (isFav) {
                repository.removeFavorite(cityName)
            } else {
                repository.addFavorite(cityName)
            }
            // Update UI state with correct favorite state
            val currentVal = _uiState.value
            if (currentVal is WeatherUiState.Success && currentVal.city.lowercase() == cityName.lowercase()) {
                _uiState.value = currentVal.copy(isFavorite = !isFav)
            }
        }
    }

    private fun checkIsFavorite(cityName: String): Boolean {
        return favorites.value.any { it.name.lowercase() == cityName.lowercase() }
    }

    private fun buildSuccessFromCache(cached: CachedWeather, isFavorite: Boolean): WeatherUiState.Success {
        val currentMock = CurrentWeatherResponse(
            name = cached.cityName,
            dt = cached.timestamp / 1000,
            main = com.example.data.network.MainData(
                temp = cached.temp,
                feelsLike = cached.temp,
                tempMin = cached.tempMin,
                tempMax = cached.tempMax,
                pressure = cached.pressure,
                humidity = cached.humidity
            ),
            weather = listOf(
                com.example.data.network.WeatherDescription(
                    id = cached.condId,
                    main = cached.condMain,
                    description = cached.condDesc,
                    icon = cached.condIcon
                )
            ),
            wind = com.example.data.network.WindData(speed = cached.windSpeed, deg = 0.0),
            sys = com.example.data.network.SysData(country = "FR", sunrise = null, sunset = null),
            cod = 200
        )

        val forecastMock = ForecastResponse(
            list = emptyList(),
            city = com.example.data.network.CityData(id = 0, name = cached.cityName, country = "FR")
        )

        return WeatherUiState.Success(
            city = cached.cityName,
            current = currentMock,
            forecast = forecastMock,
            isFavorite = isFavorite,
            isOffline = true
        )
    }
}
