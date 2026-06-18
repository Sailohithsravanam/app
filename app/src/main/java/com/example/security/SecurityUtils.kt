package com.example.security

import java.security.MessageDigest

object SecurityUtils {
    private const val SALT = "FinoraaxStaticSaltKey#2026"

    fun hashPin(pin: String): String {
        val saltedInput = pin + SALT
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(saltedInput.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
