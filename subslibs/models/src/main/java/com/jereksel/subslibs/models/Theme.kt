package com.jereksel.subslibs.models

data class Theme(
        val application: String,
        val type1: List<Type1Data> = listOf(),
        val type2: Type2Data? = null
)