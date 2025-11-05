package fitech.tutorials.rsmgraphlast.data.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiFactory {

    fun createApiServiceWithToken(
        baseUrl: String,
        token: String?
    ): ApiService {
        val authInterceptor = Interceptor { chain ->
            val req = chain.request()
            val needsAuth = !req.url.encodedPath.endsWith("/login")
            val newReq = if (needsAuth && !token.isNullOrBlank()) {
                req.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else req
            chain.proceed(newReq)
        }

        val client = OkHttpClient.Builder()
            // WorkManager işleri için biraz uzun timeout’lar:
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
