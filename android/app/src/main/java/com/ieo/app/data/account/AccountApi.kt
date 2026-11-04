package com.ieo.app.data.account

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH

interface AccountApi {

    @GET("api/users/me")
    suspend fun me(): Response<UserResponse>

    @PATCH("api/users/me")
    suspend fun update(@Body body: UpdateUserRequest): Response<UserResponse>
}
