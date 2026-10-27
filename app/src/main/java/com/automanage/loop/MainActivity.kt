package com.automanage.loop

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.min

private val Ink = Color(0xFF202239)
private val Muted = Color(0xFF85889D)
private val Purple = Color(0xFF7560E8)
private val Mint = Color(0xFFBFF3DC)
private val Paper = Color(0xFFF7F8FC)
private val Line = Color(0xFFEBECF3)

private enum class PostStatus(val title: String) {
    QUEUED("생성 대기"), GENERATING("AI 생성 중"), DRAFT("초안"), SCHEDULED("예약됨"),
    PUBLISHING("게시 중"), PUBLISHED("게시 완료"), FAILED("오류"), REVIEW("게시 확인 필요")
}

private data class StudioSettings(
    val brand: String = "",
    val description: String = "",
    val audience: String = "",
    val tone: String = "친근하고 담백하게",
    val pillars: String = "일상, 브랜드 이야기, 유용한 팁",
    val imageStyle: String = "자연광, 차분한 색감, 감각적인 라이프스타일 사진",
    val hour: Int = 19,
    val minute: Int = 0,
    val enabled: Boolean = false,
    val autoGenerate: Boolean = true,
    val autoPublish: Boolean = true,
    val textModel: String = "gpt-5.2",
    val imageModel: String = "gpt-image-2.5-sunburst",
    val contentMode: String = "MIXED",
    val username: String = "",
    val accountId: String = "",
    val connected: Boolean = false,
    val followers: Int = 0,
    val following: Int = 0,
    val mediaCount: Int = 0,
    val recentLikes: Int = 0,
    val recentComments: Int = 0,
    val syncedAt: Long = 0L
) {
    fun json() = JSONObject().apply {
        put("brand", brand); put("description", description); put("audience", audience)
        put("tone", tone); put("pillars", pillars); put("imageStyle", imageStyle)
        put("hour", hour); put("minute", minute); put("enabled", enabled)
        put("autoGenerate", autoGenerate); put("autoPublish", autoPublish)
        put("textModel", textModel); put("imageModel", imageModel); put("contentMode", contentMode)
        put("username", username); put("accountId", accountId); put("connected", connected)
        put("followers", followers); put("following", following); put("mediaCount", mediaCount)
        put("recentLikes", recentLikes); put("recentComments", recentComments); put("syncedAt", syncedAt)
    }
}

private data class StudioPost(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "새로운 콘텐츠",
    val brief: String = "",
    val caption: String = "",
    val imagePrompt: String = "",
    val image: String = "",
    val video: String = "",
    val alt: String = "",
    val format: String = "FEED",
    val status: PostStatus = PostStatus.QUEUED,
    val scheduledAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val touchedAt: Long = System.currentTimeMillis(),
    val publishedAt: Long = 0L,
    val automaticSlot: String = "",
    val accountId: String = "",
    val remoteId: String = "",
    val remoteCode: String = "",
    val likes: Int = 0,
    val comments: Int = 0,
    val configureStarted: Boolean = false,
    val error: String = ""
) {
    fun json() = JSONObject().apply {
        put("id", id); put("title", title); put("brief", brief); put("caption", caption)
        put("imagePrompt", imagePrompt); put("image", image); put("video", video); put("alt", alt); put("format", format)
        put("status", status.name); put("scheduledAt", scheduledAt); put("createdAt", createdAt)
        put("touchedAt", touchedAt); put("publishedAt", publishedAt); put("automaticSlot", automaticSlot)
        put("accountId", accountId); put("remoteId", remoteId); put("remoteCode", remoteCode)
        put("likes", likes); put("comments", comments); put("configureStarted", configureStarted); put("error", error)
    }
}

private class LocalStore private constructor(context: Context) : SQLiteOpenHelper(context, "loop.db", null, 1) {
    val app: Context = context.applicationContext
    val mediaDir = File(app.filesDir, "media").apply { mkdirs() }
    private val prefs = app.getSharedPreferences("studio", Context.MODE_PRIVATE)
    private val secrets = app.getSharedPreferences("vault", Context.MODE_PRIVATE)
    val changes = MutableStateFlow(0L)

