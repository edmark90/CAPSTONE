package com.example.smartplantcare.ui.theme.screens

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

object SystemUiController {

    fun hideSystemBars(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        val controller = WindowInsetsControllerCompat(
            activity.window,
            activity.window.decorView
        )

        controller.hide(WindowInsetsCompat.Type.systemBars())

        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    fun showSystemBars(activity: Activity) {
        val controller = WindowInsetsControllerCompat(
            activity.window,
            activity.window.decorView
        )

        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}