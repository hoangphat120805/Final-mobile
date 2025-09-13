package com.example.vaiche_driver.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vaiche_driver.data.network.RetrofitClient
import com.example.vaiche_driver.data.repository.MessageRepository
import com.example.vaiche_driver.model.ConversationMember
import com.example.vaiche_driver.model.ConversationPublic
import com.example.vaiche_driver.model.ConversationType
import com.example.vaiche_driver.model.UserPublic
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

data class ConversationRow(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val lastMessagePreview: String,
    val lastTimeLabel: String,
    val lastInstantEpoch: Long
)

class ConversationsViewModel(
    private val repo: MessageRepository = MessageRepository { RetrofitClient.instance }
) : ViewModel() {

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _items = MutableLiveData<List<ConversationRow>>(emptyList())
    val items: LiveData<List<ConversationRow>> = _items

    private val _toast = MutableLiveData<Event<String>>()
    val toast: LiveData<Event<String>> = _toast

    // formatter linh hoạt: yyyy-MM-dd'T'HH:mm:ss(.SSSSSS)
    private val FLEX_ISO_LOCAL: DateTimeFormatter =
        DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 6, true)
            .optionalEnd()
            .toFormatter()

    fun load(myId: String) {
        if (_isLoading.value == true) return
        _isLoading.value = true

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repo.getConversations() }
            result.onSuccess { list ->
                // build tất cả row với dữ liệu partner đầy đủ trước khi emit
                val rows = buildRowsFull(list, myId)
                _items.value = rows.sortedByDescending { it.lastInstantEpoch }
                _toast.postValue(Event("Loaded ${rows.size} conversations"))
            }.onFailure { e ->
                _toast.postValue(Event("Load conversations failed: ${e.message}"))
                _items.value = emptyList()
            }
            _isLoading.value = false
        }
    }

    private suspend fun buildRowsFull(
        src: List<ConversationPublic>,
        myId: String
    ): List<ConversationRow> = coroutineScope {
        src.filter { it.type == ConversationType.PRIVATE }.map { c ->
            async(Dispatchers.IO) {
                val partnerId = otherMemberId(c.members, myId)
                val preview = c.lastMessage?.content.orEmpty()
                val lastIso = c.lastMessage?.createdAt ?: c.updatedAt
                val lastInstant = parseInstantSafe(lastIso)
                val label = relativeTime(lastInstant)

                var name = c.name ?: "Conversation"
                var avatarUrl: String? = null

                // nếu có partner thì load profile để có name/avatar chuẩn
                if (partnerId != null) {
                    try {
                        val user: UserPublic? =
                            RetrofitClient.instance.getUserById(partnerId).body()
                        if (user != null) {
                            name = user.fullName ?: name
                            avatarUrl = user.avatarUrl
                        }
                    } catch (e: Exception) {
                        Log.e("ConversationsVM", "Fetch user failed: ${e.message}")
                    }
                }

                ConversationRow(
                    id = c.id,
                    name = name,
                    avatarUrl = avatarUrl,
                    lastMessagePreview = preview,
                    lastTimeLabel = label,
                    lastInstantEpoch = lastInstant.epochSecond
                )
            }
        }.awaitAll()
    }

    private fun otherMemberId(members: List<ConversationMember>, me: String): String? =
        members.firstOrNull { it.userId != me }?.userId

    private fun parseInstantSafe(s: String): Instant {
        return try {
            if (s.endsWith("Z") || s.contains('+') || s.lastIndexOf('-') > "yyyy-MM-dd".length) {
                OffsetDateTime.parse(s).toInstant()
            } else {
                LocalDateTime.parse(s, FLEX_ISO_LOCAL).atZone(ZoneOffset.UTC).toInstant()
            }
        } catch (_: Exception) {
            try { Instant.parse(s) } catch (_: Exception) { Instant.EPOCH }
        }
    }

    private fun relativeTime(last: Instant): String {
        val now = Instant.now()
        val d = Duration.between(last, now)
        val mins = d.toMinutes()
        val hours = d.toHours()
        val days = d.toDays()

        return when {
            mins < 1 -> "now"
            mins < 60 -> "${mins}m"
            hours < 24 -> "${hours}h"
            days < 7 -> "${days}d"
            days < 28 -> "${days / 7}w"
            else -> DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())
                .withZone(ZoneId.systemDefault())
                .format(last)
        }
    }
}
