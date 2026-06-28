package com.groupswipe

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient

@HiltAndroidApp
class GroupSwipeApp : Application(), ImageLoaderFactory {

    /**
     * Własny ImageLoader Coila z poprawnym nagłówkiem User-Agent.
     *
     * Domyślny User-Agent OkHttpa ("okhttp/x.y.z") jest ODRZUCANY przez część
     * serwerów obrazów (Wikimedia zwraca wtedy HTTP 403, a picsum bywa blokowany),
     * dlatego zdjęcia hoteli, wakacji i restauracji się nie ładowały. TMDB i
     * Ticketmaster nie sprawdzają UA, więc tam działało.
     *
     * Ustawiamy więc User-Agent przeglądarkowy dla wszystkich pobrań obrazów.
     */
    override fun newImageLoader(): ImageLoader {
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "GroupSwipe/1.0 (Android app; kontakt: app@groupswipe.example) Mozilla/5.0"
                    )
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(httpClient)
            .crossfade(true)
            .respectCacheHeaders(false)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .build()
    }
}
