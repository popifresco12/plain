package com.plain.app.data

import android.content.Context
import android.content.SharedPreferences

object AuthManager {
    private const val PREFS_NAME = "plain_auth"
    private const val KEY_TOKEN = "auth_token"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedToken = prefs?.getString(KEY_TOKEN, null)
        if (savedToken != null) {
            ApiClient.setToken(savedToken)
        }
    }

    fun saveToken(token: String) {
        prefs?.edit()?.putString(KEY_TOKEN, token)?.apply()
        ApiClient.setToken(token)
    }

    fun getToken(): String? {
        return prefs?.getString(KEY_TOKEN, null)
    }

    fun clearToken() {
        prefs?.edit()?.remove(KEY_TOKEN)?.apply()
        ApiClient.setToken(null)
    }
}
