package fitech.tutorials.rsmgraphlast.data.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

object ApiFactory {
    fun createDasServiceShort(baseUrl: String, token: String?): DasApiService {
        val auth = Interceptor { chain ->
            val req = chain.request()
            val newReq = if (!token.isNullOrBlank())
                req.newBuilder().addHeader("Authorization", "Bearer $token").build()
            else req
            chain.proceed(newReq)
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)   // kısa
            .readTimeout(20, TimeUnit.SECONDS)      // kısa
            .writeTimeout(20, TimeUnit.SECONDS)     // kısa
            .addInterceptor(auth)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(DasApiService::class.java)
    }

    fun createDasServiceLong(baseUrl: String, token: String?): DasApiService {
        val auth = Interceptor { chain ->
            val req = chain.request()
            val newReq = if (!token.isNullOrBlank())
                req.newBuilder().addHeader("Authorization", "Bearer $token").build()
            else req
            chain.proceed(newReq)
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)   // uzun
            .readTimeout(60, TimeUnit.SECONDS)      // uzun
            .writeTimeout(60, TimeUnit.SECONDS)     // uzun
            .addInterceptor(auth)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(DasApiService::class.java)
    }

    fun createLoginService(baseUrl: String): LoginApiService {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LoginApiService::class.java)
    }
}
