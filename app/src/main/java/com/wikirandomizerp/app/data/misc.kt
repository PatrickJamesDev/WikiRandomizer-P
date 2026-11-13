package com.wikirandomizerp.app.data

import java.util.Locale

fun bytesToHumanReadableSize(bytes: Double) = when {
    bytes >= 1 shl 30 -> "%.1f GB".format(bytes / (1 shl 30))
    bytes >= 1 shl 20 -> "%.1f MB".format(bytes / (1 shl 20))
    bytes >= 1 shl 10 -> "%.0f kB".format(bytes / (1 shl 10))
    else -> "$bytes bytes"
}

fun Double.formatToHumanReadable(): String {
    return when {
        this >= 1000000000000000 -> String.format(
            Locale.US,
            "%.2f quadrillion",
            this / 1000000000000000.0
        )

        this >= 1000000000000 -> String.format(Locale.US, "%.2f trillion", this / 1000000000000.0)
        this >= 1000000000 -> String.format(Locale.US, "%.2f billion", this / 1000000000.0)
        this >= 1000000 -> String.format(Locale.US, "%.2f million", this / 1000000.0)
        else -> this.toString()
    }
}

fun Long.formatToHumanReadable(): String {
    return when {
        this >= 1000000000000000 -> String.format(
            Locale.US,
            "%.2f quadrillion",
            this / 1000000000000000.0
        )

        this >= 1000000000000 -> String.format(Locale.US, "%.2f trillion", this / 1000000000000.0)
        this >= 1000000000 -> String.format(Locale.US, "%.2f billion", this / 1000000000.0)
        this >= 1000000 -> String.format(Locale.US, "%.2f million", this / 1000000.0)
        else -> this.toString()
    }
}