    companion object {
        @Volatile private var instance: LocalStore? = null
        fun get(context: Context): LocalStore = instance ?: synchronized(this) {
            instance ?: LocalStore(context.applicationContext).also { instance = it }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE posts (id TEXT PRIMARY KEY, body TEXT NOT NULL, status TEXT NOT NULL, scheduled INTEGER NOT NULL, touched INTEGER NOT NULL, auto_slot TEXT UNIQUE)")
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, message TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized fun settings(): StudioSettings {
        val j = JSONObject(prefs.getString("settings", "{}") ?: "{}")
        return StudioSettings(
            brand = j.optString("brand"), description = j.optString("description"), audience = j.optString("audience"),
            tone = j.optString("tone", "친근하고 담백하게"), pillars = j.optString("pillars", "일상, 브랜드 이야기, 유용한 팁"),
            imageStyle = j.optString("imageStyle", "자연광, 차분한 색감, 감각적인 라이프스타일 사진"),
            hour = j.optInt("hour", 19).coerceIn(0, 23), minute = j.optInt("minute", 0).coerceIn(0, 59),
            enabled = j.optBoolean("enabled"), autoGenerate = j.optBoolean("autoGenerate", true),
            autoPublish = j.optBoolean("autoPublish", true),
            textModel = j.optString("textModel", "gpt-5.2").let { if (it.isBlank() || it == "gpt-4.1-mini") "gpt-5.2" else it },
            imageModel = j.optString("imageModel", "gpt-image-2.5-sunburst").let { if (it.isBlank() || it == "gpt-image-1") "gpt-image-2.5-sunburst" else it },
            contentMode = j.optString("contentMode", "MIXED"), username = j.optString("username"),
            accountId = j.optString("accountId"), connected = j.optBoolean("connected") && j.optString("accountId").startsWith("native:"), followers = j.optInt("followers"),
            following = j.optInt("following"), mediaCount = j.optInt("mediaCount"), recentLikes = j.optInt("recentLikes"),
            recentComments = j.optInt("recentComments"), syncedAt = j.optLong("syncedAt")
        )
    }

    @Synchronized fun changeSettings(change: (StudioSettings) -> StudioSettings) {
        check(prefs.edit().putString("settings", change(settings()).json().toString()).commit()) { "설정을 저장하지 못했습니다." }
        changes.value += 1
    }

    @Synchronized fun hasSecret(name: String) = secrets.contains(name)

    @Synchronized fun secret(name: String): String {
        val encoded = secrets.getString(name, null) ?: return ""
        try {
            val vault = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = vault.getKey("loop_device_key", null) as SecretKey
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) {
            throw IOException("휴대폰 암호화 정보를 읽지 못했습니다. 설정에서 키 또는 계정을 다시 연결해 주세요.")
        }
    }

    @Synchronized fun putSecret(name: String, value: String) {
        if (value.isBlank()) {
            check(secrets.edit().remove(name).commit()) { "인증 정보를 삭제하지 못했습니다." }
        } else {
            val vault = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = if (vault.containsAlias("loop_device_key")) vault.getKey("loop_device_key", null) as SecretKey else {
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                    init(KeyGenParameterSpec.Builder("loop_device_key", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                }.generateKey()
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            check(secrets.edit().putString(name, Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) { "인증 정보를 저장하지 못했습니다." }
        }
        changes.value += 1
    }

    @Synchronized fun posts(): List<StudioPost> {
        val result = mutableListOf<StudioPost>()
        readableDatabase.rawQuery("SELECT body FROM posts ORDER BY rowid DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                val j = JSONObject(cursor.getString(0))
                result += StudioPost(
                    id = j.getString("id"), title = j.optString("title"), brief = j.optString("brief"), caption = j.optString("caption"),
                    imagePrompt = j.optString("imagePrompt"), image = j.optString("image"), video = j.optString("video"), alt = j.optString("alt"), format = j.optString("format", "FEED"),
                    status = PostStatus.entries.firstOrNull { it.name == j.optString("status") } ?: PostStatus.FAILED,
                    scheduledAt = j.optLong("scheduledAt"), createdAt = j.optLong("createdAt"), touchedAt = j.optLong("touchedAt"),
                    publishedAt = j.optLong("publishedAt"), automaticSlot = j.optString("automaticSlot"), accountId = j.optString("accountId"),
                    remoteId = j.optString("remoteId"), remoteCode = j.optString("remoteCode"), likes = j.optInt("likes"),
                    comments = j.optInt("comments"), configureStarted = j.optBoolean("configureStarted"), error = j.optString("error")
                )
            }
        }
        return result
    }

    @Synchronized fun save(post: StudioPost) {
        val updated = post.copy(touchedAt = System.currentTimeMillis())
        val values = ContentValues().apply {
            put("id", updated.id); put("body", updated.json().toString()); put("status", updated.status.name)
            put("scheduled", updated.scheduledAt); put("touched", updated.touchedAt)
            if (updated.automaticSlot.isBlank()) putNull("auto_slot") else put("auto_slot", updated.automaticSlot)
        }
        check(writableDatabase.insertWithOnConflict("posts", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "콘텐츠를 저장하지 못했습니다." }
        changes.value += 1
    }

    @Synchronized fun update(id: String, change: (StudioPost) -> StudioPost): StudioPost? {
        val post = posts().firstOrNull { it.id == id } ?: return null
        return change(post).also { save(it) }
    }

    @Synchronized fun delete(id: String) {
        val post = posts().firstOrNull { it.id == id } ?: return
        check(post.status !in listOf(PostStatus.GENERATING, PostStatus.PUBLISHING)) { "진행 중인 작업이 끝난 뒤 삭제해 주세요." }
        writableDatabase.delete("posts", "id=?", arrayOf(id))
        if (post.image.isNotBlank()) media(post.image).delete()
        if (post.video.isNotBlank()) media(post.video).delete()
        changes.value += 1
    }

    fun media(name: String): File {
        require(name.isNotBlank() && name == File(name).name && name !in listOf(".", "..")) { "잘못된 이미지 경로입니다." }
        return File(mediaDir, name)
    }

    @Synchronized fun event(message: String) {
        writableDatabase.insertOrThrow("events", null, ContentValues().apply { put("at", System.currentTimeMillis()); put("message", message.take(350)) })
        writableDatabase.execSQL("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY id DESC LIMIT 100)")
        changes.value += 1
    }

    @Synchronized fun events(): List<Pair<Long, String>> {
        val result = mutableListOf<Pair<Long, String>>()
        readableDatabase.rawQuery("SELECT at,message FROM events ORDER BY id DESC LIMIT 30", null).use { c ->
            while (c.moveToNext()) result += c.getLong(0) to c.getString(1)
        }
        return result
    }
}

private class OpenAi(private val store: LocalStore) {
    suspend fun request(path: String, body: JSONObject? = null): JSONObject = suspendCancellableCoroutine { continuation ->
        val key = store.secret("openai")
        require(key.isNotBlank()) { "설정에서 OpenAI API 키를 입력해 주세요." }
        val connection = URL("https://api.openai.com/v1/$path").openConnection() as HttpURLConnection
        continuation.invokeOnCancellation { connection.disconnect() }
        Thread({
            try {
                if (!continuation.isActive) return@Thread
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 30_000
                connection.readTimeout = 240_000
                connection.requestMethod = if (body == null) "GET" else "POST"
                connection.setRequestProperty("Authorization", "Bearer $key")
                connection.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(text) }.getOrElse { throw IOException("OpenAI 응답을 읽지 못했습니다. HTTP $code") }
                if (code !in 200..299) {
                    val detail = json.optJSONObject("error")?.optString("message").orEmpty().replace(key, "숨김").take(230)
                    throw IOException("OpenAI 요청 오류 ($code): $detail")
                }
                if (continuation.isActive) continuation.resume(json)
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            } finally { connection.disconnect() }
        }, "loop-openai").start()
    }
}

private object StudioJobs {
    fun periodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<StudioWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf("action" to "pulse")).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("loop-cycle", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun enqueue(context: Context, action: String, id: String = "") {
        val request = OneTimeWorkRequestBuilder<StudioWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("loop-request-${System.currentTimeMillis()}")
            .setInputData(workDataOf("action" to action, "post" to id)).build()
        WorkManager.getInstance(context).enqueueUniqueWork("loop-pipeline", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun schedule(context: Context, post: StudioPost) {
        if (post.status != PostStatus.SCHEDULED) return
        val request = OneTimeWorkRequestBuilder<StudioWorker>()
            .setInitialDelay((post.scheduledAt - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf("action" to "pulse")).build()
        WorkManager.getInstance(context).enqueueUniqueWork("loop-schedule-${post.id}", ExistingWorkPolicy.REPLACE, request)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class StudioEngine(private val store: LocalStore, private val onStatus: suspend (String) -> Unit = {}) {
    suspend fun generate(id: String) {
        var post = store.posts().firstOrNull { it.id == id } ?: return
        if (post.status != PostStatus.QUEUED) return
        try {
            val settings = store.settings()
            require(settings.brand.isNotBlank()) { "설정에서 브랜드 이름과 운영 주제를 입력해 주세요." }
            if (post.automaticSlot.isNotBlank() && !settings.enabled) return
            var claimed = false
            val claimedPost = store.update(id) { current ->
                if (current.status != PostStatus.QUEUED) current else {
                    claimed = true
                    current.copy(status = PostStatus.GENERATING, error = "")
                }
            }
            if (!claimed || claimedPost == null) return
            post = claimedPost
            if (post.format == "AUTO") {
                val video = settings.contentMode == "VIDEO" || (settings.contentMode == "MIXED" && store.posts().count { it.id != id && it.image.isNotBlank() } % 2 == 1)
                post = post.copy(format = if (video) "REELS" else "FEED")
                store.save(post)
            }
            store.event("콘텐츠 생성하는 단계 :: ${post.brief.ifBlank { settings.brand }}")
            val properties = JSONObject().apply {
                put("title", JSONObject().put("type", "string"))
                put("caption", JSONObject().put("type", "string"))
                put("hashtags", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")))
                put("image_prompt", JSONObject().put("type", "string"))
                put("alt_text", JSONObject().put("type", "string"))
                if (post.format == "REELS") put("scenes", JSONObject().put("type", "array").put("minItems", 3).put("maxItems", 3)
                    .put("items", JSONObject().put("type", "object").put("properties", JSONObject()
                        .put("image_prompt", JSONObject().put("type", "string")).put("subtitle", JSONObject().put("type", "string")))
                        .put("required", JSONArray(listOf("image_prompt", "subtitle"))).put("additionalProperties", false)))
            }
            val schema = JSONObject().put("type", "object").put("properties", properties)
                .put("required", JSONArray(listOf("title", "caption", "hashtags", "image_prompt", "alt_text") + if (post.format == "REELS") listOf("scenes") else emptyList<String>())).put("additionalProperties", false)
            val history = store.posts().filter { it.id != id && it.caption.isNotBlank() }.take(10).joinToString("\n") {
                "${it.title}: ${it.caption.take(200)}" + if (it.status == PostStatus.PUBLISHED && settings.syncedAt > 0) " · 실제 반응: 좋아요 ${it.likes}, 댓글 ${it.comments}" else ""
            }
            val brief = """
                인스타그램 ${if (post.format == "REELS") "18초 세로 영상 릴스" else if (post.format == "STORY") "스토리" else "피드"} 콘텐츠를 만드세요.
                브랜드: ${settings.brand}
                설명과 사실 자료: ${settings.description}
                대상: ${settings.audience}
                말투: ${settings.tone}
                운영 주제: ${settings.pillars}
                이미지 스타일: ${settings.imageStyle}
                이번 요청: ${post.brief.ifBlank { "브랜드에 맞는 새로운 주제 하나를 고르세요." }}
                기존 콘텐츠와 중복을 피하세요: $history
                실제 반응 정보가 있으면 잘 반응한 주제와 형식을 참고하되, 성과 수치를 만들어 내지 마세요.
                title은 짧은 한국어 제목, caption은 150~600자의 자연스러운 한국어 본문과 행동 제안,
                hashtags는 관련 태그 3~5개, image_prompt는 실제 생성할 고품질 사진의 상세 영어 프롬프트,
                alt_text는 한국어 이미지 설명입니다. 확인되지 않은 가격, 후기, 성과, 상품 특성을 만들지 마세요.
                이미지에 워터마크와 작은 글씨를 넣지 마세요. 첨부 이미지가 있으면 그 이미지에 맞게 글을 쓰세요.
                ${if (post.format == "REELS") "scenes에는 시작·전개·마무리로 이어지는 6초 장면 3개를 만드세요. 각 image_prompt는 세로 장면 이미지용 상세 영어 프롬프트, subtitle은 한국어 핵심 자막 15~45자입니다." else ""}
            """.trimIndent()
            val content = JSONArray().put(JSONObject().put("type", "input_text").put("text", brief))
            if (post.image.isNotBlank()) {
                val photo = store.media(post.image)
                require(photo.exists()) { "첨부 이미지가 없습니다. 사진을 다시 선택해 주세요." }
                content.put(JSONObject().put("type", "input_image").put("image_url", "data:image/jpeg;base64," + Base64.encodeToString(photo.readBytes(), Base64.NO_WRAP)))
            }
            currentCoroutineContext().ensureActive()
            onStatus("${settings.textModel} · 콘텐츠 기획과 문구 생성 중")
            val response = OpenAi(store).request("responses", JSONObject()
                .put("model", settings.textModel).put("store", false)
                .put("input", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                .put("text", JSONObject().put("format", JSONObject().put("type", "json_schema").put("name", "instagram_post").put("strict", true).put("schema", schema))))
            require(response.optString("status") == "completed") { "GPT 생성이 완료되지 않았습니다. 모델 설정을 확인해 주세요." }
            val output = response.getJSONArray("output")
            val generated = StringBuilder()
            for (i in 0 until output.length()) {
                val parts = output.getJSONObject(i).optJSONArray("content") ?: continue
                for (k in 0 until parts.length()) {
                    val part = parts.getJSONObject(k)
                    if (part.optString("type") == "refusal") throw IOException("GPT가 이 콘텐츠를 생성하지 못했습니다. 주제를 변경해 주세요.")
                    if (part.optString("type") == "output_text") generated.append(part.getString("text"))
                }
            }
            val j = JSONObject(generated.toString())
            val tags = j.getJSONArray("hashtags")
            val tagText = (0 until min(tags.length(), 5)).joinToString(" ") { "#" + tags.getString(it).trim().removePrefix("#").replace(" ", "") }
            val caption = j.getString("caption").trim() + "\n\n" + tagText
            require(caption.length <= 2200) { "생성된 문구가 인스타그램 글자 수 제한을 초과했습니다." }
            post = post.copy(title = j.getString("title"), caption = caption, imagePrompt = j.getString("image_prompt"), alt = j.getString("alt_text"),
                video = if (post.format == "REELS") "" else post.video)
            store.save(post)
            if (post.format == "REELS") {
                val scenes = j.getJSONArray("scenes")
                require(scenes.length() == 3) { "영상 장면 3개를 생성하지 못했습니다." }
                val frames = mutableListOf<File>()
                val renderId = UUID.randomUUID().toString()
                try {
                    for (index in 0 until scenes.length()) {
                        currentCoroutineContext().ensureActive()
                        if (post.automaticSlot.isNotBlank() && !store.settings().enabled) throw IOException("자동화가 일시정지되어 영상 생성을 중단했습니다.")
                        val scene = scenes.getJSONObject(index)
                        onStatus("영상 장면 이미지 생성 중 · ${index + 1}/3")
                        val generatedImage = OpenAi(store).request("images/generations", JSONObject().put("model", settings.imageModel)
                            .put("moderation", "low")
                            .put("prompt", scene.getString("image_prompt") + ". Vertical 9:16 composition. No text or watermark.")
                            .put("n", 1).put("size", "1024x1536").put("quality", "medium").put("output_format", "jpeg"))
                        val bytes = Base64.decode(generatedImage.getJSONArray("data").getJSONObject(0).getString("b64_json"), Base64.DEFAULT)
                        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IOException("영상 장면 이미지를 읽지 못했습니다.")
                        val frame = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
                        try {
                            val canvas = android.graphics.Canvas(frame)
                            val cropWidth = min(original.width, original.height * 9 / 16)
                            val cropHeight = min(original.height, original.width * 16 / 9)
                            val source = android.graphics.Rect((original.width - cropWidth) / 2, (original.height - cropHeight) / 2, (original.width + cropWidth) / 2, (original.height + cropHeight) / 2)
                            canvas.drawBitmap(original, source, android.graphics.Rect(0, 0, 720, 1280), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                            val subtitle = scene.getString("subtitle").take(90)
                            val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.WHITE; textSize = 36f; typeface = android.graphics.Typeface.DEFAULT_BOLD
                            }
                            val layout = android.text.StaticLayout.Builder.obtain(subtitle, 0, subtitle.length, paint, 620)
                                .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setLineSpacing(8f, 1f)
                                .setMaxLines(4).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
                            val top = 1120f - layout.height
                            canvas.drawRoundRect(android.graphics.RectF(28f, top - 24, 692f, 1150f), 22f, 22f,
                                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xAA000000.toInt() })
                            canvas.save(); canvas.translate(50f, top); layout.draw(canvas); canvas.restore()
                            val file = store.media("$id-$renderId-scene-$index.jpg")
                            frames.add(file)
                            file.outputStream().use { check(frame.compress(Bitmap.CompressFormat.JPEG, 93, it)) }
                        } finally { original.recycle(); frame.recycle() }
                    }
                    val output = store.media("$id-$renderId.mp4")
                    try {
                        onStatus("장면과 자막을 MP4 영상으로 합성 중")
                        withTimeout(240_000) {
                            withContext(Dispatchers.Main) {
                                suspendCancellableCoroutine<Unit> { continuation ->
                                    val items = frames.map { file -> EditedMediaItem.Builder(MediaItem.Builder().setUri(Uri.fromFile(file)).setImageDurationMs(6000).build()).setFrameRate(24).build() }
                                    val composition = Composition.Builder(EditedMediaItemSequence.withVideoFrom(items)).build()
                                    val transformer = Transformer.Builder(store.app).setVideoMimeType(MimeTypes.VIDEO_H264).setPortraitEncodingEnabled(true)
                                        .addListener(object : Transformer.Listener {
                                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                                if (continuation.isActive) continuation.resume(Unit)
                                            }
                                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                                if (continuation.isActive) continuation.resumeWithException(IOException("MP4 영상 합성 실패 (코드 ${exportException.errorCode}): ${exportException.cause?.message.orEmpty().take(180)}", exportException))
                                            }
                                        }).build()
                                    continuation.invokeOnCancellation { Handler(Looper.getMainLooper()).post { transformer.cancel() } }
                                    try { transformer.start(composition, output.absolutePath) }
                                    catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                                }
                            }
                        }
                        require(output.isFile && output.length() > 0) { "MP4 영상 파일이 생성되지 않았습니다." }
                    } catch (error: Throwable) {
                        output.delete()
                        if (error is kotlinx.coroutines.TimeoutCancellationException) throw IOException("MP4 영상 합성 제한 시간 4분을 초과했습니다.", error)
                        throw error
                    }
                    post = post.copy(image = frames.first().name, video = output.name)
                    store.save(post)
                } finally {
                    frames.filter { it.name != post.image }.forEach { it.delete() }
                }
            } else if (post.image.isBlank()) {
                currentCoroutineContext().ensureActive()
                if (post.automaticSlot.isNotBlank() && !store.settings().enabled) {
                    store.save(post.copy(status = PostStatus.FAILED, error = "자동화가 일시정지되어 이미지 생성 전 중단했습니다."))
                    return
                }
                onStatus("사진 이미지 생성 중")
                val result = OpenAi(store).request("images/generations", JSONObject().put("model", settings.imageModel)
                    .put("moderation", "low")
                    .put("prompt", post.imagePrompt + if (post.format == "STORY") ". Vertical composition for an Instagram story, subject centered." else ". Square Instagram editorial photograph.")
                    .put("n", 1).put("size", if (post.format == "STORY") "1024x1536" else "1024x1024")
                    .put("quality", "medium").put("output_format", "jpeg"))
                val bytes = Base64.decode(result.getJSONArray("data").getJSONObject(0).getString("b64_json"), Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IOException("생성된 이미지를 읽지 못했습니다.")
                val targetRatio = if (post.format == "STORY") 9f / 16f else 1f
                val cropWidth = min(bitmap.width, (bitmap.height * targetRatio).toInt())
                val cropHeight = min(bitmap.height, (bitmap.width / targetRatio).toInt())
                val cropped = Bitmap.createBitmap(bitmap, (bitmap.width - cropWidth) / 2, (bitmap.height - cropHeight) / 2, cropWidth, cropHeight)
                val filename = "${post.id}.jpg"
                store.media(filename).outputStream().use { check(cropped.compress(Bitmap.CompressFormat.JPEG, 93, it)) }
                if (cropped !== bitmap) cropped.recycle()
                bitmap.recycle()
                post = post.copy(image = filename)
                store.save(post)
            }
            currentCoroutineContext().ensureActive()
            val latest = store.settings()
            val shouldSchedule = post.scheduledAt > 0L && (post.automaticSlot.isBlank() || (latest.enabled && latest.autoPublish))
            post = post.copy(status = if (shouldSchedule) PostStatus.SCHEDULED else PostStatus.DRAFT, error = "")
            store.save(post)
            if (shouldSchedule) StudioJobs.schedule(store.app, post)
            store.event("콘텐츠 저장하는 단계 :: ${post.title}")
        } catch (cancel: CancellationException) {
            store.update(id) { it.copy(status = PostStatus.FAILED, error = "작업이 중단되었습니다. 생성된 내용은 보관했습니다.") }
            throw cancel
        } catch (error: Exception) {
            store.update(id) { it.copy(status = PostStatus.FAILED, error = error.message.orEmpty().take(300)) }
            store.event("콘텐츠 생성 오류 확인하는 단계 :: ${error.message.orEmpty().take(230)}")
            notify("콘텐츠 생성을 완료하지 못했습니다", "앱에서 오류 내용을 확인해 주세요.", id.hashCode())
        }
    }

    suspend fun publish(id: String, manual: Boolean = false) {
        val post = store.posts().firstOrNull { it.id == id } ?: return
        if (post.configureStarted || post.remoteId.isNotBlank() || post.status in listOf(PostStatus.PUBLISHED, PostStatus.GENERATING, PostStatus.QUEUED)) return
        val settings = store.settings()
        if (!manual && (!settings.enabled || !settings.autoPublish)) return
        try {
            require(settings.connected) { "인스타그램 앱에서 계정을 먼저 연결해 주세요." }
            require(post.accountId.isBlank() || post.accountId == settings.accountId) { "예약한 계정과 연결된 계정이 다릅니다." }
            require(post.caption.isNotBlank() && post.caption.length <= 2200) { "게시할 문구를 확인해 주세요." }
            require(post.image.isNotBlank() && store.media(post.image).isFile) { "생성된 이미지가 없습니다." }
            require(post.format != "REELS" || (post.video.isNotBlank() && store.media(post.video).isFile)) { "영상 생성이 완료되지 않았습니다." }
            val keyguard = store.app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val power = store.app.getSystemService(Context.POWER_SERVICE) as PowerManager
            val service = InstagramAutomationService.active
            if (keyguard.isKeyguardLocked || !power.isInteractive || service == null) {
                val message = if (service == null) "설정에서 인스타그램 자동 게시 화면 조작 권한을 켜 주세요." else "휴대폰 잠금을 해제하면 예약 게시를 이어갑니다."
                if (post.error != message) notify("예약 게시 대기 중", message, id.hashCode())
                store.update(id) { it.copy(error = message) }
                if (manual) throw IOException(message)
                return
            }
            store.update(id) { it.copy(status = PostStatus.PUBLISHING, error = "") }
            onStatus("인스타그램 화면 조작 시작 대기 중")
            service.execute(id, manual, onStatus)
        } catch (cancel: CancellationException) {
            store.update(id) { it.copy(status = if (it.configureStarted) PostStatus.REVIEW else PostStatus.DRAFT, error = "게시 작업이 중단되었습니다. 인스타그램에서 게시 여부를 확인해 주세요.") }
            throw cancel
        } catch (error: Exception) {
            store.update(id) { it.copy(status = if (it.configureStarted) PostStatus.REVIEW else PostStatus.FAILED, error = error.message.orEmpty().take(300)) }
            throw error
        }
    }

    suspend fun sync() {
        val service = InstagramAutomationService.active ?: throw IOException("설정에서 인스타그램 자동 게시 화면 조작 권한을 켜 주세요.")
        service.execute(onStatus = onStatus)
    }

    private fun notify(title: String, text: String, id: Int) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(store.app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = store.app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("loop", "루프 운영 알림", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = android.app.PendingIntent.getActivity(store.app, id, Intent(store.app, MainActivity::class.java), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        manager.notify(id, NotificationCompat.Builder(store.app, "loop").setSmallIcon(R.drawable.ic_loop).setContentTitle(title)
            .setContentText(text).setContentIntent(intent).setAutoCancel(true).build())
    }
}

class InstagramAutomationService : AccessibilityService() {
    companion object {
        @Volatile var active: InstagramAutomationService? = null
            private set
    }
    private val gate = Mutex()
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var stopping = 0L
    private var flowPost = ""
    private var confirmedAt = 0L
    private val unlocked = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT && LocalStore.get(context).settings().enabled) StudioJobs.enqueue(context, "tick")
        }
    }
    private val timer = object : Runnable {
        override fun run() {
            val store = LocalStore.get(this@InstagramAutomationService)
            if (store.settings().enabled && flowPost.isBlank() && store.posts().any { it.status == PostStatus.SCHEDULED && it.scheduledAt <= System.currentTimeMillis() }) {
                StudioJobs.enqueue(this@InstagramAutomationService, "tick")
            }
            handler.postDelayed(this, 60_000)
        }
    }

    override fun onServiceConnected() {
        active = this
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(unlocked, IntentFilter(Intent.ACTION_USER_PRESENT), Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(unlocked, IntentFilter(Intent.ACTION_USER_PRESENT))
        handler.post(timer)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (flowPost.isBlank() || event?.packageName?.toString() != "com.instagram.android") return
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val text = event.text.joinToString(" ")
            if (Regex("(게시물|릴스|동영상).{0,16}(공유되었습니다|공유됐습니다|게시되었습니다|공유 완료|게시 완료)|(post|reel|video) (has been |was )?(shared|posted)", RegexOption.IGNORE_CASE).containsMatchIn(text)) confirmedAt = System.currentTimeMillis()
        }
    }

    fun stopPosting() { stopping += 1 }
    override fun onInterrupt() { stopping += 1 }
    override fun onDestroy() {
        stopping += 1
        active = null
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(unlocked) }
        super.onDestroy()
    }

    suspend fun execute(postId: String = "", manual: Boolean = false, onStatus: suspend (String) -> Unit = {}) = gate.withLock {
        withContext(Dispatchers.Main) {
            val store = LocalStore.get(this@InstagramAutomationService)
            val settings = store.settings()
            val username = store.secret("instagram_username").ifBlank { settings.username }.trim().removePrefix("@")
            require(username.matches(Regex("[A-Za-z0-9._]{1,30}"))) { "인스타그램 아이디를 저장해 주세요." }
            check(active === this@InstagramAutomationService) { "화면 조작 권한이 꺼져 있습니다." }
            val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            check(!keyguard.isKeyguardLocked && power.isInteractive) { "휴대폰 잠금을 해제한 뒤 다시 시도해 주세요." }
            val post = if (postId.isBlank()) null else store.posts().firstOrNull { it.id == postId } ?: throw IOException("콘텐츠가 없습니다.")
            require(post == null || post.format in listOf("FEED", "REELS")) { "자동 게시에는 사진 피드 또는 영상 릴스를 선택해 주세요." }
            val epoch = stopping
            var phase = "PROFILE"
            var loginFilled = false
            var nextCount = 0
            var destinationChosen = false
            var captionEntered = false
            var lastFingerprint = ""
            var lastClickAt = 0L
            var shareAt = 0L
            val deadline = System.currentTimeMillis() + 240_000
            flowPost = postId
            confirmedAt = 0
            try {
                onStatus("인스타그램 앱에서 로그인 계정 확인 중")
                try { packageManager.getPackageInfo("com.instagram.android", 0) }
                catch (_: PackageManager.NameNotFoundException) { throw IOException("휴대폰에 인스타그램 앱을 설치한 뒤 연결해 주세요.") }
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/$username/")).setPackage("com.instagram.android").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                while (System.currentTimeMillis() < deadline) {
                    currentCoroutineContext().ensureActive()
                    check(stopping == epoch) { "자동 게시가 중지되었습니다." }
                    check(!keyguard.isKeyguardLocked && power.isInteractive) { "휴대폰이 잠겨 게시 작업을 중단했습니다." }
                    if (post != null && !manual && (!store.settings().enabled || !store.settings().autoPublish)) throw IOException("자동화가 일시정지되었습니다.")
                    delay(600)
                    val root = rootInActiveWindow ?: continue
                    if (root.packageName?.toString() != "com.instagram.android") continue
                    val nodes = mutableListOf<AccessibilityNodeInfo>()
                    val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
                    queue.add(root)
                    while (queue.isNotEmpty() && nodes.size < 600) {
                        val node = queue.removeFirst()
                        if (node.isVisibleToUser) nodes.add(node)
                        for (index in 0 until node.childCount) node.getChild(index)?.let { queue.add(it) }
                    }
                    val texts = nodes.flatMap { listOf(it.text?.toString().orEmpty(), it.contentDescription?.toString().orEmpty()) }.filter { it.isNotBlank() }
                    if (phase == "PROFILE") {
                        val ownProfile = texts.any { it.trim() in listOf("프로필 편집", "Edit profile", "Edit Profile") }
                        val correctAccount = texts.any { it.trim().removePrefix("@").equals(username, ignoreCase = true) }
                        if (ownProfile && correctAccount) {
                            val accountId = "native:$username"
                            require(post == null || post.accountId.isBlank() || post.accountId == accountId) { "예약한 계정과 로그인된 계정이 다릅니다." }
                            store.changeSettings { it.copy(username = username, accountId = accountId, connected = true, syncedAt = 0) }
                            if (post == null) {
                                startActivity(Intent(this@InstagramAutomationService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                                return@withContext
                            }
                            val filename = post.video.ifBlank { post.image }
                            val file = store.media(filename)
                            require(file.isFile && file.length() > 0) { "생성된 게시 파일이 없습니다." }
                            val uri = FileProvider.getUriForFile(this@InstagramAutomationService, "$packageName.files", file)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = if (post.video.isNotBlank()) "video/mp4" else "image/jpeg"
                                setPackage("com.instagram.android")
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_TEXT, post.caption)
                                clipData = ClipData.newRawUri("content", uri)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(send)
                            onStatus("생성한 파일을 인스타그램 앱으로 전달 · 게시 화면 진행 중")
                            phase = "COMPOSE"
                            lastClickAt = System.currentTimeMillis()
                            continue
                        }
                        if (post == null && !loginFilled) {
                            val fields = nodes.filter { it.isEditable }
                            val password = fields.singleOrNull { it.isPassword || it.viewIdResourceName.orEmpty().contains("password", ignoreCase = true) }
                            val identifier = fields.singleOrNull { !it.isPassword && Regex("username|email|login_username|phone", RegexOption.IGNORE_CASE).containsMatchIn(it.viewIdResourceName.orEmpty()) }
                            if (identifier != null && password != null) {
                                val savedPassword = store.secret("instagram_password")
                                if (savedPassword.isNotBlank()) {
                                    val userOk = identifier.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, username) })
                                    val passwordOk = password.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, savedPassword) })
                                    loginFilled = userOk && passwordOk
                                }
                            }
                        }
                        if (!ownProfile && nodes.none { it.isPassword || it.isEditable }) {
                            val profileTab = nodes.singleOrNull { it.isEnabled && it.isClickable && it.viewIdResourceName.orEmpty().endsWith(":id/profile_tab") }
                            if (profileTab != null && System.currentTimeMillis() - lastClickAt > 3000) {
                                profileTab.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                lastClickAt = System.currentTimeMillis()
                            }
                        }
                        continue
                    }
                    if (shareAt > 0) {
                        val success = texts.any { Regex("^(게시물|릴스|동영상).{0,12}(공유되었습니다|공유됐습니다|게시되었습니다|공유 완료|게시 완료)[.!]?$|^Your (post|reel|video) has been shared[.!]?$", RegexOption.IGNORE_CASE).matches(it.trim()) }
                        val feedVisible = nodes.any { Regex("row_feed|feed_list|media_group").containsMatchIn(it.viewIdResourceName.orEmpty()) }
                        val captionVisible = nodes.any { !it.isEditable && it.text?.toString()?.trim() == post!!.caption.trim() }
                        if (success || confirmedAt >= shareAt || (feedVisible && captionVisible && texts.any { it.trim().removePrefix("@").equals(username, ignoreCase = true) })) {
                            store.update(postId) { it.copy(status = PostStatus.PUBLISHED, publishedAt = System.currentTimeMillis(), remoteId = "native:$postId", accountId = "native:$username", error = "") }
                            store.event("인스타그램 자동 게시 완료하는 단계 :: $postId")
                            return@withContext
                        }
                        if (System.currentTimeMillis() - shareAt > 120_000) throw IOException("게시 완료 여부를 확인하지 못했습니다. 인스타그램에서 확인해 주세요.")
                        continue
                    }
                    val fingerprint = texts.joinToString("|") + nodes.mapNotNull { it.viewIdResourceName }.joinToString("|")
                    if (System.currentTimeMillis() - lastClickAt < 1300 || (fingerprint == lastFingerprint && lastClickAt > 0)) continue
                    val captionFields = nodes.filter { it.isEditable && !it.isPassword && Regex("caption|문구|설명", RegexOption.IGNORE_CASE).containsMatchIn(it.viewIdResourceName.orEmpty() + " " + it.hintText?.toString().orEmpty() + " " + it.text?.toString().orEmpty() + " " + it.contentDescription?.toString().orEmpty()) }
                    if (captionFields.isEmpty() && !captionEntered) {
                        val prompts = nodes.filter { !it.isEditable && it.isEnabled && Regex("^(문구.*입력|설명.*입력|캡션.*|Write a caption.*|Add a caption.*)$", RegexOption.IGNORE_CASE)
                            .matches(it.text?.toString().orEmpty().ifBlank { it.contentDescription?.toString().orEmpty() }) }
                        val targets = prompts.mapNotNull { node ->
                            var current: AccessibilityNodeInfo? = node
                            var depth = 0
                            while (depth++ < 4) {
                                val candidate = current ?: break
                                if (candidate.isClickable) break
                                current = candidate.parent
                            }
                            current?.takeIf { it.isClickable && it.isEnabled }
                        }.distinctBy { node -> android.graphics.Rect().also { node.getBoundsInScreen(it) }.toShortString() }
                        if (targets.size == 1 && targets.single().performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            lastFingerprint = fingerprint
                            lastClickAt = System.currentTimeMillis()
                            continue
                        }
                    }
                    if (captionFields.size == 1) {
                        val field = captionFields.single()
                        if (field.text?.toString()?.trim() != post!!.caption.trim()) {
                            onStatus("인스타그램 게시 문구 입력 중")
                            check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, post.caption) })) { "인스타그램 문구를 입력하지 못했습니다." }
                            lastClickAt = System.currentTimeMillis()
                            continue
                        }
                        captionEntered = true
                    }
                    val names = when {
                        captionEntered -> listOf("공유", "Share", "게시", "Post")
                        !destinationChosen && post!!.format == "REELS" -> listOf("릴스", "Reels")
                        !destinationChosen -> listOf("피드", "Feed")
                        else -> listOf("다음", "Next")
                    }
                    var candidates = nodes.filter { it.isEnabled && (it.text?.toString()?.trim() in names || it.contentDescription?.toString()?.trim() in names) }
                    if (candidates.isEmpty() && !captionEntered) {
                        candidates = nodes.filter { it.isEnabled && (it.text?.toString()?.trim() in listOf("다음", "Next") || it.contentDescription?.toString()?.trim() in listOf("다음", "Next")) }
                    }
                    val buttons = candidates.mapNotNull { node ->
                        var current: AccessibilityNodeInfo? = node
                        var depth = 0
                        while (depth++ < 4) {
                            val candidate = current ?: break
                            if (candidate.isClickable) break
                            current = candidate.parent
                        }
                        current?.takeIf { it.isClickable && it.isEnabled }
                    }.distinctBy { node -> android.graphics.Rect().also { node.getBoundsInScreen(it) }.toShortString() }
                    if (buttons.size != 1) continue
                    val button = buttons.single()
                    if (captionEntered) {
                        check(captionFields.size == 1 && captionFields.single().text?.toString()?.trim() == post!!.caption.trim()) { "게시 전 문구를 확인하지 못했습니다." }
                        check(stopping == epoch && (manual || (store.settings().enabled && store.settings().autoPublish))) { "게시 전 자동화가 중지되었습니다." }
                        store.update(postId) { it.copy(configureStarted = true) }
                        shareAt = System.currentTimeMillis()
                        check(button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "인스타그램 게시 버튼을 누르지 못했습니다." }
                        onStatus("인스타그램 게시 완료 확인 대기 중")
                    } else {
                        val label = button.text?.toString().orEmpty() + " " + button.contentDescription?.toString().orEmpty() + " " + candidates.joinToString { it.text?.toString().orEmpty() }
                        if (Regex("다음|Next").containsMatchIn(label)) {
                            check(++nextCount <= 5) { "인스타그램 게시 단계를 확인하지 못했습니다." }
                            destinationChosen = true
                        } else destinationChosen = true
                        check(button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "인스타그램 게시 화면을 진행하지 못했습니다." }
                    }
                    lastFingerprint = fingerprint
                    lastClickAt = System.currentTimeMillis()
                }
                if (post == null) store.changeSettings { it.copy(connected = false, enabled = false) }
                throw IOException(if (post == null) "인스타그램 앱에서 로그인·추가 인증을 마치고, 본인 프로필을 연 뒤 다시 연결해 주세요." else "인스타그램 게시 화면을 확인하지 못했습니다. 현재 화면을 확인한 뒤 다시 시도해 주세요.")
            } finally { flowPost = "" }
        }
    }
}

class StudioWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    companion object { private val gate = Mutex() }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("loop-work", "콘텐츠 자동 생성·게시", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getActivity(applicationContext, 31, Intent(applicationContext, MainActivity::class.java)
            .putExtra("stop_automation", true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, "loop-work").setSmallIcon(R.drawable.ic_loop)
            .setContentTitle("루프 스튜디오 작업 중").setContentText("사진·영상 생성 또는 인스타그램 게시를 진행합니다.")
            .setOngoing(true).addAction(0, "작업 중지", stop).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(31, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(31, notification)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (inputData.getString("action") == "pulse") {
            StudioJobs.enqueue(applicationContext, "tick")
            return@withContext Result.success()
        }
        gate.withLock {
            val store = LocalStore.get(applicationContext)
            val before = store.posts().associateBy { it.id }
            val startedAt = System.currentTimeMillis()
            var phase = if (inputData.getString("action") == "run_now") "즉시 실행 조건 확인 중" else "대기·예약 작업 확인 중"
            var phaseAt = startedAt
            val progressGate = Mutex()
            val engine = StudioEngine(store) { message ->
                progressGate.withLock {
                    phase = message
                    phaseAt = System.currentTimeMillis()
                    setProgress(workDataOf("phase" to phase, "phaseAt" to phaseAt, "startedAt" to startedAt, "at" to phaseAt))
                }
            }
            val heartbeat = CoroutineScope(currentCoroutineContext()).launch {
                while (true) {
                    progressGate.withLock { setProgress(workDataOf("phase" to phase, "phaseAt" to phaseAt, "startedAt" to startedAt, "at" to System.currentTimeMillis())) }
                    delay(2000)
                }
            }
            try {
                if (inputData.getString("action") != "sync" && (store.settings().enabled || inputData.getString("action") in listOf("generate", "publish", "run_now"))) setForeground(getForegroundInfo())
                val staleBefore = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15)
                store.posts().filter { it.touchedAt < staleBefore && it.status in listOf(PostStatus.GENERATING, PostStatus.PUBLISHING) }.forEach { post ->
                    store.update(post.id) { it.copy(status = if (it.configureStarted) PostStatus.REVIEW else PostStatus.FAILED,
                        error = if (it.configureStarted) "이전 게시 작업이 중단되었습니다. 인스타그램에서 게시 여부를 확인해 주세요." else "이전 작업이 중단되었습니다. 저장된 내용을 확인해 주세요.") }
                }
                when (inputData.getString("action")) {
                    "run_now" -> {
                        val id = inputData.getString("post").orEmpty()
                        try {
                            val current = store.settings()
                            require(store.hasSecret("openai") && current.brand.isNotBlank()) { "브랜드와 OpenAI API 키를 먼저 저장해 주세요." }
                            require(current.connected) { "게시할 인스타그램 계정을 먼저 연결해 주세요." }
                            require(InstagramAutomationService.active != null) { "인스타그램 자동 게시 화면 조작 권한을 켜 주세요." }
                            val keyguard = applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                            val power = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                            require(!keyguard.isKeyguardLocked && power.isInteractive) { "휴대폰 잠금을 해제한 뒤 실행해 주세요." }
                            engine.generate(id)
                            currentCoroutineContext().ensureActive()
                            val generated = store.posts().firstOrNull { it.id == id } ?: throw IOException("실행할 콘텐츠가 없습니다.")
                            if (generated.status == PostStatus.DRAFT) engine.publish(id, manual = true)
                            else check(generated.status in listOf(PostStatus.FAILED, PostStatus.REVIEW, PostStatus.PUBLISHED)) { "이전 작업이 중단되어 진행 상태를 복구할 수 없습니다. 콘텐츠 상태를 확인한 뒤 다시 실행해 주세요." }
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (error: Exception) {
                            store.update(id) { it.copy(status = if (it.configureStarted) PostStatus.REVIEW else PostStatus.FAILED, error = error.message.orEmpty().take(300)) }
                            throw error
                        }
                    }
                    "generate" -> engine.generate(inputData.getString("post").orEmpty())
                    "publish" -> engine.publish(inputData.getString("post").orEmpty(), manual = true)
                    "sync" -> engine.sync()
                    else -> {
                        val settings = store.settings()
                        if (!settings.enabled) return@withLock Result.success(workDataOf("outcome" to "WAITING", "phase" to "자동화가 꺼져 있어 실행하지 않았습니다.", "at" to System.currentTimeMillis()))
                        store.posts().filter { it.status == PostStatus.SCHEDULED && it.scheduledAt <= System.currentTimeMillis() }
                            .sortedBy { it.scheduledAt }.forEach { post ->
                                currentCoroutineContext().ensureActive()
                                try { engine.publish(post.id) }
                                catch (cancel: CancellationException) { throw cancel }
                                catch (error: Exception) { store.event("예약 게시 확인하는 단계 :: ${error.message.orEmpty().take(230)}") }
                            }
                        store.posts().filter { it.status == PostStatus.QUEUED }.forEach { currentCoroutineContext().ensureActive(); engine.generate(it.id) }
                        val current = store.settings()
                        if (current.enabled && current.autoGenerate && store.hasSecret("openai") && current.brand.isNotBlank()) {
                            val now = LocalDateTime.now()
                            val todaySlot = now.toLocalDate().atTime(current.hour, current.minute)
                            val slot = if (todaySlot.isAfter(now.plusMinutes(10))) todaySlot else todaySlot.plusDays(1)
                            val slotKey = "${current.accountId.ifBlank { "local" }}:${slot.toLocalDate()}"
                            if (store.posts().none { it.automaticSlot == slotKey }) {
                                val post = StudioPost(brief = "${slot.toLocalDate()}에 게시할 ${current.brand}의 새로운 콘텐츠",
                                    format = "AUTO", scheduledAt = slot.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), automaticSlot = slotKey, accountId = current.accountId)
                                store.save(post)
                                engine.generate(post.id)
                            }
                        }
                    }
                }
                val changed = store.posts().filter { it != before[it.id] }
                val failed = changed.firstOrNull { it.status in listOf(PostStatus.FAILED, PostStatus.REVIEW) }
                val waiting = changed.firstOrNull { it.status == PostStatus.SCHEDULED && it.error.isNotBlank() }
                val published = changed.count { it.status == PostStatus.PUBLISHED }
                val generated = changed.count { it.image.isNotBlank() && it.caption.isNotBlank() &&
                    (it.image != before[it.id]?.image || it.video != before[it.id]?.video || it.caption != before[it.id]?.caption) }
                val message = when {
                    failed != null -> failed.error.ifBlank { "작업을 완료하지 못했습니다. 콘텐츠의 오류를 확인해 주세요." }
                    waiting != null -> waiting.error
                    inputData.getString("action") == "sync" -> "인스타그램 계정 확인을 완료했습니다."
                    generated > 0 || published > 0 -> "작업 완료 · 콘텐츠 생성 ${generated}개 · 게시 ${published}개"
                    else -> "처리할 대기 작업이 없습니다. 기존 예약은 설정한 시각에 실행됩니다."
                }
                Result.success(workDataOf("outcome" to if (failed != null) "ERROR" else if (waiting != null) "WAITING" else "DONE", "phase" to message, "at" to System.currentTimeMillis()))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                store.event("백그라운드 작업 확인하는 단계 :: ${error.message.orEmpty().take(230)}")
                Result.success(workDataOf("outcome" to "ERROR", "phase" to (error.message ?: "작업을 완료하지 못했습니다.").take(600), "at" to System.currentTimeMillis()))
            } finally { heartbeat.cancel() }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("stop_automation", false)) {
            LocalStore.get(this).changeSettings { it.copy(enabled = false) }
            InstagramAutomationService.active?.stopPosting()
            WorkManager.getInstance(this).cancelUniqueWork("loop-pipeline")
            intent.removeExtra("stop_automation")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = LocalStore.get(this)
        if (intent.getBooleanExtra("stop_automation", false)) {
            store.changeSettings { it.copy(enabled = false) }
            InstagramAutomationService.active?.stopPosting()
            WorkManager.getInstance(this).cancelUniqueWork("loop-pipeline")
            intent.removeExtra("stop_automation")
        }
        val initialized = getSharedPreferences("credential_defaults", Context.MODE_PRIVATE)
        listOf(
            "openai" to BuildConfig.DEFAULT_OPENAI_API_KEY,
            "instagram_username" to BuildConfig.DEFAULT_INSTAGRAM_USERNAME,
            "instagram_password" to BuildConfig.DEFAULT_INSTAGRAM_PASSWORD
        ).forEach { (name, value) ->
            if (!initialized.getBoolean(name, false) && value.isNotBlank()) {
                if (!store.hasSecret(name)) store.putSecret(name, value)
                check(initialized.edit().putBoolean(name, true).commit()) { "기본 설정을 저장하지 못했습니다." }
            }
        }
        StudioJobs.periodic(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(primary = Purple, onPrimary = Color.White, background = Paper,
                    surface = Color.White, onSurface = Ink, onBackground = Ink, outline = Line,
                    secondary = Color(0xFF3C9272), secondaryContainer = Mint),
                typography = Typography(bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
                    bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 20.sp))
            ) { StudioApp() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioApp() {
    val context = LocalContext.current
    val store = remember { LocalStore.get(context) }
    val revision by store.changes.collectAsState()
    val settings = remember(revision) { store.settings() }
    val posts = remember(revision) { store.posts() }
    val events = remember(revision) { store.events() }
    val hasKey = remember(revision) { store.hasSecret("openai") }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var composer by rememberSaveable { mutableStateOf(false) }
    var composerImage by rememberSaveable { mutableStateOf("") }
    var composerDate by rememberSaveable { mutableStateOf("") }
    var composerFormat by rememberSaveable { mutableStateOf("AUTO") }
    var editorId by rememberSaveable { mutableStateOf("") }
    var webLogin by remember { mutableStateOf(false) }
    val report: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }
    val runTask: (suspend () -> String) -> Unit = { action ->
        if (!busy) scope.launch {
            busy = true
            try {
                val message = withContext(Dispatchers.IO) { action() }
                report(message)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                report(error.message ?: "작업을 완료하지 못했습니다.")
            } finally { busy = false }
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runTask {
            val snapshot = store.posts()
            val backup = JSONObject().put("version", 1).put("exportedAt", Instant.now().toString())
                .put("settings", store.settings().copy(enabled = false, connected = false).json())
                .put("posts", JSONArray().apply { snapshot.forEach { put(it.json()) } })
            val output = context.contentResolver.openOutputStream(uri) ?: throw IOException("저장 위치를 열지 못했습니다.")
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("studio.json")); zip.write(backup.toString(2).toByteArray(Charsets.UTF_8)); zip.closeEntry()
                snapshot.flatMap { listOf(it.image, it.video) }.filter { it.isNotBlank() }.distinct().forEach { name ->
                    val file = store.media(name)
                    if (file.exists()) {
                        zip.putNextEntry(ZipEntry("media/$name")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    }
                }
            }
            "콘텐츠와 이미지를 내보냈습니다. API 키와 로그인 세션은 포함하지 않았습니다."
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runTask {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "이미지 파일을 선택해 주세요." }
            var sample = 1
            while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 2048) sample *= 2
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: throw IOException("사진을 읽지 못했습니다.")
            val orientation = context.contentResolver.openInputStream(uri)?.use {
                runCatching { android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
            } ?: 1
            val matrix = android.graphics.Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(270f); postScale(-1f, 1f) }
                    8 -> setRotate(270f)
                }
            }
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val ratio = if (composerFormat == "STORY") 9f / 16f else 1f
            val width = min(rotated.width, (rotated.height * ratio).toInt())
            val height = min(rotated.height, (rotated.width / ratio).toInt())
            val cropped = Bitmap.createBitmap(rotated, (rotated.width - width) / 2, (rotated.height - height) / 2, width, height)
            val filename = "import-${UUID.randomUUID()}.jpg"
            store.media(filename).outputStream().use { check(cropped.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
            if (cropped !== rotated) cropped.recycle()
            if (rotated !== decoded) rotated.recycle()
            decoded.recycle()
            withContext(Dispatchers.Main) {
                if (composerImage.isNotBlank()) store.media(composerImage).delete()
                composerImage = filename
            }
            "사진을 가져왔습니다. GPT가 사진에 맞는 문구를 만듭니다."
        }
    }
    val toggleAutomation: () -> Unit = {
        if (settings.enabled) {
            store.changeSettings { it.copy(enabled = false) }
            InstagramAutomationService.active?.stopPosting()
            report("자동화를 일시정지했습니다.")
        } else if (!hasKey || settings.brand.isBlank() || (settings.autoPublish && !settings.connected)) {
            tab = 4
            report("브랜드와 API 키를 저장하고, 자동 게시를 사용할 인스타그램 계정을 연결해 주세요.")
        } else if (settings.autoPublish && InstagramAutomationService.active == null) {
            webLogin = true
            report("인스타그램 자동 게시 화면 조작 권한을 켜 주세요.")
        } else {
            store.changeSettings { it.copy(enabled = true) }
            posts.filter { it.status == PostStatus.SCHEDULED }.forEach { StudioJobs.schedule(context, it) }
            StudioJobs.enqueue(context, "tick")
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            report("자동화를 시작했습니다.")
        }
    }
    val openComposer: () -> Unit = { composerDate = ""; composer = true }

    Scaffold(
        containerColor = Paper,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Purple), contentAlignment = Alignment.Center) {
                    Text("L", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(7.dp).background(Mint, CircleShape))
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("loop studio", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, letterSpacing = (-0.6).sp)
                    Text("INSTAGRAM ON AUTOPILOT", color = Muted, fontSize = 8.sp, letterSpacing = 1.sp)
                }
                Surface(color = Color.White, shape = RoundedCornerShape(30.dp), modifier = Modifier.clickable { tab = 4 }) {
                    Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Glyph("lock", Muted, Modifier.size(13.dp))
                        Text(" 폰에 저장", color = Muted, fontSize = 10.sp)
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = Color.White, shadowElevation = 10.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 11.dp)) {
                    listOf("home" to "홈", "grid" to "콘텐츠", "calendar" to "캘린더", "spark" to "자동화", "settings" to "설정").forEachIndexed { index, item ->
                        Column(Modifier.weight(1f).clickable { tab = index }.padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(width = 44.dp, height = 30.dp).background(if (tab == index) Purple.copy(alpha = 0.09f) else Color.Transparent, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                                Glyph(item.first, if (tab == index) Purple else Muted, Modifier.size(21.dp))
                            }
                            Text(item.second, color = if (tab == index) Purple else Muted, fontSize = 10.sp, fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> HomeScreen(settings, posts, hasKey, openComposer, toggleAutomation, { tab = 4 }, { editorId = it }, { StudioJobs.enqueue(context, "sync"); report("인스타그램 앱에서 계정을 확인합니다.") })
                1 -> ContentScreen(posts, openComposer) { editorId = it }
                2 -> CalendarScreen(posts, { date -> composerDate = date; composer = true }) { editorId = it }
                3 -> AutomationScreen(settings, posts, events, toggleAutomation, { generate, publish ->
                    store.changeSettings { it.copy(autoGenerate = generate, autoPublish = publish) }
                    if (!publish) InstagramAutomationService.active?.stopPosting()
                }, {
                    runTask {
                        val current = store.settings()
                        val manager = WorkManager.getInstance(context)
                        require(manager.getWorkInfosForUniqueWork("loop-pipeline").get().none { !it.state.isFinished }) { "진행 중인 작업이 있습니다. 아래 실행 상태를 확인해 주세요." }
                        val post = StudioPost(brief = "${current.brand}에 맞는 새로운 콘텐츠를 생성하고 지금 게시하세요.", format = "AUTO", accountId = current.accountId)
                        store.save(post)
                        StudioJobs.enqueue(context, "run_now", post.id)
                        "새 콘텐츠 생성과 즉시 게시를 요청했습니다. 아래 실행 상태를 확인해 주세요."
                    }
                }, { id ->
                    runTask {
                        InstagramAutomationService.active?.stopPosting()
                        WorkManager.getInstance(context).cancelWorkById(id).result.get()
                        "작업 중지를 요청했습니다."
                    }
                })
                else -> SettingsScreen(store, settings, hasKey, busy, runTask, { webLogin = true }, {
                    export.launch("loop-studio-${LocalDate.now()}.zip")
                }, { intent -> runCatching { context.startActivity(intent) }.onFailure { report("연결할 앱이 없습니다.") } })
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = Purple, trackColor = Purple.copy(alpha = 0.1f))
        }
    }

    if (composer) {
        ComposerSheet(settings, hasKey, composerImage, composerFormat, composerDate, busy,
            onFormat = { if (composerImage.isBlank()) composerFormat = it else report("사진을 제거한 뒤 형식을 변경해 주세요.") },
            onPick = { pickImage.launch("image/*") },
            onRemove = { if (composerImage.isNotBlank()) store.media(composerImage).delete(); composerImage = "" },
            onSettings = { composer = false; tab = 4 },
            onDismiss = { if (!busy) { if (composerImage.isNotBlank()) store.media(composerImage).delete(); composerImage = ""; composer = false } },
            onCreate = { brief, count, at ->
                try {
                    require(hasKey && settings.brand.isNotBlank()) { "설정에서 브랜드와 API 키를 저장해 주세요." }
                    require(composerImage.isBlank() || count == 1) { "선택한 사진으로는 한 개의 콘텐츠를 생성해 주세요." }
                    val start = if (at.isBlank()) null else LocalDateTime.parse(at, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    require(start == null || start.isAfter(LocalDateTime.now())) { "예약 시각은 현재보다 이후로 입력해 주세요." }
                    repeat(count) { index ->
                        val date = start?.plusDays(index.toLong())?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0L
                        val post = StudioPost(brief = brief.ifBlank { "${settings.brand}에 맞는 새로운 콘텐츠" } + if (count > 1) " · 서로 다른 주제 ${index + 1}/$count" else "",
                            format = composerFormat, image = composerImage, scheduledAt = date, accountId = settings.accountId)
                        store.save(post)
                        StudioJobs.enqueue(context, "generate", post.id)
                    }
                    composerImage = ""; composer = false; tab = 1
                    report("${count}개 콘텐츠 생성을 시작했습니다. 앱을 닫아도 작업을 이어갑니다.")
                } catch (error: Exception) { report(error.message ?: "예약 날짜를 yyyy-MM-dd HH:mm 형식으로 입력해 주세요.") }
            })
    }
    posts.firstOrNull { it.id == editorId }?.let { post ->
        PostEditor(post, settings, store, runTask, report, onDismiss = { editorId = "" })
    }
    if (webLogin) {
        InstagramLogin(store, onDismiss = { webLogin = false }, onConnected = { webLogin = false })
    }
}

@Composable
private fun HomeScreen(settings: StudioSettings, posts: List<StudioPost>, hasKey: Boolean, create: () -> Unit,
    toggle: () -> Unit, setup: () -> Unit, openPost: (String) -> Unit, sync: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 23.dp, end = 23.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(21.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (settings.brand.isBlank()) "당신의 브랜드를 위한" else "${settings.brand}의 새로운 하루", color = Muted, fontSize = 12.sp)
                    Text("나의 소셜 스튜디오", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Ink, letterSpacing = (-1).sp)
                }
                Box(Modifier.size(43.dp).clip(CircleShape).background(Color.White).clickable(onClick = setup), contentAlignment = Alignment.Center) {
                    Glyph("account", Purple, Modifier.size(22.dp))
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp)).background(Brush.linearGradient(listOf(Color(0xFF8270ED), Color(0xFF5C4AC7))))) {
                Canvas(Modifier.matchParentSize()) {
                    drawCircle(Color.White.copy(alpha = 0.07f), size.width * 0.40f, Offset(size.width, size.height * 0.2f))
                    drawCircle(Color.White.copy(alpha = 0.07f), size.width * 0.24f, Offset(size.width * 0.95f, size.height))
                }
                Column(Modifier.padding(23.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Color.White.copy(alpha = 0.16f), shape = RoundedCornerShape(30.dp)) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(5.dp).background(Mint, CircleShape))
                                Text(if (settings.enabled) "  AI 매니저가 일하고 있어요" else "  나만의 AI 인스타그램 매니저", color = Color.White, fontSize = 10.sp)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Glyph("spark", Mint, Modifier.size(25.dp))
                    }
                    Text("좋은 콘텐츠는 AI가.\n당신은 브랜드에 집중하세요.", color = Color.White, fontSize = 23.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
                    Text("아이디어부터 이미지, 문구, 예약 게시까지.\n내 휴대폰에서 시작하는 똑똑한 인스타 운영.", color = Color(0xFFE8E2FF), fontSize = 11.sp, lineHeight = 18.sp)
                    Button(onClick = toggle, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Purple), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                        Glyph(if (settings.enabled) "pause" else "play", Purple, Modifier.size(15.dp))
                        Text(if (settings.enabled) "  자동화 일시정지" else "  내 피드 자동화하기", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.width(18.dp)); Glyph("arrow", Purple, Modifier.size(15.dp))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Triple("clock", posts.count { it.status == PostStatus.SCHEDULED }.toString(), "예약 콘텐츠"),
                    Triple("photo", posts.count { it.status == PostStatus.DRAFT }.toString(), "보관한 초안"),
                    Triple("check", posts.count { it.status == PostStatus.PUBLISHED }.toString(), "게시 완료")).forEach { stat ->
                    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Glyph(stat.first, Purple, Modifier.size(17.dp))
                            Text(stat.second, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text(stat.third, color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        item {
            SectionTitle("피드의 다음 장", "AI로 만들기", create)
            Spacer(Modifier.height(12.dp))
            val next = posts.filter { it.status != PostStatus.PUBLISHED }.take(5)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (next.isEmpty()) {
                    items(3) { index ->
                        Surface(shape = RoundedCornerShape(17.dp), color = Color.White, modifier = Modifier.width(147.dp).clickable(onClick = create)) {
                            Column {
                                Box {
                                    Image(painterResource(listOf(R.drawable.preview_coffee, R.drawable.preview_space, R.drawable.preview_brand)[index]),
                                        contentDescription = "콘텐츠 스타일 미리보기", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(166.dp))
                                    Surface(color = Color.White.copy(alpha = 0.9f), shape = RoundedCornerShape(6.dp), modifier = Modifier.padding(9.dp)) {
                                        Text("스타일 예시", fontSize = 8.sp, color = Ink, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                                    }
                                }
                                Column(Modifier.padding(12.dp)) {
                                    Text(listOf("작은 순간의 취향", "브랜드의 분위기", "당신의 다음 이야기")[index], fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text("내 브랜드로 만들어 보기 ↗", fontSize = 9.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                } else items(next, key = { it.id }) { post -> PostTile(post, Modifier.width(155.dp)) { openPost(post.id) } }
            }
        }
        if (!hasKey || !settings.connected || settings.brand.isBlank()) item {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
                Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("시작은 세 가지만", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${listOf(hasKey, settings.connected, settings.brand.isNotBlank()).count { it }}/3", color = Purple, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    listOf(Triple("key", "내 OpenAI 키 연결", hasKey), Triple("account", "인스타그램 로그인", settings.connected),
                        Triple("spark", "브랜드와 운영 주제 설정", settings.brand.isNotBlank())).forEach { item ->
                        Row(Modifier.fillMaxWidth().clickable(onClick = setup), verticalAlignment = Alignment.CenterVertically) {
                            Glyph(if (item.third) "check" else item.first, if (item.third) Color(0xFF3C9272) else Muted, Modifier.size(18.dp))
                            Text(item.second, fontSize = 12.sp, color = if (item.third) Muted else Ink, modifier = Modifier.weight(1f).padding(start = 11.dp))
                            Glyph("chevron", Muted, Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
        if (settings.connected) item {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
                Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    SectionTitle("@${settings.username}", "계정 확인", sync)
                    if (settings.syncedAt == 0L) Text("인스타그램 앱에 연결되었습니다. 계정 성과는 인스타그램에서 확인해 주세요.", color = Muted, fontSize = 12.sp)
                    else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf("팔로워" to settings.followers, "최근 좋아요" to settings.recentLikes, "최근 댓글" to settings.recentComments).forEach { item ->
                                Column { Text("%,d".format(item.second), fontSize = 21.sp, fontWeight = FontWeight.Bold); Text(item.first, color = Muted, fontSize = 10.sp) }
                            }
                        }
                        Text("최근 최대 24개 게시물 기준 · " + Instant.ofEpochMilli(settings.syncedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM.dd HH:mm")), fontSize = 9.sp, color = Muted)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Glyph("lock", Muted, Modifier.size(12.dp)); Text("  내 콘텐츠는 내 휴대폰에", color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ContentScreen(posts: List<StudioPost>, create: () -> Unit, openPost: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("전체") }
    val filtered = posts.filter { post -> when (filter) {
        "초안" -> post.status == PostStatus.DRAFT
        "예약" -> post.status == PostStatus.SCHEDULED
        "완료" -> post.status == PostStatus.PUBLISHED
        "확인" -> post.status in listOf(PostStatus.FAILED, PostStatus.REVIEW)
        else -> true
    } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 23.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("콘텐츠 라이브러리", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold); Text("만들고, 다듬고, 내 피드로 보내세요.", color = Muted, fontSize = 12.sp) }
                FilledIconButton(onClick = create, shape = RoundedCornerShape(14.dp)) { Glyph("plus", Color.White, Modifier.size(22.dp)) }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("전체", "초안", "예약", "완료", "확인").forEach { name -> FilterChip(selected = filter == name, onClick = { filter = name }, label = { Text(name, fontSize = 11.sp) }) }
            }
        }
        if (filtered.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 25.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Box(Modifier.size(128.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFFEDE8FA)), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.preview_brand), contentDescription = "첫 콘텐츠 만들기", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Text(if (posts.isEmpty()) "첫 콘텐츠를 만들어 볼까요?" else "이 상태의 콘텐츠가 없습니다.", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("주제 한 줄이면 이미지와 문구가 함께 완성됩니다.", color = Muted, fontSize = 11.sp)
                Button(onClick = create, shape = RoundedCornerShape(12.dp)) { Glyph("spark", Color.White, Modifier.size(17.dp)); Text("  AI 콘텐츠 만들기", fontSize = 12.sp) }
            }
        }
        items(filtered.chunked(2), key = { it.first().id }) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { post -> PostTile(post, Modifier.weight(1f)) { openPost(post.id) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PostTile(post: StudioPost, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(17.dp), modifier = modifier.clickable(onClick = onClick)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(0.92f).background(Color(0xFFEDEAF7))) {
                LocalImage(post.image, post.alt.ifBlank { post.title }, Modifier.fillMaxSize())
                if (post.status in listOf(PostStatus.QUEUED, PostStatus.GENERATING)) {
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.65f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text(post.status.title, color = Purple, fontSize = 10.sp)
                        }
                    }
                }
                StatusPill(post.status, Modifier.padding(9.dp))
                Text(if (post.format == "REELS") "REELS" else if (post.format == "STORY") "STORY" else "FEED", color = Ink, fontSize = 8.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(9.dp).background(Color.White.copy(alpha = 0.88f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(post.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val time = if (post.status == PostStatus.PUBLISHED) post.publishedAt else post.scheduledAt
                Text(if (time > 0) Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM.dd · HH:mm")) else "내 휴대폰에 보관 중", fontSize = 9.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun CalendarScreen(posts: List<StudioPost>, create: (String) -> Unit, openPost: (String) -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var dayText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.parse(dayText)
    val planned = posts.filter { it.scheduledAt > 0 || it.publishedAt > 0 }
    val selectedPosts = planned.filter { Instant.ofEpochMilli(if (it.publishedAt > 0) it.publishedAt else it.scheduledAt).atZone(ZoneId.systemDefault()).toLocalDate() == selected }.sortedBy { it.scheduledAt }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 23.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("콘텐츠 캘린더", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold); Text("한눈에 보는 나의 인스타그램 플랜", color = Muted, fontSize = 12.sp) }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { monthText = month.minusMonths(1).toString() }) { Text("‹", fontSize = 26.sp, color = Muted) }
                        Text("${month.year}년 ${month.monthValue}월", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { monthText = YearMonth.now().toString(); dayText = LocalDate.now().toString() }) { Text("오늘", fontSize = 11.sp) }
                        IconButton(onClick = { monthText = month.plusMonths(1).toString() }) { Text("›", fontSize = 26.sp, color = Muted) }
                    }
                    Row { listOf("월", "화", "수", "목", "금", "토", "일").forEach { Text(it, color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
                    val offset = month.atDay(1).dayOfWeek.value - 1
                    val cells = ((offset + month.lengthOfMonth() + 6) / 7) * 7
                    (0 until cells).chunked(7).forEach { week ->
                        Row {
                            week.forEach { index ->
                                val day = index - offset + 1
                                val date = if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
                                val count = if (date == null) 0 else planned.count { Instant.ofEpochMilli(if (it.publishedAt > 0) it.publishedAt else it.scheduledAt).atZone(ZoneId.systemDefault()).toLocalDate() == date }
                                Column(Modifier.weight(1f).height(43.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (date == selected) Purple else if (date == LocalDate.now()) Color(0xFFF0EDF9) else Color.Transparent)
                                    .clickable(enabled = date != null) { dayText = date.toString() }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(if (date == null) "" else day.toString(), fontSize = 12.sp, color = if (date == selected) Color.White else Ink, fontWeight = if (date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal)
                                    if (count > 0) Box(Modifier.padding(top = 4.dp).size(4.dp).background(if (date == selected) Mint else Purple, CircleShape))
                                }
                            }
                        }
                    }
                }
            }
        }
        item { SectionTitle("${selected.monthValue}월 ${selected.dayOfMonth}일의 콘텐츠", "+ 추가", { create(selected.toString()) }) }
        if (selectedPosts.isEmpty()) item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(25.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Glyph("calendar", Purple.copy(alpha = 0.6f), Modifier.size(32.dp))
                    Text("아직 비어 있는 하루예요.", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("새 콘텐츠를 예약해 피드를 이어가세요.", fontSize = 11.sp, color = Muted)
                    TextButton(onClick = { create(selected.toString()) }) { Text("이 날짜에 콘텐츠 만들기", fontSize = 12.sp) }
                }
            }
        }
        items(selectedPosts, key = { it.id }) { post ->
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth().clickable { openPost(post.id) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    LocalImage(post.image, post.title, Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(post.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); StatusPill(post.status) }
                    Text(Instant.ofEpochMilli(if (post.publishedAt > 0) post.publishedAt else post.scheduledAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")), color = Purple, fontSize = 12.sp)
                }
            }
        }
        item { Text("예약은 휴대폰 시간대 기준입니다. 절전·네트워크 상태에 따라 실행이 지연될 수 있습니다.", color = Muted, fontSize = 10.sp, lineHeight = 17.sp) }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun AutomationScreen(settings: StudioSettings, posts: List<StudioPost>, events: List<Pair<Long, String>>, toggle: () -> Unit,
    changeModes: (Boolean, Boolean) -> Unit, run: () -> Unit, stop: (UUID) -> Unit) {
    val context = LocalContext.current
    val workInfos by remember(context) { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow("loop-pipeline") }.collectAsState(initial = emptyList())
    val work = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
        ?: workInfos.filter { info -> info.tags.any { it.startsWith("loop-request-") } }
            .maxByOrNull { info -> info.tags.mapNotNull { it.removePrefix("loop-request-").toLongOrNull() }.maxOrNull() ?: 0L }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(work?.id, work?.state) {
        while (work?.state == WorkInfo.State.RUNNING) { clock = System.currentTimeMillis(); delay(1000) }
    }
    val running = work?.state == WorkInfo.State.RUNNING
    val waiting = work?.state in listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED)
    val details = if (running) work?.progress else work?.outputData
    val refreshedAt = details?.getLong("at", 0L) ?: 0L
    val stale = running && refreshedAt > 0 && clock - refreshedAt > 15_000
    val error = work?.state == WorkInfo.State.FAILED || details?.getString("outcome") == "ERROR"
    val status = when {
        work == null -> "실행 기록 없음"
        stale -> "상태 갱신 끊김 · 확인 필요"
        running -> "실제 작업 실행 중"
        waiting -> "실행 시작 대기 중"
        work.state == WorkInfo.State.CANCELLED -> "작업 중지됨"
        error -> "오류로 완료하지 못함"
        details?.getString("outcome") == "WAITING" -> "작업 대기"
        else -> "실행 완료"
    }
    val statusColor = if (error || stale) Color(0xFFB85646) else if (running) Purple else Muted
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 23.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("나의 AI 매니저", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold); Text("한 번 설정하고, 매일의 운영을 맡기세요.", color = Muted, fontSize = 12.sp) }
        item {
            Surface(shape = RoundedCornerShape(23.dp), color = Ink) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(19.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(45.dp).background(Purple, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { Glyph("spark", Mint, Modifier.size(25.dp)) }
                        Column(Modifier.weight(1f).padding(start = 13.dp)) {
                            Text(if (settings.enabled) "예약 자동화 켜짐" else "예약 자동화 꺼짐", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("지금 실행은 예약 설정과 관계없이 생성 후 바로 게시합니다.", color = Color(0xFFAEADC4), fontSize = 10.sp)
                        }
                        Box(Modifier.size(8.dp).background(if (settings.enabled) Mint else Muted, CircleShape))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("기획" to "주제 선택", "제작" to "문구 + 이미지", "게시" to "%02d:%02d".format(settings.hour, settings.minute)).forEachIndexed { i, item ->
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("0${i + 1} / ${item.first}", color = Mint, fontSize = 10.sp)
                                Text(item.second, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Button(onClick = toggle, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = if (settings.enabled) Color(0xFF3A3C53) else Mint, contentColor = if (settings.enabled) Color.White else Ink)) {
                        Glyph(if (settings.enabled) "pause" else "play", if (settings.enabled) Color.White else Ink, Modifier.size(15.dp))
                        Text(if (settings.enabled) "  자동화 일시정지" else "  자동화 시작", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("어디까지 맡길까요?", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("매일 콘텐츠 자동 생성", fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text("하루 1개, 브랜드에 맞는 새로운 이야기", fontSize = 10.sp, color = Muted) }
                        Switch(checked = settings.autoGenerate, onCheckedChange = { changeModes(it, settings.autoPublish) })
                    }
                    HorizontalDivider(color = Line)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("생성 후 자동 예약", fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text(if (settings.autoPublish) "생성한 콘텐츠를 정한 시각에 자동 게시" else "자동 생성한 콘텐츠를 초안으로 보관", fontSize = 10.sp, color = Muted) }
                        Switch(checked = settings.autoPublish, onCheckedChange = { changeModes(settings.autoGenerate, it) })
                    }
                    Text("자동화를 켜면 직접 예약한 콘텐츠도 게시합니다. 일시정지하면 새 자동 생성과 예약 게시를 멈춥니다. 이미 전송한 요청은 완료될 수 있습니다.", color = Muted, fontSize = 10.sp, lineHeight = 17.sp)
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(19.dp), color = Color(0xFFEFECFA)) {
                Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Glyph("clock", Purple, Modifier.size(21.dp))
                    Column(Modifier.weight(1f).padding(start = 11.dp)) {
                        Text("새 콘텐츠 생성 → 즉시 게시", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("예약 시각을 기다리지 않고 실행합니다.", color = Muted, fontSize = 9.sp)
                    }
                    TextButton(onClick = run, enabled = !running && !waiting) { Text("지금 실행", fontSize = 11.sp) }
                }
                HorizontalDivider(color = Purple.copy(alpha = 0.12f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(statusColor, CircleShape))
                    Text(status, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = statusColor, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    if (running || waiting) TextButton(onClick = { work?.let { stop(it.id) } }) { Text("중지", fontSize = 11.sp) }
                }
                Text(when {
                    waiting -> if (work?.state == WorkInfo.State.BLOCKED) "앞선 작업이 끝나기를 기다리고 있습니다." else "Android가 작업을 시작하기를 기다리고 있습니다."
                    work?.state == WorkInfo.State.CANCELLED -> "사용자 또는 시스템이 작업을 취소했습니다."
                    else -> details?.getString("phase") ?: if (running) "작업을 준비하고 있습니다." else "지금 실행을 누르면 여기에 단계와 결과가 표시됩니다."
                }, color = Ink, fontSize = 11.sp, lineHeight = 18.sp)
                if (running) {
                    if (!stale) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Purple)
                    val startedAt = details?.getLong("startedAt", 0L) ?: 0L
                    val phaseAt = details?.getLong("phaseAt", 0L) ?: 0L
                    if (startedAt > 0) Text("전체 ${((clock - startedAt).coerceAtLeast(0) / 1000)}초 · 현재 단계 ${((clock - phaseAt).coerceAtLeast(0) / 1000)}초", color = Muted, fontSize = 10.sp)
                    if (stale) Text("15초 이상 작업 상태가 갱신되지 않았습니다. 실행 중이라고 단정할 수 없습니다.", color = statusColor, fontSize = 10.sp)
                    else if (phaseAt > 0 && clock - phaseAt > 60_000) Text("현재 단계의 응답을 기다리고 있습니다. 응답 제한 시간을 넘기면 오류가 표시됩니다.", color = Muted, fontSize = 10.sp)
                }
                if (refreshedAt > 0) Text("마지막 상태 갱신 " + Instant.ofEpochMilli(refreshedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm:ss")), color = Muted, fontSize = 9.sp)
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("최근 7일 게시 기록", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    val days = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
                    val counts = days.map { date -> posts.count { it.status == PostStatus.PUBLISHED && it.publishedAt > 0 && Instant.ofEpochMilli(it.publishedAt).atZone(ZoneId.systemDefault()).toLocalDate() == date } }
                    val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
                    Row(Modifier.fillMaxWidth().height(95.dp), horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.Bottom) {
                        days.forEachIndexed { index, day ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(counts[index].toString(), fontSize = 9.sp, color = Muted)
                                Box(Modifier.fillMaxWidth().height((5 + 55 * counts[index] / max).dp).background(if (counts[index] > 0) Purple else Line, RoundedCornerShape(5.dp)))
                                Text(day.format(DateTimeFormatter.ofPattern("M/d")), fontSize = 8.sp, color = Muted)
                            }
                        }
                    }
                }
            }
        }
        item { SectionTitle("운영 기록", "", {}) }
        if (events.isEmpty()) item { Text("작업을 시작하면 실행 결과가 여기에 남습니다.", color = Muted, fontSize = 12.sp) }
        items(events, key = { "${it.first}:${it.second}" }) { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.padding(top = 6.dp).size(6.dp).background(Purple.copy(alpha = 0.45f), CircleShape))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.second, fontSize = 11.sp, color = Ink, lineHeight = 18.sp)
                    Text(Instant.ofEpochMilli(item.first).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM.dd HH:mm")), color = Muted, fontSize = 9.sp)
                }
            }
        }
        item { Text("휴대폰이 꺼져 있거나 앱을 강제 종료하면 실행할 수 없습니다. 절전·네트워크 상태에 따라 예약이 지연될 수 있습니다.", color = Muted, fontSize = 10.sp, lineHeight = 17.sp) }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun SettingsScreen(store: LocalStore, settings: StudioSettings, hasKey: Boolean, busy: Boolean,
    runTask: (suspend () -> String) -> Unit, webLogin: () -> Unit, export: () -> Unit, open: (Intent) -> Unit) {
    var brand by rememberSaveable(settings.brand) { mutableStateOf(settings.brand) }
    var description by rememberSaveable(settings.description) { mutableStateOf(settings.description) }
    var audience by rememberSaveable(settings.audience) { mutableStateOf(settings.audience) }
    var pillars by rememberSaveable(settings.pillars) { mutableStateOf(settings.pillars) }
    var tone by rememberSaveable(settings.tone) { mutableStateOf(settings.tone) }
    var style by rememberSaveable(settings.imageStyle) { mutableStateOf(settings.imageStyle) }
    var time by rememberSaveable(settings.hour, settings.minute) { mutableStateOf("%02d:%02d".format(settings.hour, settings.minute)) }
    var apiKey by remember { mutableStateOf(runCatching { store.secret("openai") }.getOrDefault("")) }
    var showKey by remember { mutableStateOf(false) }
    var instagramUsername by remember { mutableStateOf(runCatching { store.secret("instagram_username") }.getOrDefault("")) }
    var instagramPassword by remember { mutableStateOf(runCatching { store.secret("instagram_password") }.getOrDefault("")) }
    var showInstagramPassword by remember { mutableStateOf(false) }
    var textModel by rememberSaveable(settings.textModel) { mutableStateOf(settings.textModel) }
    var imageModel by rememberSaveable(settings.imageModel) { mutableStateOf(settings.imageModel) }
    var contentMode by rememberSaveable(settings.contentMode) { mutableStateOf(settings.contentMode) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 23.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("스튜디오 설정", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold); Text("내 계정, 내 AI, 내 브랜드.", fontSize = 12.sp, color = Muted) }
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Glyph("account", Purple, Modifier.size(21.dp)); Text("  인스타그램 연결", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                    StudioField(instagramUsername, { instagramUsername = it.trim().removePrefix("@") }, "인스타그램 아이디", single = true, enabled = !busy)
                    OutlinedTextField(instagramPassword, { instagramPassword = it }, label = { Text("비밀번호", fontSize = 12.sp) },
                        visualTransformation = if (showInstagramPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, enabled = !busy,
                        trailingIcon = { TextButton(onClick = { showInstagramPassword = !showInstagramPassword }) { Text(if (showInstagramPassword) "숨김" else "보기", fontSize = 10.sp) } },
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                    if (settings.connected) {
                        Row(Modifier.fillMaxWidth().background(Color(0xFFEDF8F1), RoundedCornerShape(12.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Glyph("check", Color(0xFF348660), Modifier.size(18.dp))
                            Column(Modifier.padding(start = 9.dp).weight(1f)) { Text("@${settings.username}", fontWeight = FontWeight.Bold, fontSize = 14.sp); Text("인스타그램 앱 계정 확인됨", color = Muted, fontSize = 9.sp) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            OutlinedButton(onClick = { StudioJobs.enqueue(store.app, "sync") }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("계정 확인", fontSize = 11.sp) }
                            OutlinedButton(onClick = { runTask {
                                store.changeSettings { it.copy(connected = false, enabled = false, accountId = "", username = "", followers = 0, following = 0, mediaCount = 0, recentLikes = 0, recentComments = 0, syncedAt = 0) }
                                store.putSecret("instagram", ""); store.putSecret("instagram_pending", "")
                                InstagramAutomationService.active?.stopPosting()
                                "계정 연결을 해제하고 자동화를 정지했습니다."
                            } }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("연결 해제", fontSize = 11.sp) }
                        }
                    } else {
                        Text("설치된 인스타그램 앱에서 로그인과 추가 인증을 완료하면 본인 프로필을 확인해 연결합니다.", fontSize = 10.sp, color = Muted, lineHeight = 17.sp)
                    }
                    Button(onClick = { runTask {
                        require(instagramUsername.isNotBlank() && instagramPassword.isNotBlank()) { "인스타그램 아이디와 비밀번호를 입력해 주세요." }
                        store.putSecret("instagram_username", instagramUsername)
                        store.putSecret("instagram_password", instagramPassword)
                        withContext(Dispatchers.Main) { webLogin() }
                        "계정 정보를 저장했습니다."
                    } }, enabled = !busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(if (settings.connected) "인스타그램 다시 연결" else "인스타그램 계정 연결", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Glyph("key", Purple, Modifier.size(21.dp)); Text("  나의 OpenAI", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        if (hasKey) Text("키 저장됨", color = Color(0xFF3C9272), fontSize = 10.sp)
                    }
                    OutlinedTextField(apiKey, { apiKey = it.trim() }, label = { Text(if (hasKey) "새 API 키 입력 시 교체" else "OpenAI API 키 · sk-…", fontSize = 11.sp) },
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, enabled = !busy,
                        trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "숨김" else "보기", fontSize = 10.sp) } },
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                    StudioField(textModel, { textModel = it.trim() }, "문구 생성 모델", single = true, enabled = !busy)
                    StudioField(imageModel, { imageModel = it.trim() }, "이미지 생성 모델", single = true, enabled = !busy)
                    Button(onClick = { runTask {
                        require(textModel.isNotBlank() && imageModel.isNotBlank()) { "모델 이름을 입력해 주세요." }
                        if (apiKey.isNotBlank()) store.putSecret("openai", apiKey)
                        store.changeSettings { it.copy(textModel = textModel, imageModel = imageModel) }
                        OpenAi(store).request("models/" + URLEncoder.encode(textModel, "UTF-8"))
                        "API 키를 저장하고 문구 모델 접근을 확인했습니다. 이미지 모델은 실제 생성 시 확인합니다."
                    } }, enabled = !busy && (hasKey || apiKey.isNotBlank()), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Text("저장 및 연결 확인", fontSize = 12.sp) }
                    if (hasKey) TextButton(onClick = { runTask {
                        store.changeSettings { it.copy(enabled = false) }; store.putSecret("openai", "")
                        withContext(Dispatchers.Main) { apiKey = "" }; "API 키를 삭제하고 자동화를 정지했습니다."
                    } }, enabled = !busy) { Text("저장된 키 삭제", fontSize = 10.sp, color = Muted) }
                    Text("본인 API 키로 직접 호출하며 사용량에 따라 OpenAI 요금이 발생합니다. ChatGPT 구독과 API 결제는 별개입니다.", color = Muted, fontSize = 10.sp, lineHeight = 17.sp)
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Glyph("spark", Purple, Modifier.size(21.dp)); Text("  브랜드 프로필", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                    StudioField(brand, { brand = it }, "브랜드 / 계정 이름", single = true)
                    StudioField(description, { description = it }, "어떤 계정인가요? · 사실 자료와 제품 정보")
                    StudioField(audience, { audience = it }, "누구에게 이야기하나요?", single = true)
                    StudioField(pillars, { pillars = it }, "반복해서 다룰 콘텐츠 주제")
                    StudioField(tone, { tone = it }, "글의 말투", single = true)
                    StudioField(style, { style = it }, "이미지 분위기")
                    StudioField(time, { time = it }, "매일 게시할 시각 · HH:mm", single = true)
                    Text("자동 생성할 콘텐츠", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("PHOTO" to "사진", "VIDEO" to "영상", "MIXED" to "사진·영상").forEach { (value, label) ->
                            FilterChip(selected = contentMode == value, onClick = { contentMode = value }, label = { Text(label, fontSize = 11.sp) })
                        }
                    }
                    Button(onClick = { runTask {
                        require(brand.isNotBlank()) { "브랜드 이름을 입력해 주세요." }
                        val parsed = java.time.LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"))
                        store.changeSettings { it.copy(brand = brand.trim(), description = description.trim(), audience = audience.trim(), pillars = pillars.trim(),
                            tone = tone.trim(), imageStyle = style.trim(), hour = parsed.hour, minute = parsed.minute, contentMode = contentMode) }
                        "브랜드 프로필을 저장했습니다."
                    } }, enabled = !busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Text("브랜드 설정 저장", fontSize = 12.sp) }
                    Text("시각을 바꾸면 이후 생성하는 콘텐츠부터 적용됩니다. 기존 예약은 콘텐츠 화면에서 변경할 수 있습니다.", color = Muted, fontSize = 10.sp, lineHeight = 17.sp)
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
                Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Glyph("lock", Purple, Modifier.size(21.dp)); Text("  휴대폰 로컬 저장", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                    Text("콘텐츠·사진·영상·운영 기록은 이 휴대폰에 저장합니다. 저장한 API 키와 계정 정보는 Android Keystore로 암호화합니다. 인스타그램 로그인 세션은 인스타그램 앱에서 관리합니다.", fontSize = 11.sp, color = Muted, lineHeight = 19.sp)
                    Text("생성 요청과 선택한 사진은 OpenAI로 전송됩니다. 게시할 파일과 문구는 설치된 인스타그램 앱으로 전달합니다. 별도 운영 서버는 사용하지 않습니다.", fontSize = 10.sp, color = Muted, lineHeight = 17.sp)
                    OutlinedButton(onClick = export, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Glyph("download", Purple, Modifier.size(16.dp)); Text("  콘텐츠·이미지 ZIP 내보내기", fontSize = 11.sp)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("참고한 공개 프로젝트", color = Muted, fontSize = 10.sp)
                Text("Metricool · 공개 MCP 소스 ↗", fontSize = 11.sp, color = Purple, modifier = Modifier.clickable { open(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/vicampuzano/metricool-mcp"))) })
                Text("NoimosAI · 공개 연동·운영 자료 ↗", fontSize = 11.sp, color = Purple, modifier = Modifier.clickable { open(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AGOS-Labs/noimosai_skill"))) })
                Text("Instagram Web API · 로그인·업로드 흐름 ↗", fontSize = 11.sp, color = Purple, modifier = Modifier.clickable { open(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jlobos/instagram-web-api"))) })
            }
        }
        item { Text("LOOP STUDIO / 1.0\nMade for your next chapter.", color = Muted, fontSize = 9.sp, lineHeight = 17.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerSheet(settings: StudioSettings, hasKey: Boolean, image: String, format: String, initialDate: String, busy: Boolean,
    onFormat: (String) -> Unit, onPick: () -> Unit, onRemove: () -> Unit, onSettings: () -> Unit, onDismiss: () -> Unit, onCreate: (String, Int, String) -> Unit) {
    var brief by rememberSaveable { mutableStateOf("") }
    var count by rememberSaveable { mutableIntStateOf(1) }
    var schedule by rememberSaveable { mutableStateOf(initialDate.isNotBlank()) }
    var time by rememberSaveable { mutableStateOf("${initialDate.ifBlank { LocalDate.now().plusDays(1).toString() }} %02d:%02d".format(settings.hour, settings.minute)) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Glyph("spark", Purple, Modifier.size(25.dp)); Text("  AI 콘텐츠 만들기", fontSize = 21.sp, fontWeight = FontWeight.Bold) }
            Text("주제만 알려주세요. 문구와 사진·영상을 함께 만듭니다.", color = Muted, fontSize = 11.sp)
            if (!hasKey || settings.brand.isBlank()) {
                Surface(color = Color(0xFFEFECFA), shape = RoundedCornerShape(13.dp)) {
                    Column(Modifier.padding(15.dp)) { Text("브랜드 프로필과 OpenAI 키를 먼저 저장해 주세요.", fontSize = 12.sp); TextButton(onClick = onSettings) { Text("설정하러 가기 →", fontSize = 12.sp) } }
                }
            }
            StudioField(brief, { brief = it }, "어떤 게시물을 만들까요?", minimumLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = format == "FEED", onClick = { onFormat("FEED") }, label = { Text("피드 · 1:1", fontSize = 11.sp) })
                FilterChip(selected = format == "REELS", onClick = { onFormat("REELS") }, label = { Text("영상 · 9:16", fontSize = 11.sp) })
                FilterChip(selected = format == "AUTO", onClick = { onFormat("AUTO") }, label = { Text("자동 선택", fontSize = 11.sp) })
            }
            if (image.isBlank()) {
                OutlinedButton(onClick = onPick, enabled = !busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Glyph("photo", Purple, Modifier.size(17.dp)); Text("  내 사진으로 만들기 · 선택 사항", fontSize = 11.sp)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LocalImage(image, "선택한 사진", Modifier.size(68.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) { Text("내 사진을 사용합니다", fontWeight = FontWeight.SemiBold, fontSize = 12.sp); Text(if (format == "FEED") "사진에 맞는 문구 생성" else "사진을 참고해 콘텐츠 생성", fontSize = 10.sp, color = Muted) }
                    TextButton(onClick = onRemove, enabled = !busy) { Text("제거", fontSize = 11.sp) }
                }
            }
            Text("한 번에 몇 개 만들까요?", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 7).forEach { number -> FilterChip(selected = count == number, onClick = { count = number }, enabled = image.isBlank() || number == 1, label = { Text("${number}개", fontSize = 11.sp) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("생성 후 예약하기", fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text("여러 개라면 하루 간격으로 예약합니다.", color = Muted, fontSize = 10.sp) }
                Switch(checked = schedule, onCheckedChange = { schedule = it })
            }
            if (schedule) {
                StudioField(time, { time = it }, "첫 예약 · yyyy-MM-dd HH:mm", single = true)
                Text(if (settings.enabled) "자동화가 켜져 있어 예약 시각에 게시됩니다." else "설정한 콘텐츠는 자동화를 켠 뒤 예약 게시됩니다.", color = Purple, fontSize = 10.sp)
            }
            Text("영상은 AI가 만든 장면 이미지 3개와 자막을 18초 세로 MP4로 합성합니다.", color = Muted, fontSize = 10.sp)
            Button(onClick = { onCreate(brief, if (image.isBlank()) count else 1, if (schedule) time else "") }, enabled = !busy && hasKey && settings.brand.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(13.dp)) {
                Glyph("spark", Color.White, Modifier.size(18.dp)); Text("  ${if (image.isBlank()) count else 1}개 콘텐츠 생성", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Text("입력한 정보${if (image.isBlank()) "" else "와 사진"}를 OpenAI로 보내며, 본인 API 키의 사용 요금이 발생합니다.", color = Muted, fontSize = 9.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun PostEditor(post: StudioPost, settings: StudioSettings, store: LocalStore, runTask: (suspend () -> String) -> Unit,
    report: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var title by rememberSaveable(post.id, post.title) { mutableStateOf(post.title) }
    var caption by rememberSaveable(post.id, post.caption) { mutableStateOf(post.caption) }
    var time by rememberSaveable(post.id, post.scheduledAt) {
        mutableStateOf((if (post.scheduledAt > 0) Instant.ofEpochMilli(post.scheduledAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
        else LocalDate.now().plusDays(1).atTime(settings.hour, settings.minute)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
    }
    var deletePrompt by remember { mutableStateOf(false) }
    var resetPrompt by remember { mutableStateOf(false) }
    val working = post.status in listOf(PostStatus.QUEUED, PostStatus.GENERATING, PostStatus.PUBLISHING)
    val editable = !working && post.status !in listOf(PostStatus.PUBLISHED, PostStatus.REVIEW)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Paper, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Glyph("close", Ink, Modifier.size(21.dp)) }
                    Text("콘텐츠 스튜디오", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    StatusPill(post.status)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    LocalImage(post.image, post.alt.ifBlank { post.title }, Modifier.fillMaxWidth().aspectRatio(if (post.format in listOf("STORY", "REELS")) 9f / 13f else 1f).clip(RoundedCornerShape(20.dp)))
                    if (post.video.isNotBlank()) OutlinedButton(onClick = {
                        runCatching {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", store.media(post.video))
                            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        }.onFailure { report("영상을 재생하지 못했습니다.") }
                    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("생성한 영상 재생", fontSize = 12.sp) }
                    if (working) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("${post.status.title} · 작업 결과가 자동으로 반영됩니다.", color = Purple, fontSize = 11.sp)
                        OutlinedButton(onClick = { runTask {
                            InstagramAutomationService.active?.stopPosting()
                            WorkManager.getInstance(context).cancelUniqueWork("loop-pipeline").result.get()
                            store.update(post.id) {
                                if (it.status == PostStatus.PUBLISHED) it else it.copy(status = if (it.configureStarted) PostStatus.REVIEW else PostStatus.FAILED,
                                    error = if (it.configureStarted) "게시 시도 후 작업을 중지했습니다. 인스타그램에서 게시 여부를 확인해 주세요." else "사용자가 작업을 중지했습니다.")
                            }
                            "작업 중지를 요청했습니다."
                        } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("작업 중지", fontSize = 12.sp) }
                    }
                    if (post.error.isNotBlank()) {
                        Surface(color = Color(0xFFFFF0E8), shape = RoundedCornerShape(13.dp)) {
                            Text(post.error, color = Color(0xFFAA6541), fontSize = 11.sp, lineHeight = 18.sp, modifier = Modifier.fillMaxWidth().padding(14.dp))
                        }
                    }
                    if (post.status == PostStatus.PUBLISHED) {
                        Text("인스타그램에 게시된 콘텐츠입니다. 아래에서 앱에 보관된 문구를 복사하거나 이미지를 공유할 수 있습니다.", fontSize = 11.sp, color = Muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Text("좋아요 ${post.likes}", color = Purple, fontSize = 12.sp)
                            Text("댓글 ${post.comments}", color = Purple, fontSize = 12.sp)
                        }
                    }
                    StudioField(title, { title = it }, "콘텐츠 제목", single = true, enabled = editable)
                    StudioField(caption, { caption = it }, "인스타그램 문구와 해시태그", minimumLines = 6, enabled = editable)
                    Text("${caption.length}/2,200자 · ${if (post.format == "REELS") "릴스" else if (post.format == "STORY") "스토리" else "피드"}", color = if (caption.length > 2200) Color(0xFFBB584D) else Muted, fontSize = 10.sp)
                    if (editable) {
                        Button(onClick = { runTask {
                            require(caption.length <= 2200) { "문구는 2,200자 이내로 작성해 주세요." }
                            store.update(post.id) { current ->
                                check(current.status !in listOf(PostStatus.GENERATING, PostStatus.PUBLISHING, PostStatus.QUEUED, PostStatus.PUBLISHED, PostStatus.REVIEW)) { "진행 중인 작업이 끝난 뒤 수정해 주세요." }
                                current.copy(title = title.trim(), caption = caption.trim())
                            }
                            "수정한 내용을 저장했습니다."
                        } }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Text("내용 저장", fontSize = 12.sp) }
                        if (post.image.isNotBlank()) {
                            StudioField(time, { time = it }, "예약 시각 · yyyy-MM-dd HH:mm", single = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                OutlinedButton(onClick = { runTask {
                                    val selected = LocalDateTime.parse(time, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                                    require(selected.isAfter(LocalDateTime.now())) { "현재보다 이후 시각을 입력해 주세요." }
                                    require(caption.isNotBlank() && caption.length <= 2200) { "문구를 1~2,200자로 입력해 주세요." }
                                    val latest = store.settings()
                                    require(latest.connected) { "예약할 인스타그램 계정을 먼저 연결해 주세요." }
                                    val updated = store.update(post.id) {
                                        check(it.status !in listOf(PostStatus.QUEUED, PostStatus.GENERATING, PostStatus.PUBLISHING, PostStatus.PUBLISHED, PostStatus.REVIEW)) { "현재 상태에서는 예약을 변경할 수 없습니다." }
                                        it.copy(title = title.trim(), caption = caption.trim(), scheduledAt = selected.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                                            status = PostStatus.SCHEDULED, accountId = latest.accountId, error = "")
                                    } ?: throw IOException("콘텐츠가 없습니다.")
                                    StudioJobs.schedule(context, updated)
                                    if (latest.enabled) "예약을 저장했습니다." else "예약을 저장했습니다. 자동화를 켜면 게시됩니다."
                                } }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) { Text("예약 저장", fontSize = 11.sp) }
                                Button(onClick = { runTask {
                                    val latest = store.settings()
                                    require(latest.connected) { "인스타그램을 먼저 연결해 주세요." }
                                    require(caption.isNotBlank() && caption.length <= 2200) { "문구를 1~2,200자로 입력해 주세요." }
                                    store.update(post.id) {
                                        check(it.status !in listOf(PostStatus.QUEUED, PostStatus.GENERATING, PostStatus.PUBLISHING, PostStatus.PUBLISHED, PostStatus.REVIEW)) { "이미 작업 중입니다." }
                                        it.copy(title = title.trim(), caption = caption.trim(), status = PostStatus.DRAFT, accountId = latest.accountId, error = "")
                                    }
                                    StudioJobs.enqueue(context, "publish", post.id)
                                    "@${latest.username} 계정에 게시를 요청했습니다."
                                } }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) { Text("지금 게시", fontSize = 11.sp) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { runTask {
                                store.update(post.id) {
                                    check(it.status !in listOf(PostStatus.QUEUED, PostStatus.GENERATING, PostStatus.PUBLISHING, PostStatus.PUBLISHED, PostStatus.REVIEW)) { "현재 작업이 끝난 뒤 다시 시도해 주세요." }
                                    it.copy(status = PostStatus.QUEUED, error = "")
                                }
                                StudioJobs.enqueue(context, "generate", post.id)
                                "다시 생성을 요청했습니다. API 사용 요금이 발생합니다."
                            } }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) { Text(if (post.format == "REELS") "영상 다시 생성" else if (post.image.isBlank()) "생성 다시 시도" else "문구 다시 생성", fontSize = 10.sp) }
                            if (post.status == PostStatus.SCHEDULED) OutlinedButton(onClick = { runTask {
                                store.update(post.id) {
                                    check(it.status == PostStatus.SCHEDULED) { "이미 진행 중인 예약은 취소할 수 없습니다." }
                                    it.copy(status = PostStatus.DRAFT, scheduledAt = 0)
                                }
                                WorkManager.getInstance(context).cancelUniqueWork("loop-schedule-${post.id}")
                                "예약을 취소하고 초안으로 보관했습니다."
                            } }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) { Text("예약 취소", fontSize = 10.sp) }
                        }
                    }
                    if (post.status == PostStatus.REVIEW) {
                        Text("인스타그램에서 실제 게시 여부를 확인한 다음 앱 기록을 정리해 주세요.", fontSize = 11.sp, color = Muted)
                        OutlinedButton(onClick = { resetPrompt = true }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Text("게시되지 않음 · 초안으로 되돌리기", fontSize = 11.sp) }
                        OutlinedButton(onClick = { runTask {
                            store.update(post.id) {
                                check(it.status == PostStatus.REVIEW) { "현재 게시 상태를 다시 확인해 주세요." }
                                it.copy(status = PostStatus.PUBLISHED, publishedAt = System.currentTimeMillis(), remoteId = it.remoteId.ifBlank { "manual:${it.id}" }, error = "사용자가 인스타그램에서 게시를 확인했습니다.")
                            }
                            "게시 완료로 기록했습니다."
                        } }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Text("게시됨 · 완료로 기록하기", fontSize = 11.sp) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("인스타그램 문구", caption)); report("문구를 복사했습니다.")
                        }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) { Text("문구 복사", fontSize = 10.sp) }
                        if (post.image.isNotBlank()) OutlinedButton(onClick = {
                            runCatching {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", store.media(post.video.ifBlank { post.image }))
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (post.video.isNotBlank()) "video/mp4" else "image/jpeg"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_TEXT, caption)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); clipData = ClipData.newRawUri("image", uri)
                                }
                                context.startActivity(Intent.createChooser(intent, "콘텐츠 공유"))
                            }.onFailure { report("이미지를 공유하지 못했습니다.") }
                        }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) { Text(if (post.video.isNotBlank()) "영상 공유" else "이미지 공유", fontSize = 10.sp) }
                    }
                    if (settings.connected || post.remoteCode.isNotBlank()) TextButton(onClick = {
                        val link = when {
                            post.remoteCode.matches(Regex("[A-Za-z0-9_-]+")) -> "https://www.instagram.com/p/${post.remoteCode}/"
                            settings.username.isNotBlank() -> "https://www.instagram.com/${settings.username}/"
                            else -> "https://www.instagram.com/"
                        }
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }.onFailure { report("인스타그램을 열지 못했습니다.") }
                    }, modifier = Modifier.fillMaxWidth()) { Text("인스타그램에서 확인 ↗", fontSize = 11.sp) }
                    if (!working || post.status == PostStatus.QUEUED) TextButton(onClick = { deletePrompt = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (post.status == PostStatus.QUEUED) "생성 대기 취소 · 콘텐츠 삭제" else "휴대폰에서 콘텐츠 삭제", color = Color(0xFFBD6868), fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
    if (deletePrompt) AlertDialog(onDismissRequest = { deletePrompt = false }, title = { Text("콘텐츠를 삭제할까요?") },
        text = { Text("휴대폰에 저장된 문구와 이미지를 삭제합니다. 인스타그램 게시물은 유지됩니다.", fontSize = 13.sp) },
        confirmButton = { TextButton(onClick = { deletePrompt = false; runTask { store.delete(post.id); WorkManager.getInstance(context).cancelUniqueWork("loop-schedule-${post.id}"); withContext(Dispatchers.Main) { onDismiss() }; "로컬 콘텐츠를 삭제했습니다." } }) { Text("삭제") } },
        dismissButton = { TextButton(onClick = { deletePrompt = false }) { Text("취소") } })
    if (resetPrompt) AlertDialog(onDismissRequest = { resetPrompt = false }, title = { Text("게시되지 않은 것을 확인하셨나요?") },
        text = { Text("초안으로 되돌리면 다시 게시할 수 있습니다. 인스타그램에 이미 올라갔다면 중복 게시될 수 있습니다.", fontSize = 13.sp) },
        confirmButton = { TextButton(onClick = { resetPrompt = false; runTask {
            store.update(post.id) { check(it.status == PostStatus.REVIEW); it.copy(status = PostStatus.DRAFT, configureStarted = false, scheduledAt = 0, error = "") }
            "초안으로 되돌렸습니다."
        } }) { Text("확인했습니다") } }, dismissButton = { TextButton(onClick = { resetPrompt = false }) { Text("취소") } })
}

@Composable
private fun InstagramLogin(store: LocalStore, onDismiss: () -> Unit, onConnected: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(InstagramAutomationService.active != null) }
    var consent by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) { granted = InstagramAutomationService.active != null; delay(1000) }
    }
    AlertDialog(onDismissRequest = { if (!working) onDismiss() },
        title = { Text("인스타그램 앱 연결") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("휴대폰에 설치된 인스타그램 앱으로 로그인하고 게시합니다. 추가 인증은 인스타그램 앱에서 직접 완료해 주세요.", fontSize = 12.sp)
                Text("화면 조작 권한을 켜면 인스타그램 화면의 텍스트를 읽고, 설정한 일정이나 게시 요청에 따라 사진·영상을 첨부하고 문구와 게시 버튼을 입력합니다. 화면 내용은 외부 서버로 전송하지 않습니다. 자동화 일시정지 또는 권한 해제로 중지할 수 있습니다.", fontSize = 11.sp, lineHeight = 18.sp)
                if (!granted) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consent, onCheckedChange = { consent = it })
                    Text("화면 읽기·조작에 동의합니다.", fontSize = 12.sp)
                }
                Text("게시할 때 화면이 켜져 있고 잠금이 풀려 있어야 합니다. 사진·영상 생성은 백그라운드에서 진행합니다.", fontSize = 11.sp, color = Muted)
                if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (message.isNotBlank()) Text(message, fontSize = 11.sp, color = Purple)
            }
        },
        confirmButton = {
            TextButton(enabled = !working && (granted || consent), onClick = {
                if (!granted) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else scope.launch {
                    working = true
                    message = "인스타그램 앱에서 계정을 확인합니다. 로그인 후 본인 프로필을 열어 주세요."
                    try {
                        val service = InstagramAutomationService.active ?: throw IOException("화면 조작 권한을 켜 주세요.")
                        service.execute()
                        onConnected()
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (error: Exception) { message = error.message ?: "인스타그램 연결을 확인하지 못했습니다." }
                    finally { working = false }
                }
            }) { Text(if (granted) "인스타그램 연결" else "화면 조작 권한 켜기") }
        },
        dismissButton = { TextButton(onClick = { InstagramAutomationService.active?.stopPosting(); onDismiss() }) { Text(if (working) "연결 중지" else "닫기") } })
}

@Composable
private fun StudioField(value: String, change: (String) -> Unit, label: String, single: Boolean = false, minimumLines: Int = 2, enabled: Boolean = true) {
    OutlinedTextField(value = value, onValueChange = change, label = { Text(label, fontSize = 11.sp) }, singleLine = single,
        minLines = if (single) 1 else minimumLines, enabled = enabled, shape = RoundedCornerShape(12.dp),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Ink, lineHeight = 21.sp), modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, disabledTextColor = Ink, disabledLabelColor = Muted, disabledBorderColor = Line))
}

@Composable
private fun SectionTitle(title: String, action: String, click: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
        if (action.isNotBlank()) Text(action, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Purple, modifier = Modifier.clickable(onClick = click).padding(vertical = 4.dp))
    }
}

@Composable
private fun StatusPill(status: PostStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        PostStatus.PUBLISHED -> Color(0xFF39866B)
        PostStatus.FAILED, PostStatus.REVIEW -> Color(0xFFAD6646)
        PostStatus.DRAFT -> Color(0xFF74758A)
        else -> Purple
    }
    Surface(color = Color.White.copy(alpha = 0.94f), shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(status.title, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
    }
}

@Composable
private fun LocalImage(filename: String, description: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, filename) {
        value = withContext(Dispatchers.IO) {
            if (filename.isBlank()) null else runCatching {
                val file = LocalStore.get(context).media(filename)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                var factor = 1
                while (maxOf(bounds.outWidth / factor, bounds.outHeight / factor) > 1024) factor *= 2
                BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = factor })?.asImageBitmap()
            }.getOrNull()
        }
    }
    if (bitmap != null) Image(bitmap = bitmap!!, contentDescription = description, contentScale = ContentScale.Crop, modifier = modifier)
    else Box(modifier.background(Color(0xFFEFECF8)), contentAlignment = Alignment.Center) { Glyph("photo", Purple.copy(alpha = 0.35f), Modifier.size(34.dp)) }
}

@Composable
private fun Glyph(name: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.65f, cap = StrokeCap.Round)
            when (name) {
                "home" -> {
                    drawPath(Path().apply { moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f); lineTo(21f, 21f); lineTo(15f, 21f); lineTo(15f, 14f); lineTo(9f, 14f); lineTo(9f, 21f); lineTo(3f, 21f); close() }, color, style = stroke)
                }
                "grid" -> {
                    listOf(Offset(3f, 3f), Offset(14f, 3f), Offset(3f, 14f), Offset(14f, 14f)).forEach {
                        drawRoundRect(color, it, androidx.compose.ui.geometry.Size(7f, 7f), androidx.compose.ui.geometry.CornerRadius(1.6f), style = stroke)
                    }
                }
                "calendar" -> {
                    drawRoundRect(color, Offset(3f, 5f), androidx.compose.ui.geometry.Size(18f, 16f), androidx.compose.ui.geometry.CornerRadius(2f), style = stroke)
                    drawLine(color, Offset(3f, 10f), Offset(21f, 10f), 1.6f)
                    drawLine(color, Offset(8f, 2f), Offset(8f, 7f), 1.6f, StrokeCap.Round); drawLine(color, Offset(16f, 2f), Offset(16f, 7f), 1.6f, StrokeCap.Round)
                    listOf(Offset(8f, 14f), Offset(12f, 14f), Offset(16f, 14f), Offset(8f, 18f), Offset(12f, 18f)).forEach { drawCircle(color, 0.8f, it) }
                }
                "spark" -> {
                    drawPath(Path().apply { moveTo(11f, 2f); lineTo(14f, 9f); lineTo(21f, 12f); lineTo(14f, 15f); lineTo(11f, 22f); lineTo(8f, 15f); lineTo(1f, 12f); lineTo(8f, 9f); close() }, color, style = stroke)
                    drawLine(color, Offset(20f, 1f), Offset(20f, 6f), 1.5f, StrokeCap.Round); drawLine(color, Offset(17.5f, 3.5f), Offset(22.5f, 3.5f), 1.5f, StrokeCap.Round)
                }
                "settings" -> {
                    drawCircle(color, 4f, Offset(12f, 12f), style = stroke)
                    drawCircle(color, 8f, Offset(12f, 12f), style = stroke)
                    listOf(Offset(12f, 1f) to Offset(12f, 4f), Offset(12f, 20f) to Offset(12f, 23f), Offset(1f, 12f) to Offset(4f, 12f), Offset(20f, 12f) to Offset(23f, 12f), Offset(4f, 4f) to Offset(6f, 6f), Offset(18f, 18f) to Offset(20f, 20f), Offset(4f, 20f) to Offset(6f, 18f), Offset(18f, 6f) to Offset(20f, 4f)).forEach { drawLine(color, it.first, it.second, 2.4f, StrokeCap.Round) }
                }
                "account" -> {
                    drawCircle(color, 4f, Offset(12f, 7f), style = stroke)
                    drawPath(Path().apply { moveTo(4f, 22f); lineTo(4f, 19f); cubicTo(4f, 12f, 20f, 12f, 20f, 19f); lineTo(20f, 22f) }, color, style = stroke)
                }
                "lock" -> {
                    drawRoundRect(color, Offset(5f, 10f), androidx.compose.ui.geometry.Size(14f, 11f), androidx.compose.ui.geometry.CornerRadius(2f), style = stroke)
                    drawPath(Path().apply { moveTo(8f, 10f); lineTo(8f, 6f); cubicTo(8f, 1f, 16f, 1f, 16f, 6f); lineTo(16f, 10f) }, color, style = stroke)
                    drawCircle(color, 1.2f, Offset(12f, 15f))
                }
                "key" -> {
                    drawCircle(color, 4.5f, Offset(7f, 8f), style = stroke)
                    drawLine(color, Offset(10.3f, 11.3f), Offset(21f, 22f), 1.8f, StrokeCap.Round)
                    drawLine(color, Offset(16f, 17f), Offset(19f, 14f), 1.8f, StrokeCap.Round)
                    drawLine(color, Offset(19f, 20f), Offset(22f, 17f), 1.8f, StrokeCap.Round)
                }
                "photo" -> {
                    drawRoundRect(color, Offset(3f, 3f), androidx.compose.ui.geometry.Size(18f, 18f), androidx.compose.ui.geometry.CornerRadius(3f), style = stroke)
                    drawCircle(color, 2f, Offset(8f, 8f), style = stroke)
                    drawPath(Path().apply { moveTo(3f, 18f); lineTo(9f, 12f); lineTo(13f, 16f); lineTo(17f, 12f); lineTo(21f, 16f) }, color, style = stroke)
                }
                "clock" -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = stroke)
                    drawLine(color, Offset(12f, 6f), Offset(12f, 12f), 1.7f, StrokeCap.Round); drawLine(color, Offset(12f, 12f), Offset(16f, 14f), 1.7f, StrokeCap.Round)
                }
                "check" -> drawPath(Path().apply { moveTo(4f, 12f); lineTo(9f, 17f); lineTo(20f, 6f) }, color, style = Stroke(2f, cap = StrokeCap.Round))
                "plus" -> { drawLine(color, Offset(12f, 4f), Offset(12f, 20f), 1.8f, StrokeCap.Round); drawLine(color, Offset(4f, 12f), Offset(20f, 12f), 1.8f, StrokeCap.Round) }
                "close" -> { drawLine(color, Offset(5f, 5f), Offset(19f, 19f), 1.8f, StrokeCap.Round); drawLine(color, Offset(5f, 19f), Offset(19f, 5f), 1.8f, StrokeCap.Round) }
                "arrow" -> { drawLine(color, Offset(3f, 12f), Offset(21f, 12f), 1.8f, StrokeCap.Round); drawPath(Path().apply { moveTo(15f, 6f); lineTo(21f, 12f); lineTo(15f, 18f) }, color, style = stroke) }
                "chevron" -> drawPath(Path().apply { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }, color, style = stroke)
                "play" -> drawPath(Path().apply { moveTo(7f, 4f); lineTo(20f, 12f); lineTo(7f, 20f); close() }, color)
                "pause" -> { drawRoundRect(color, Offset(6f, 4f), androidx.compose.ui.geometry.Size(4f, 16f), androidx.compose.ui.geometry.CornerRadius(1f)); drawRoundRect(color, Offset(14f, 4f), androidx.compose.ui.geometry.Size(4f, 16f), androidx.compose.ui.geometry.CornerRadius(1f)) }
                "download" -> {
                    drawLine(color, Offset(12f, 2f), Offset(12f, 16f), 1.8f, StrokeCap.Round)
                    drawPath(Path().apply { moveTo(6f, 10f); lineTo(12f, 16f); lineTo(18f, 10f) }, color, style = stroke)
                    drawPath(Path().apply { moveTo(3f, 16f); lineTo(3f, 21f); lineTo(21f, 21f); lineTo(21f, 16f) }, color, style = stroke)
                }
            }
        }
    }
}
