package com.martinvidal.tv.di

import android.content.Context
import com.martinvidal.tv.BuildConfig
import com.martinvidal.tv.data.net.DeviceProfile
import com.martinvidal.tv.data.net.PortalApi
import com.martinvidal.tv.data.net.PortalInterceptor
import com.martinvidal.tv.data.net.XuperAuthInterceptor
import com.martinvidal.tv.data.session.SettingsStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Host del portal configurado al compilar; si esta vacio, el interceptor
    // usa el host guardado en SettingsStore.
    private val baseUrl: String =
        BuildConfig.PORTAL_HOST.takeIf { it.isNotBlank() } ?: "https://portal.invalid/"

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttp(
        @ApplicationContext context: Context,
        session: SettingsStore,
        device: DeviceProfile
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                // En debug se registra el cuerpo para verificar el contrato.
                // En release no se registra nada.
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        val bundledAuth = runCatching {
            context.assets.open("xuper_auth.txt").bufferedReader().use { it.readText() }
        }.getOrDefault("")
        return OkHttpClient.Builder()
            .addInterceptor(PortalInterceptor(session, device))
            .addInterceptor(XuperAuthInterceptor(session, bundledAuth))
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, moshi: Moshi): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    fun providePortalApi(retrofit: Retrofit): PortalApi = retrofit.create(PortalApi::class.java)
}
