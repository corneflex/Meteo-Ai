package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.network.CurrentWeatherResponse
import com.example.data.network.ForecastItem
import com.example.data.network.ForecastResponse
import com.example.ui.WeatherUiState
import com.example.ui.WeatherViewModel
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: WeatherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val favorites by viewModel.favorites.collectAsStateWithLifecycle()
                val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

                WeatherAppScreen(
                    uiState = uiState,
                    favorites = favorites,
                    searchQuery = searchQuery,
                    onSearchQueryChanged = { viewModel.updateSearchQuery(it) },
                    onSearchSubmit = { viewModel.loadWeather(it) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) }
                )
            }
        }
    }
}

// Data holder for aggregated day forecast
data class DayForecast(
    val dayName: String,
    val tempMax: Double,
    val tempMin: Double,
    val description: String,
    val icon: String
)

// Aggregate 5-day weather data
fun groupForecastByDay(items: List<ForecastItem>): List<DayForecast> {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val dayFormat = SimpleDateFormat("EEEE", Locale.FRANCE)
    
    val grouped = items.groupBy { 
        try {
            val rawStr = it.dtTxt
            val subStr = if (rawStr.length >= 10) rawStr.substring(0, 10) else rawStr
            val date = sdf.parse(subStr)
            if (date != null) dayFormat.format(date).replaceFirstChar { char -> char.uppercase() }
            else "Météo"
        } catch (e: Exception) {
            "Météo"
        }
    }
    
    return grouped.filterKeys { it != "Météo" }.map { (day, list) ->
        val maxTemp = list.maxOfOrNull { it.main.tempMax } ?: 0.0
        val minTemp = list.minOfOrNull { it.main.tempMin } ?: 0.0
        // Use mid-day item (typically 12:00 or index size / 2) to pick description & icon
        val sampleItem = list.getOrNull(list.size / 2) ?: list.first()
        
        DayForecast(
            dayName = day,
            tempMax = maxTemp,
            tempMin = minTemp,
            description = sampleItem.weather.firstOrNull()?.description ?: "météo",
            icon = sampleItem.weather.firstOrNull()?.icon ?: "01d"
        )
    }.take(5)
}

@Composable
fun WeatherAppScreen(
    uiState: WeatherUiState,
    favorites: List<com.example.data.database.FavoriteCity>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Dynamically choose warm golden or deep indigo ambient brushes corresponding to weather condition
    val currentThemeGradient = remember(uiState) {
        when (uiState) {
            is WeatherUiState.Success -> {
                val icon = uiState.current.weather.firstOrNull()?.icon ?: "01d"
                when {
                    icon.startsWith("01") -> { // Sunny / Clear
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFFFFB74D), Color(0xFFE65100))
                        )
                    }
                    icon.startsWith("09") || icon.startsWith("10") || icon.startsWith("11") -> { // Rain / Thunder
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF37474F), Color(0xFF102027))
                        )
                    }
                    icon.startsWith("02") || icon.startsWith("03") || icon.startsWith("04") -> { // Clouds
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF4FC3F7), Color(0xFF0277BD))
                        )
                    }
                    else -> { // Night, Default
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1E3A8A), Color(0xFF0F172A))
                        )
                    }
                }
            }
            else -> {
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1E3A8A), Color(0xFF0F172A))
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentThemeGradient)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Draw real-time procedurally animated particles corresponding to weather state
        AnimatedWeatherCanvas(uiState = uiState)

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // High-fidelity glassmorphism search toolbar
            WeatherSearchBar(
                query = searchQuery,
                onQueryChanged = onSearchQueryChanged,
                onSearch = {
                    onSearchSubmit(it)
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            )

            // Pinned favorites row
            if (favorites.isNotEmpty()) {
                FavoritesRow(
                    favorites = favorites,
                    onFavoriteClick = {
                        onSearchSubmit(it)
                        onSearchQueryChanged("")
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                )
            }

            // Main body area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (uiState) {
                    is WeatherUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Recherche des prévisions...",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    is WeatherUiState.Success -> {
                        SuccessWeatherLayout(
                            state = uiState,
                            onToggleFavorite = { onToggleFavorite(uiState.city) }
                        )
                    }

                    is WeatherUiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudOff,
                                        contentDescription = "Erreur",
                                        tint = Color.White,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = uiState.message,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 22.sp
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = { onSearchSubmit("Paris") },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White,
                                            contentColor = Color(0xFF1E3A8A)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Recharger Paris")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Security Warning for proto, displayed elegantly at the base of the viewport
            SecurityWarningFooter()
        }
    }
}

