package io.github.juns_git.android.familystockgate.utils

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore

data class FirebaseCustomConfig(
    val apiKey: String,
    val appId: String,
    val projectId: String,
    val gcmSenderId: String
)

object FirebaseConfigManager {

    private const val PREFS_NAME  = "firebase_custom_config"
    private const val KEY_API_KEY = "apiKey"
    private const val KEY_APP_ID  = "appId"
    private const val KEY_PROJECT  = "projectId"
    private const val KEY_SENDER   = "gcmSenderId"
    private const val KEY_INVITE   = "inviteCode"

    /** 독립 파이어베이스 앱 인스턴스 이름 */
    const val FAMILY_APP_NAME = "familyApp"

    /**
     * 개발자 공용 SHA-1 서명 지문.
     * Firebase Console > 안드로이드 앱 > SHA 인증서 지문 에 등록된 값과 동일해야 한다.
     */
    const val DEV_SHA1 = "97a56d77f2fe7f4aa9a252e1ed511b1523299dd8"

    // ── SharedPreferences 읽기/쓰기 ───────────────────────────────────────────

    fun saveConfig(context: Context, config: FirebaseCustomConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().run {
            putString(KEY_API_KEY, config.apiKey.trim())
            putString(KEY_APP_ID,  config.appId.trim())
            putString(KEY_PROJECT, config.projectId.trim())
            putString(KEY_SENDER,  config.gcmSenderId.trim())
            apply()
        }
    }

    fun loadConfig(context: Context): FirebaseCustomConfig? {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return FirebaseCustomConfig(
            apiKey      = p.getString(KEY_API_KEY, null) ?: return null,
            appId       = p.getString(KEY_APP_ID,  null) ?: return null,
            projectId   = p.getString(KEY_PROJECT, null) ?: return null,
            gcmSenderId = p.getString(KEY_SENDER,  null) ?: return null
        )
    }

    fun hasConfig(context: Context): Boolean = loadConfig(context) != null

    fun clearConfig(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun saveInviteCode(context: Context, code: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_INVITE, code).apply()
    }

    fun loadInviteCode(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_INVITE, null)

    // ── Firebase 초기화 ───────────────────────────────────────────────────────

    /**
     * 저장된 커스텀 키로 "familyApp" FirebaseApp 을 초기화한다.
     * - 이미 초기화된 경우: 그냥 true 반환
     * - 저장된 키 없음: false 반환
     * - 초기화 실패(키 오류 등): false 반환
     */
    fun initCustomFirebaseApp(context: Context): Boolean {
        val config = loadConfig(context) ?: return false
        if (FirebaseApp.getApps(context).any { it.name == FAMILY_APP_NAME }) return true
        return try {
            val options = FirebaseOptions.Builder()
                .setApiKey(config.apiKey)
                .setApplicationId(config.appId)
                .setProjectId(config.projectId)
                .setGcmSenderId(config.gcmSenderId)
                .build()
            FirebaseApp.initializeApp(context, options, FAMILY_APP_NAME)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 가족 데이터용 Firestore 인스턴스를 반환한다.
     * 커스텀 앱이 초기화되어 있으면 그것을, 없으면 DEFAULT(google-services.json)를 사용한다.
     */
    fun getFamilyDb(context: Context): FirebaseFirestore {
        val familyApp = FirebaseApp.getApps(context).firstOrNull { it.name == FAMILY_APP_NAME }
        return if (familyApp != null) FirebaseFirestore.getInstance(familyApp)
               else FirebaseFirestore.getInstance()
    }

    /** 개발자 중앙 Firebase Firestore (global_invites 컬렉션 전용). */
    fun getDefaultDb(): FirebaseFirestore = FirebaseFirestore.getInstance()

    fun isUsingCustomFirebase(context: Context): Boolean =
        FirebaseApp.getApps(context).any { it.name == FAMILY_APP_NAME }
}
