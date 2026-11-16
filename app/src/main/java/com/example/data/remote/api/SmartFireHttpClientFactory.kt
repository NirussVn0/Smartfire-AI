package com.example.data.remote.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object SmartFireHttpClientFactory {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val clientCache = ConcurrentHashMap<String, SmartFireApiService>()

    fun getService(ipAddress: String, bearerToken: String, timeoutSeconds: Int = 2): SmartFireApiService {
        val sanitizedIp = ipAddress.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        val clampedTimeout = timeoutSeconds.coerceIn(1, 15).toLong()
        val cacheKey = "$sanitizedIp::$bearerToken::$clampedTimeout"

        return clientCache.getOrPut(cacheKey) {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(clampedTimeout, TimeUnit.SECONDS)
                .readTimeout(clampedTimeout, TimeUnit.SECONDS)
                .writeTimeout(clampedTimeout, TimeUnit.SECONDS)
                .addInterceptor(AuthInterceptor(bearerToken))
                .build()

            val baseUrl = "http://$sanitizedIp/"

            Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(SmartFireApiService::class.java)
        }
    }

    private class AuthInterceptor(private val token: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val requestBuilder = original.newBuilder()
            if (token.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            return chain.proceed(requestBuilder.build())
        }
    }
}
