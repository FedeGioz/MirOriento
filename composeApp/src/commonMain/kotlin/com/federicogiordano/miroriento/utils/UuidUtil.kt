package com.federicogiordano.miroriento.utils

fun generateUUID(): String {
    val random = kotlin.random.Random.Default
    return "studente-${random.nextLong().toString(16).padStart(12, '0')}"
}