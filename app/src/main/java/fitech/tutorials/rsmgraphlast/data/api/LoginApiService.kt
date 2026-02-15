package fitech.tutorials.rsmgraphlast.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// baseUrl -> http://160.75.159.6:3012/api/database/

interface LoginApiService {
    @POST("login")
    suspend fun login(@Body body: Map<String, String>): Response<String>
} 