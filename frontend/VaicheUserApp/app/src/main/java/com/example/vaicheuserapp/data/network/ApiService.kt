package com.example.vaicheuserapp.data.network

import com.example.vaicheuserapp.data.model.LoginResponse
import com.example.vaicheuserapp.data.model.UserCreateRequest
import com.example.vaicheuserapp.data.model.UserPublic
import com.example.vaicheuserapp.data.model.CategoryPublic
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {

    @POST("/api/auth/signup")
    suspend fun signup(@Body userCreateRequest: UserCreateRequest): Response<UserPublic>

    @FormUrlEncoded
    @POST("/api/auth/login/access-token")
    suspend fun login(
        @Field("username") phoneNumber: String, // The API expects 'username' for the phone number
        @Field("password") password: String,
        @Field("grant_type") grantType: String = "password" // Default value as per OAuth2
    ): Response<LoginResponse>

    @GET("/api/category/")
    suspend fun getCategories(): Response<List<CategoryPublic>>
}