package app.stridelocal

import android.app.Application
import app.stridelocal.data.local.StrideDatabase
import app.stridelocal.maps.OfflineMapPacks
import okhttp3.OkHttpClient
import okhttp3.Dispatcher
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

class StrideLocalApp : Application() {
    val database by lazy { StrideDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        MapLibre.getInstance(this).setConnected(false)
        OfflineMapPacks.pauseAll(this)
        val requestDispatcher = Dispatcher().apply { maxRequests = 4; maxRequestsPerHost = 4 }
        val mapClient = OkHttpClient.Builder().dispatcher(requestDispatcher).addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "StrideLocal/0.1 Android").build())
        }.build()
        HttpRequestUtil().setOkHttpClient(mapClient)
    }
}
