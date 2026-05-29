package io.github.juns_git.android.familystockgate.data.remote

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://apis.data.go.kr/"

    private val gson = GsonBuilder()
        .registerTypeAdapter(ApiBody::class.java, ApiBodyDeserializer())
        .create()

    private val logger = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private fun buildRetrofit(client: OkHttpClient): StockApiService =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(StockApiService::class.java)

    // 단건 검색용 (짧은 타임아웃)
    val stockApiService: StockApiService by lazy {
        buildRetrofit(
            OkHttpClient.Builder()
                .addInterceptor(logger)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .build()
        )
    }

    // 전체 5,000건 다운로드용 (긴 타임아웃)
    val bulkStockApiService: StockApiService by lazy {
        buildRetrofit(
            OkHttpClient.Builder()
                .addInterceptor(logger)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .build()
        )
    }
}
