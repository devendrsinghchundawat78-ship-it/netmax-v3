@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class, com.lagradost.cloudstream3.Prerelease::class)
package com.lagradost.cloudstream3.utils

class UiText private constructor(private val value: String) {
    fun asString(context: Any?): String = value
    override fun toString(): String = value

    companion object {
        fun string(value: String): UiText = UiText(value)
        fun stringRes(res: Int, vararg args: Any): UiText = UiText("string/$res")
    }
}
