package com.federicogiordano.miroriento

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform