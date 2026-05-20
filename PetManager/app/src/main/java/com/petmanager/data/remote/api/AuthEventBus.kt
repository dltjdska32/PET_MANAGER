package com.petmanager.data.remote.api

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 인증 관련 전역 이벤트 버스
 *
 * - Interceptor 같은 네트워크 레이어에서는 여기로 "세션 만료" 이벤트만 발행
 * - UI 레이어(MainActivity 등)에서 이 이벤트를 구독해서
 *   실제 로그아웃/화면 전환/토스트를 처리
 */
object AuthEventBus {

    sealed class Event {
        object SessionExpired : Event()
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events: SharedFlow<Event> = _events

    // 한 번의 세션 만료 상황에서 중복 이벤트 발행을 막기 위한 플래그
    private val sessionExpiredEmitted = AtomicBoolean(false)

    /**
     * 세션 만료 이벤트를 한 번만 발행
     */
    fun emitSessionExpiredOnce() {
        if (!sessionExpiredEmitted.compareAndSet(false, true)) return
        _events.tryEmit(Event.SessionExpired)
    }

    /**
     * 새 로그인 세션 시작 시 플래그 리셋
     */
    fun resetSessionExpiredFlag() {
        sessionExpiredEmitted.set(false)
    }
}


