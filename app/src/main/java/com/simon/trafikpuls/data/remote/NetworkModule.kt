package com.simon.trafikpuls.data.remote

import android.util.Log
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    const val BASE_URL = "https://realtime-api.trafiklab.se/v1/"

    /** Lenient on input: unknown fields are ignored and nulls fall back to defaults. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val apiKeyPattern = Regex("""key=[^&\s]+""")

    fun okHttpClient(debugLogging: Boolean, userAgent: String): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
            })
            .apply {
                if (debugLogging) {
                    // The API key travels as a query parameter; never let it reach logcat.
                    val logger = HttpLoggingInterceptor { message ->
                        Log.d("Trafiklab", message.replace(apiKeyPattern, "key=***"))
                    }
                    logger.level = HttpLoggingInterceptor.Level.BASIC
                    addInterceptor(logger)
                }
            }
            .build()

    fun trafiklabApi(client: OkHttpClient, baseUrl: String = BASE_URL): TrafiklabApi =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TrafiklabApi::class.java)
}
