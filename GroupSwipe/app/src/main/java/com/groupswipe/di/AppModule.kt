package com.groupswipe.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.groupswipe.data.local.GroupSwipeDatabase
import com.groupswipe.data.local.dao.ProposalDao
import com.groupswipe.data.local.dao.SessionHistoryDao
import com.groupswipe.data.local.dao.VoteDao
import com.groupswipe.data.remote.api.FoursquareApi
import com.groupswipe.data.remote.api.OpenTripMapApi
import com.groupswipe.data.remote.api.OverpassApi
import com.groupswipe.data.remote.api.TeleportApi
import com.groupswipe.data.remote.api.TicketmasterApi
import com.groupswipe.data.remote.api.TmdbApi
import com.groupswipe.data.remote.api.WikipediaApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GroupSwipeDatabase =
        Room.databaseBuilder(context, GroupSwipeDatabase::class.java, "groupswipe.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideSessionHistoryDao(db: GroupSwipeDatabase): SessionHistoryDao = db.sessionHistoryDao()
    @Provides fun provideProposalDao(db: GroupSwipeDatabase): ProposalDao = db.proposalDao()
    @Provides fun provideVoteDao(db: GroupSwipeDatabase): VoteDao = db.voteDao()

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS // Mniej spamu, tylko nagłówki
        })
        // User-Agent: niektóre API (m.in. Wikipedia REST) odrzucają domyślne
        // "okhttp/x.y.z" błędem 403. Dodajemy przeglądarkowy UA, jeśli żądanie
        // nie ustawiło własnego (Overpass ustawia swój przez @Header).
        .addInterceptor { chain ->
            val original = chain.request()
            val request = if (original.header("User-Agent") == null) {
                original.newBuilder()
                    .header("User-Agent", "GroupSwipe/1.0 (Android app; app@groupswipe.example)")
                    .build()
            } else original
            chain.proceed(request)
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS) // Overpass bywa wolniejsze
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun buildRetrofit(client: OkHttpClient, baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides @Singleton
    fun provideTmdbApi(client: OkHttpClient): TmdbApi =
        buildRetrofit(client, "https://api.themoviedb.org/3/").create(TmdbApi::class.java)

    @Provides @Singleton
    fun provideFoursquareApi(client: OkHttpClient): FoursquareApi =
        buildRetrofit(client, "https://places-api.foursquare.com/").create(FoursquareApi::class.java)

    @Provides @Singleton
    fun provideWikipediaApi(client: OkHttpClient): WikipediaApi =
        buildRetrofit(client, "https://pl.wikipedia.org/").create(WikipediaApi::class.java)

    @Provides @Singleton
    fun provideOverpassApi(client: OkHttpClient): OverpassApi =
        buildRetrofit(client, "https://overpass-api.de/").create(OverpassApi::class.java)

    @Provides @Singleton
    fun provideOpenTripMapApi(client: OkHttpClient): OpenTripMapApi =
        buildRetrofit(client, "https://api.opentripmap.com/").create(OpenTripMapApi::class.java)

    @Provides @Singleton
    fun provideTeleportApi(client: OkHttpClient): TeleportApi =
        buildRetrofit(client, "https://api.teleport.org/api/").create(TeleportApi::class.java)

    @Provides @Singleton
    fun provideTicketmasterApi(client: OkHttpClient): TicketmasterApi =
        buildRetrofit(client, "https://app.ticketmaster.com/").create(TicketmasterApi::class.java)
}
