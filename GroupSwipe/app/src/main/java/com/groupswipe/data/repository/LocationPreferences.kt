package com.groupswipe.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Predefiniowane miasto z gotowymi współrzędnymi. */
data class City(val name: String, val lat: Double, val lng: Double)

/** Katalog miast do wyboru jako alternatywa dla lokalizacji GPS. */
object Cities {
    val ALL = listOf(
        City("Warszawa", 52.2297, 21.0122),
        City("Kraków", 50.0647, 19.9450),
        City("Wrocław", 51.1079, 17.0385),
        City("Gdańsk", 54.3520, 18.6466),
        City("Poznań", 52.4064, 16.9252),
        City("Łódź", 51.7592, 19.4560),
        City("Szczecin", 53.4285, 14.5528),
        City("Lublin", 51.2465, 22.5684),
        City("Katowice", 50.2649, 19.0238),
        City("Zakopane", 49.2992, 19.9496),
        City("Londyn", 51.5074, -0.1278),
        City("Paryż", 48.8566, 2.3522),
        City("Berlin", 52.5200, 13.4050),
        City("Barcelona", 41.3851, 2.1734),
        City("Rzym", 41.9028, 12.4964),
        City("Praga", 50.0755, 14.4378),
        City("Amsterdam", 52.3676, 4.9041),
        City("Wiedeń", 48.2082, 16.3738),
        City("Nowy Jork", 40.7128, -74.0060),
        City("Tokio", 35.6762, 139.6503)
    )

    val NAMES: List<String> = ALL.map { it.name }

    fun find(name: String?): City? = ALL.firstOrNull { it.name == name }
}

/**
 * Trwałe ustawienia źródła lokalizacji propozycji:
 *  - tryb GPS (domyślny) – używa bieżącej lokalizacji urządzenia,
 *  - tryb miasta – używa współrzędnych wybranego miasta (alternatywa bez GPS).
 */
@Singleton
class LocationPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("location_prefs", Context.MODE_PRIVATE)

    var useCity: Boolean
        get() = prefs.getBoolean(KEY_USE_CITY, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_CITY, value).apply()

    var cityName: String?
        get() = prefs.getString(KEY_CITY_NAME, null)
        set(value) = prefs.edit().putString(KEY_CITY_NAME, value).apply()

    /** Współrzędne wybranego miasta lub null, gdy tryb miasta wyłączony / brak wyboru. */
    fun selectedCity(): City? = if (useCity) Cities.find(cityName) else null

    companion object {
        private const val KEY_USE_CITY = "use_city"
        private const val KEY_CITY_NAME = "city_name"
    }
}
