package io.github.javiernarvaezz.shura

import okhttp3.Interceptor

/** Release builds have no diagnostic network interceptors (ADR 0001 R7). */
internal fun debugNetworkInterceptors(): List<Interceptor> = emptyList()
