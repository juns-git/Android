package io.github.juns_git.android.familystockgate.data.remote

import android.util.Log
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

// ── Naver Finance 모바일 API 모델 ──────────────────────────────────────────
data class NaverStockBasicResponse(
    val stockCode: String?,
    val stockName: String?,
    val closePrice: String?,               // 현재가 (장중) 또는 종가, 쉼표 포함 "317,000"
    val compareToPreviousClosePrice: String?,
    val fluctuationsRatio: String?         // 등락률 "+5.84" / "-2.44"
)

// ── 공공데이터포털 KRX API 모델 (마스터 목록 다운로드 전용) ──────────────────
data class StockPriceApiResponse(val response: ApiResponse?)
data class ApiResponse(val header: ApiHeader?, val body: ApiBody?)
data class ApiHeader(val resultCode: String?, val resultMsg: String?)
data class ApiBody(val items: List<StockItemResponse>?)

data class StockItemResponse(
    val basDt: String?,    // 기준일자 (YYYYMMDD)
    val srtnCd: String?,   // 단축코드
    val itmsNm: String?,   // 종목명
    val clpr: String?,     // 종가
    val fltRt: String?,    // 등락률
    val trqu: String?      // 거래량 (Trading Quantity)
)

/**
 * items 필드 quirk 처리 (전체 runCatching으로 파싱 예외가 앱을 죽이지 않도록 방어):
 *   "items": ""              → emptyList
 *   "items": {}              → emptyList (item 키 없음)
 *   "items": {"item": {...}} → listOf(one item)
 *   "items": {"item": [...]} → parsed list
 */
class ApiBodyDeserializer : JsonDeserializer<ApiBody> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): ApiBody = runCatching {
        val obj = json.asJsonObject
        ApiBody(parseItems(obj.get("items"), context))
    }.getOrElse { e ->
        Log.e("ApiBodyDeserializer", "파싱 오류: ${e.message}", e)
        ApiBody(emptyList())
    }

    private fun parseItems(
        itemsElem: JsonElement?,
        context: JsonDeserializationContext
    ): List<StockItemResponse> {
        if (itemsElem == null || itemsElem.isJsonNull || itemsElem.isJsonPrimitive) return emptyList()
        val itemsObj = runCatching { itemsElem.asJsonObject }.getOrNull() ?: return emptyList()
        val item = itemsObj.get("item") ?: return emptyList()
        return when {
            item.isJsonNull   -> emptyList()
            item.isJsonArray  -> item.asJsonArray.mapNotNull { elem ->
                runCatching {
                    context.deserialize<StockItemResponse>(elem, StockItemResponse::class.java)
                }.getOrNull()
            }
            item.isJsonObject -> listOfNotNull(
                runCatching {
                    context.deserialize<StockItemResponse>(item, StockItemResponse::class.java)
                }.getOrNull()
            )
            else -> emptyList()
        }
    }
}
