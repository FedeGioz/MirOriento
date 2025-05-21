package com.federicogiordano.miroriento.data

import kotlinx.serialization.Serializable

@Serializable
data class StudentInfo(
    val name: String = "",
    val city: String = "",
    val schoolFocus: SchoolFocus = SchoolFocus.INFORMATICA
)

enum class SchoolFocus {
    LOGISTICA, INFORMATICA, ROBOTICA
}