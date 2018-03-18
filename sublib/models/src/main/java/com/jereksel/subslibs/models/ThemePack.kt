package com.jereksel.subslibs.models

data class ThemePack(
        val themes: List<Theme> = listOf(),
        val type3: Type3Data? = null
)
