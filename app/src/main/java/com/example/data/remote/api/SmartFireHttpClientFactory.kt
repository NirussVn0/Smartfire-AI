package com.example.data.remote.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

object SmartFireHttpClientFactory {

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var activeWifiNetwork: Network? = null

    @Volatile
    private var networkCallbackRegistered = false

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val clientCache = ConcurrentHashMap<String, SmartFireApiService>()
    private val deviceLocks = ConcurrentHashMap<String, Mutex>()

    private val baseClient: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 16
            maxRequestsPerHost = 1
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
            .socketFactory(WifiBoundSocketFactory())
            .retryOnConnectionFailure(true)
            .build()
    }

    fun init(context: Context) {
        val appCtx = context.applicationContext ?: context
        appContext = appCtx
        if (!networkCallbackRegistered) {
            synchronized(this) {
                if (!networkCallbackRegistered) {
                    runCatching {
                        val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                        if (cm != null) {
                            val request = NetworkRequest.Builder()
                                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                                .build()
                            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                                override fun onAvailable(network: Network) {
                                    activeWifiNetwork = network
                                }

                                override fun onLost(network: Network) {
                                    if (activeWifiNetwork == network) {
                                        activeWifiNetwork = null
                                    }
                                }
                            })
                            networkCallbackRegistered = true
                        }
                    }
                }
            }
        }
    }

    suspend fun <T> withDeviceLock(ipOrKey: String, block: suspend () -> T): T {
        val normalizedKey = ipOrKey.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
            .ifBlank { "__default__" }
        val mutex = deviceLocks.getOrPut(normalizedKey) { Mutex() }
        return mutex.withLock { block() }
    }

    fun getService(ipAddress: String, bearerToken: String, timeoutSeconds: Int = 5): SmartFireApiService {
        val sanitizedIp = ipAddress.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        val clampedReadWriteTimeout = timeoutSeconds.coerceIn(3, 60).toLong()
        val cacheKey = "$sanitizedIp::$bearerToken::$clampedReadWriteTimeout"

        return clientCache.getOrPut(cacheKey) {
            val clientBuilder = baseClient.newBuilder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(clampedReadWriteTimeout, TimeUnit.SECONDS)
                .writeTimeout(clampedReadWriteTimeout, TimeUnit.SECONDS)
                .addInterceptor(AuthInterceptor(bearerToken))

            if (!isPrivateRfc1918Ip(sanitizedIp) && !sanitizedIp.endsWith(".local", ignoreCase = true)) {
                clientBuilder.socketFactory(SocketFactory.getDefault())
            }

            val baseUrl = "http://$sanitizedIp/"

            Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(clientBuilder.build())
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(SmartFireApiService::class.java)
        }
    }

    fun getOtaService(ipAddress: String, bearerToken: String): SmartFireApiService {
        return getService(ipAddress = ipAddress, bearerToken = bearerToken, timeoutSeconds = 60)
    }

    private fun isPrivateRfc1918Ip(host: String?): Boolean {
        val h = host?.trim()?.substringBefore(':') ?: return false
        if (h.startsWith("192.168.") || h.startsWith("10.")) return true
        if (h.startsWith("172.")) {
            val secondOctet = h.removePrefix("172.").substringBefore('.').toIntOrNull()
            if (secondOctet != null && secondOctet in 16..31) return true
        }
        return false
    }

    private fun resolveWifiSocketFactory(): SocketFactory {
        val ctx = appContext ?: return SocketFactory.getDefault()
        return runCatching {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return@runCatching SocketFactory.getDefault()

            val cached = activeWifiNetwork
            if (cached != null && cm.getNetworkCapabilities(cached)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                return@runCatching cached.socketFactory
            }

            val active = cm.activeNetwork
            if (active != null && cm.getNetworkCapabilities(active)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                activeWifiNetwork = active
                return@runCatching active.socketFactory
            }

            @Suppress("DEPRECATION")
            val wifiNet = cm.allNetworks.firstOrNull { net ->
                cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            if (wifiNet != null) {
                activeWifiNetwork = wifiNet
                wifiNet.socketFactory
            } else {
                SocketFactory.getDefault()
            }
        }.getOrDefault(SocketFactory.getDefault())
    }

    /**
     * Binds sockets targeting ESP32 private LAN IPs to the active Wi-Fi Network interface
     * so mobile cellular data (4G/5G) never hijacks local 192.168.x.x / 10.x.x.x / 172.16-31.x.x requests.
     */
    private class WifiBoundSocketFactory : SocketFactory() {
        override fun createSocket(): Socket {
            return runCatching { resolveWifiSocketFactory().createSocket() }
                .getOrElse { SocketFactory.getDefault().createSocket() }
        }

        override fun createSocket(host: String?, port: Int): Socket {
            val factory = if (isPrivateRfc1918Ip(host)) resolveWifiSocketFactory() else SocketFactory.getDefault()
            return factory.createSocket(host, port)
        }

        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket {
            val factory = if (isPrivateRfc1918Ip(host)) resolveWifiSocketFactory() else SocketFactory.getDefault()
            return factory.createSocket(host, port, localHost, localPort)
        }

        override fun createSocket(host: InetAddress?, port: Int): Socket {
            val factory = if (isPrivateRfc1918Ip(host?.hostAddress)) resolveWifiSocketFactory() else SocketFactory.getDefault()
            return factory.createSocket(host, port)
        }

        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
            val factory = if (isPrivateRfc1918Ip(address?.hostAddress)) resolveWifiSocketFactory() else SocketFactory.getDefault()
            return factory.createSocket(address, port, localAddress, localPort)
        }
    }

    private class AuthInterceptor(private val token: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                // ESP32 single-threaded WebServer handles short-lived connections far more reliably
                .header("Connection", "close")
            if (token.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            return chain.proceed(requestBuilder.build())
        }
    }
}
