package com.plain.app.data

import android.content.Context
import android.content.SharedPreferences

object AuthManager {
    private const val PREFS_NAME = "plain_auth"
    private const val KEY_USER_TOKEN = "user_token"
    private const val KEY_BUSINESS_TOKEN = "business_token"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUserToken = prefs?.getString(KEY_USER_TOKEN, null)
        val savedBusinessToken = prefs?.getString(KEY_BUSINESS_TOKEN, null)
        if (savedUserToken != null) {
            ApiClient.setUserToken(savedUserToken)
        }
        if (savedBusinessToken != null) {
            ApiClient.setBusinessToken(savedBusinessToken)
        }
    }

    fun saveUserToken(token: String) {
        prefs?.edit()?.putString(KEY_USER_TOKEN, token)?.apply()
        ApiClient.setUserToken(token)
    }

    fun saveBusinessToken(token: String) {
        prefs?.edit()?.putString(KEY_BUSINESS_TOKEN, token)?.apply()
        ApiClient.setBusinessToken(token)
    }

    fun getUserToken(): String? = prefs?.getString(KEY_USER_TOKEN, null)

    fun getBusinessToken(): String? = prefs?.getString(KEY_BUSINESS_TOKEN, null)

    // Legacy method for backward compatibility
    fun saveToken(token: String) {
        // Assume user token by default for backward compat
        saveUserToken(token)
    }

    fun getToken(): String? = getUserToken()

    fun clearToken() {
        clearUserToken()
    }

    fun clearUserToken() {
        prefs?.edit()?.remove(KEY_USER_TOKEN)?.apply()
        ApiClient.setUserToken(null)
    }

    fun clearBusinessToken() {
        prefs?.edit()?.remove(KEY_BUSINESS_TOKEN)?.apply()
        ApiClient.setBusinessToken(null)
    }

    fun clearTokenForPath(path: String) {
        if (path.contains("api/business")) {
            clearBusinessToken()
        } else {
            clearUserToken()
        }
    }

    fun clearAllTokens() {
        clearUserToken()
        clearBusinessToken()
    }
}