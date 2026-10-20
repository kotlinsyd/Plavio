package com.pulsestream.app.data

import android.animation.ValueAnimator
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object MotionPreferences {
    private val mutableReduced = MutableStateFlow(false)
    val reduced: StateFlow<Boolean> = mutableReduced
    fun initialize(context: Context) {
        mutableReduced.value = context.getSharedPreferences("plavio_accessibility", Context.MODE_PRIVATE)
            .getBoolean("reduced_motion", false)
    }
    fun setReduced(context: Context, value: Boolean) {
        check(context.getSharedPreferences("plavio_accessibility", Context.MODE_PRIVATE).edit()
            .putBoolean("reduced_motion", value).commit()) { "Couldn't save accessibility choice" }
        mutableReduced.value = value
    }
    fun animationsEnabled(): Boolean = !reduced.value && ValueAnimator.areAnimatorsEnabled()
}

@Composable
fun rememberReducedMotion(): Boolean {
    val reduced by MotionPreferences.reduced.collectAsState()
    return reduced || !ValueAnimator.areAnimatorsEnabled()
}
