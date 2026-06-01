package io.github.juns_git.android.familystockgate.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// Naver Finance 모바일 API — 당일 현재가 조회
interface NaverStockApiService {
    @GET("api/stock/{code}/basic")
    suspend fun getStockBasic(
        @Path("code") code: String
    ): NaverStockBasicResponse
}

interface StockApiService {

    // 이름 검색 — 마스터 미로드 시 폴백용
    @GET("1160100/service/GetStockSecuritiesInfoService/getStockPriceInfo")
    suspend fun searchByName(
        @Query(value = "serviceKey", encoded = true) serviceKey: String,
        @Query("resultType") resultType: String,
        @Query("likeItmsNm") query: String,
        @Query("numOfRows") numOfRows: Int
    ): StockPriceApiResponse

    // 전체 종목 일괄 다운로드 (최초 1회 / 부모 강제 갱신)
    @GET("1160100/service/GetStockSecuritiesInfoService/getStockPriceInfo")
    suspend fun fetchAllStocks(
        @Query(value = "serviceKey", encoded = true) serviceKey: String,
        @Query("resultType") resultType: String,
        @Query("numOfRows") numOfRows: Int
    ): StockPriceApiResponse

    // 단일 종목 현재가 — 관심 등록 / 초기 설정 시점 단발성
    @GET("1160100/service/GetStockSecuritiesInfoService/getStockPriceInfo")
    suspend fun fetchSingleStock(
        @Query(value = "serviceKey", encoded = true) serviceKey: String,
        @Query("resultType") resultType: String,
        @Query("srtnCd") ticker: String,
        @Query("numOfRows") numOfRows: Int
    ): StockPriceApiResponse

    // 단일 종목 날짜 범위 조회 — 30일 차트 히스토리용
    @GET("1160100/service/GetStockSecuritiesInfoService/getStockPriceInfo")
    suspend fun fetchStockHistory(
        @Query(value = "serviceKey", encoded = true) serviceKey: String,
        @Query("resultType") resultType: String,
        @Query("itmsNm") stockName: String,
        @Query("beginBasDt") beginDate: String,
        @Query("endBasDt") endDate: String,
        @Query("numOfRows") numOfRows: Int
    ): StockPriceApiResponse
}
