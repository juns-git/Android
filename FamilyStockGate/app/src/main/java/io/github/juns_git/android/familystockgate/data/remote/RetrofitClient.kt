package io.github.juns_git.android.familystockgate.data.remote

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val KRX_BASE_URL   = "https://apis.data.go.kr/"
    private const val NAVER_BASE_URL = "https://m.stock.naver.com/"

    private val krxGson = GsonBuilder()
        .registerTypeAdapter(ApiBody::class.java, ApiBodyDeserializer())
        .create()

    private val logger = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val naverClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(logger)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                        .header("Referer", "https://m.stock.naver.com/")
                        .build()
                )
            }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // 당일 현재가 — Naver Finance (primary)
    val naverStockApiService: NaverStockApiService by lazy {
        Retrofit.Builder()
            .baseUrl(NAVER_BASE_URL)
            .client(naverClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NaverStockApiService::class.java)
    }

    // 단건 검색용 — KRX (마스터 미로드 시 폴백, 종목명 검색)
    val stockApiService: StockApiService by lazy {
        Retrofit.Builder()
            .baseUrl(KRX_BASE_URL)
            .client(OkHttpClient.Builder()
                .addInterceptor(logger)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .build())
            .addConverterFactory(GsonConverterFactory.create(krxGson))
            .build()
            .create(StockApiService::class.java)
    }

    // 전체 5,000건 다운로드용 (긴 타임아웃)
    val bulkStockApiService: StockApiService by lazy {
        Retrofit.Builder()
            .baseUrl(KRX_BASE_URL)
            .client(OkHttpClient.Builder()
                .addInterceptor(logger)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .build())
            .addConverterFactory(GsonConverterFactory.create(krxGson))
            .build()
            .create(StockApiService::class.java)
    }
}
