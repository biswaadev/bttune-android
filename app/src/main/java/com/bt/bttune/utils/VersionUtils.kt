package com.bt.bttune.utils

object VersionUtils {
    /**
     * Returns true if [remoteVersion] is strictly greater than [currentVersion].
     * Safely handles "v6.0.5", "6.0.5", "v6.0.5-release", "6.0.10", etc.
     */
    fun isVersionGreater(remoteVersion: String, currentVersion: String): Boolean {
        if (remoteVersion.isBlank() || currentVersion.isBlank()) return false

        val cleanRemote = remoteVersion.trim().removePrefix("v").removePrefix("V").substringBefore("-")
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V").substringBefore("-")

        val partsRemote = cleanRemote.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        val partsCurrent = cleanCurrent.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }

        val maxLen = maxOf(partsRemote.size, partsCurrent.size)
        for (i in 0 until maxLen) {
            val r = partsRemote.getOrElse(i) { 0 }
            val c = partsCurrent.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