@Composable
fun AnimatedWeatherCanvas(uiState: WeatherUiState) {
    if (uiState !is WeatherUiState.Success) return
    val icon = uiState.current.weather.firstOrNull()?.icon ?: "01d"

    when {
        icon.startsWith("01") -> { // Sunny / Clear
            val infiniteTransition = rememberInfiniteTransition(label = "RayRotation")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(28000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotation"
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width * 0.85f
                val centerY = size.height * 0.15f
                rotate(rotation, pivot = Offset(centerX, centerY)) {
                    val rayCount = 8
                    val innerRadius = 35f
                    val outerRadius = 55f
                    for (i in 0 until rayCount) {
                        val angle = (i * (360f / rayCount)) * (Math.PI / 180f)
                        val startX = centerX + innerRadius * kotlin.math.cos(angle).toFloat()
                        val startY = centerY + innerRadius * kotlin.math.sin(angle).toFloat()
                        val endX = centerX + outerRadius * kotlin.math.cos(angle).toFloat()
                        val endY = centerY + outerRadius * kotlin.math.sin(angle).toFloat()
                        drawLine(
                            color = Color(0xFFFFD54F).copy(alpha = 0.4f),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = 6f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }

        icon.startsWith("09") || icon.startsWith("10") || icon.startsWith("11") -> { // Rainy / Thundercloud
            val infiniteTransition = rememberInfiniteTransition(label = "RainFlow")
            val progress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rain"
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val particleCount = 45
                for (i in 0 until particleCount) {
                    val x = (width * (i.toFloat() / particleCount + 0.17f * i)) % width
                    val strokeLen = 40f
                    val startY = ((height + strokeLen) * (progress + i * 0.022f)) % (height + strokeLen) - strokeLen
                    drawLine(
                        color = Color.White.copy(alpha = 0.3f),
                        start = Offset(x, startY),
                        end = Offset(x - 6f, startY + strokeLen),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        icon.startsWith("02") || icon.startsWith("03") || icon.startsWith("04") -> { // Clouds
            val infiniteTransition = rememberInfiniteTransition(label = "CloudDrift")
            val deltaX by infiniteTransition.animateFloat(
                initialValue = -160f,
                targetValue = 160f,
                animationSpec = infiniteRepeatable(
                    animation = tween(15000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "clouds"
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = 110f,
                    center = Offset(width * 0.15f + deltaX, height * 0.22f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = 180f,
                    center = Offset(width * 0.8f - deltaX, height * 0.13f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherSearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearch: (String) -> Unit
) {
    TextField(
        value = query,
        onValueChange = onQueryChanged,
        placeholder = { Text("Recherche une ville (ex : Lyon, Marseille...)", color = Color.White.copy(alpha = 0.7f)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Recherche", tint = Color.White) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.15f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.15f),
            disabledContainerColor = Color.White.copy(alpha = 0.1f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Color.White
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(
            onSearch = { if (query.trim().isNotEmpty()) onSearch(query) }
        ),
        shape = RoundedCornerShape(20.dp),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(56.dp)
    )
}

@Composable
fun FavoritesRow(
    favorites: List<com.example.data.database.FavoriteCity>,
    onFavoriteClick: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        items(favorites) { favorite ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable { onFavoriteClick(favorite.name) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = favorite.name,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun SuccessWeatherLayout(
    state: WeatherUiState.Success,
    onToggleFavorite: () -> Unit
) {
    val temp = state.current.main.temp.toInt()
    val condition = state.current.weather.firstOrNull()?.description?.replaceFirstChar { it.uppercase() } ?: "Inconnu"
    val wind = state.current.wind.speed.toInt()
    val humidity = state.current.main.humidity
    val pressure = state.current.main.pressure.toInt()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Hero Weather Metrics Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = "Ville actuelle",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = state.city,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                modifier = Modifier.widthIn(max = 180.dp)
                            )
                            if (state.isOffline) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Hors-ligne",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Favorite star button
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (state.isFavorite) Icons.Filled.Star else Icons.Filled.StarOutline,
                                contentDescription = "Favori",
                                tint = if (state.isFavorite) Color(0xFFFFD54F) else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Official high-res cloud image loaded from openweather
                        val iconCode = state.current.weather.firstOrNull()?.icon ?: "01d"
                        AsyncImage(
                            model = "https://openweathermap.org/img/wn/$iconCode@4x.png",
                            contentDescription = condition,
                            modifier = Modifier.size(100.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Text(
                                text = "$temp°C",
                                color = Color.White,
                                fontSize = 68.sp,
                                fontWeight = FontWeight.Light,
                                lineHeight = 68.sp
                            )
                            Text(
                                text = condition,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Secondary indicators (Humidity, Wind, Pressure)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        IndicatorItem(
                            icon = Icons.Outlined.WaterDrop,
                            label = "Humidité",
                            value = "$humidity %"
                        )
                        IndicatorItem(
                            icon = Icons.Outlined.Air,
                            label = "Vent",
                            value = "$wind km/h"
                        )
                        IndicatorItem(
                            icon = Icons.Outlined.Compress,
                            label = "Pression",
                            value = "$pressure hPa"
                        )
                    }
                }
            }
        }

        // 3-Hourly Horizontal Scrollable Card
        if (state.forecast.list.isNotEmpty()) {
            item {
                Text(
                    text = "Aujourd'hui (Toutes les 3h)",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 12.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(state.forecast.list.take(8)) { item ->
                        val timeStr = try {
                            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                            val date = inputFormat.parse(item.dtTxt)
                            if (date != null) {
                                outputFormat.format(date)
                            } else {
                                if (item.dtTxt.length >= 16) item.dtTxt.substring(11, 16) else item.dtTxt
                            }
                        } catch (e: Exception) {
                            if (item.dtTxt.length >= 16) item.dtTxt.substring(11, 16) else item.dtTxt
                        }

                        val hIcon = item.weather.firstOrNull()?.icon ?: "01d"
                        val hTemp = item.main.temp.toInt()
                        val hDesc = item.weather.firstOrNull()?.description ?: "météo"

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .width(74.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = timeStr,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                AsyncImage(
                                    model = "https://openweathermap.org/img/wn/$hIcon@2x.png",
                                    contentDescription = hDesc,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$hTemp°",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // 5-Day Weekly Outlook Cards
            val filteredDayForecasts = groupForecastByDay(state.forecast.list)
            if (filteredDayForecasts.isNotEmpty()) {
                item {
                    Text(
                        text = "Prévisions sur 5 jours",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 12.dp)
                    )
                }

                items(filteredDayForecasts) { forecast ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = forecast.dayName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(100.dp)
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            Text(
                                text = forecast.description.replaceFirstChar { it.uppercase() },
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .widthIn(max = 100.dp)
                                    .padding(end = 8.dp),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1
                            )

                            AsyncImage(
                                model = "https://openweathermap.org/img/wn/${forecast.icon}@2x.png",
                                contentDescription = forecast.description,
                                modifier = Modifier.size(32.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = "${forecast.tempMin.toInt()}°",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                                modifier = Modifier.width(26.dp),
                                textAlign = TextAlign.End
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "${forecast.tempMax.toInt()}°",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(26.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IndicatorItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp
        )
    }
}

@Composable
fun SecurityWarningFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Security Warning : I have included your API keys in the generated APK file for this prototype. Please be aware that Android APKs can be easily decompiled, and these keys can be extracted by anyone who has access to the file. Do not share this APK file publicly or with unauthorized individuals.",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            lineHeight = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
