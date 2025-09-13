package com.example.vaiche_driver.data.repository

import com.example.vaiche_driver.data.common.ApiProvider
import com.example.vaiche_driver.data.common.safeApiCall
import com.example.vaiche_driver.model.ConversationPublic
import com.example.vaiche_driver.model.MessageCreate
import com.example.vaiche_driver.model.MessagePublic
import com.example.vaiche_driver.model.UserPublic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MessageRepository(
    private val apiProvider: ApiProvider
) {
    private val api get() = apiProvider()

    suspend fun getConversations(): Result<List<ConversationPublic>> =
        withContext(Dispatchers.IO) { safeApiCall { api.getConversations() } }

    suspend fun getMessages(conversationId: String): Result<List<MessagePublic>> =
        withContext(Dispatchers.IO) { safeApiCall { api.getMessages(conversationId) } }

    suspend fun getUserById(userId: String): Result<UserPublic> =
        withContext(Dispatchers.IO) { safeApiCall { apiProvider().getUserById(userId) } }

}
