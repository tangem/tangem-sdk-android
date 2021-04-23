package com.tangem.tester.common

import com.google.gson.Gson
import com.google.gson.GsonBuilder

interface JsonConverter {
    fun toJson(from: Any): String?
    fun <T> fromJson(from: String, clazz: Class<T>): T?
}

class GsonConverter : JsonConverter {
    private val gson: Gson = GsonBuilder().create()

    override fun toJson(from: Any): String? {
        return gson.toJson(from)
    }

    override fun <T> fromJson(from: String, clazz: Class<T>): T? {
        return gson.fromJson(from, clazz)
    }
}