package com.petmanager.data.remote.socket

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.petmanager.BuildConfig
import com.petmanager.data.local.TokenStorage
import com.petmanager.data.remote.api.JwtDecoder
import com.petmanager.data.remote.dto.FindChatLogsRespDto
import com.petmanager.presentation.mapper.parseIncomingChatLog
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatSocketManager @Inject constructor(
    private val gson: Gson,
) {

    private var socket: Socket? = null
    private var connectedRoomId: String? = null


    /** handshake 시 인증된 JWT subject — 동일 유저 RTR 중에는 재연결하지 않음 */
    private var authenticatedSubject: String? = null

    private val _newMessages = MutableSharedFlow<FindChatLogsRespDto>(extraBufferCapacity = 64)
    val newMessages: SharedFlow<FindChatLogsRespDto> = _newMessages.asSharedFlow()

    private val _connectionErrors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val connectionErrors: SharedFlow<String> = _connectionErrors.asSharedFlow()

    @Synchronized
    fun connect() {
        val token = TokenStorage.accessToken()
        if (token.isNullOrBlank()) {
            _connectionErrors.tryEmit("로그인이 필요합니다.")
            return
        }

        val subject = JwtDecoder.subject(token)
        if (socket?.connected() == true) {
            // RTR 등으로 AT만 갱신된 경우 — 동일 유저면 기존 소켓 유지
            if (!subject.isNullOrBlank() && subject == authenticatedSubject) return
            closeSocketOnly()
        }

        val base = BuildConfig.SERVER_BASE_URL.trimEnd('/')
        val opts = IO.Options().apply {
            forceNew = true
            reconnection = true
            auth = mapOf("token" to token)
        }

        authenticatedSubject = subject

        try {
            socket = IO.socket(URI.create("$base/chat"), opts).also { s ->
                s.on(Socket.EVENT_CONNECT) {
                    Log.d(TAG, "socket connected")
                }
                s.on(Socket.EVENT_CONNECT_ERROR) { args ->
                    Log.e(TAG, "connect error: ${args.joinToString()}")
                    _connectionErrors.tryEmit("채팅 서버 연결에 실패했습니다.")
                }
                s.on(EVENT_NEW_MESSAGE, onNewMessage)
                s.connect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "socket init failed", e)
            _connectionErrors.tryEmit(e.message ?: "소켓 초기화 실패")
        }
    }

    /** 소켓만 종료. leave-room 이벤트는 보내지 않는다. */
    @Synchronized
    fun disconnect() {
        closeSocketOnly()
    }

    /**
     * 로그아웃·계정 전환·세션 무효 시 호출.
     * RTR(동일 유저 토큰 갱신)에서는 호출하지 않는다.
     */
    @Synchronized
    fun clearSession() {
        closeSocketOnly()
        authenticatedSubject = null
    }

    fun joinRoom(roomId: String) {
        val s = socket ?: run {
            connect()
            socket
        } ?: return

        if (!s.connected()) {
            s.once(Socket.EVENT_CONNECT) {
                emitJoinRoom(roomId)
            }
        } else {
            emitJoinRoom(roomId)
        }
    }

    /**
     * 채팅방 나가기 — 사용자가 [나가기] 버튼을 눌렀을 때만 호출.
     * leave-room 이벤트 + LeaveChatRoomReqDto(roomId) 전송.
     */
    fun leaveRoomExplicit(roomId: String, callback: (Result<Unit>) -> Unit) {
        val s = socket
        if (s == null || !s.connected()) {
            callback(Result.failure(IllegalStateException("채팅 서버에 연결되어 있지 않습니다.")))
            return
        }

        val completed = AtomicBoolean(false)
        val mainHandler = Handler(Looper.getMainLooper())
        val timeoutHolder = arrayOf<Runnable?>(null)

        val successListener = Emitter.Listener {
            if (!completed.compareAndSet(false, true)) return@Listener
            timeoutHolder[0]?.let { mainHandler.removeCallbacks(it) }
            if (connectedRoomId == roomId) {
                connectedRoomId = null
            }
            callback(Result.success(Unit))
        }

        timeoutHolder[0] = Runnable {
            if (!completed.compareAndSet(false, true)) return@Runnable
            s.off(EVENT_LEAVE_SUCCESS, successListener)
            callback(Result.failure(Exception("채팅방 나가기 응답 시간이 초과되었습니다.")))
        }

        s.once(EVENT_LEAVE_SUCCESS, successListener)
        mainHandler.postDelayed(timeoutHolder[0]!!, LEAVE_TIMEOUT_MS)

        val payload = JSONObject().apply {
            put("roomId", roomId)
        }
        s.emit(EVENT_LEAVE_ROOM, payload)
        Log.d(TAG, "leave-room emitted roomId=$roomId")
    }

    fun sendTextMessage(roomId: String, text: String) {
        val payload = JSONObject().apply {
            put("roomId", roomId)
            put("messageType", "TEXT")
            put("message", text)
        }
        socket?.emit(EVENT_SEND_MESSAGE, payload)
    }

    private fun emitJoinRoom(roomId: String) {
        connectedRoomId = roomId
        socket?.emit(EVENT_JOIN_ROOM, roomId)
    }

    @Synchronized
    private fun closeSocketOnly() {
        connectedRoomId = null
        socket?.off()
        socket?.disconnect()
        socket = null
    }

    private val onNewMessage = Emitter.Listener { args ->
        val raw = args.firstOrNull() ?: return@Listener
        try {
            val json = when (raw) {
                is JSONObject -> raw.toString()
                is String -> raw
                else -> gson.toJson(raw)
            }
            val dto = gson.parseIncomingChatLog(json)
            if (dto != null) {
                _newMessages.tryEmit(dto)
            } else {
                Log.w(TAG, "new-message ignored: missing logId payload=$json")
            }
        } catch (e: Exception) {
            Log.e(TAG, "parse new-message failed", e)
        }
    }

    companion object {
        private const val TAG = "ChatSocketManager"
        private const val EVENT_SEND_MESSAGE = "send-message"
        private const val EVENT_JOIN_ROOM = "join-room"
        private const val EVENT_LEAVE_ROOM = "leave-room"
        private const val EVENT_LEAVE_SUCCESS = "leave-room-success"
        private const val EVENT_NEW_MESSAGE = "new-message"
        private const val LEAVE_TIMEOUT_MS = 10_000L
    }
}
