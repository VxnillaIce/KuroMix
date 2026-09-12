package com.kuromify.kuromix.data

import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val componentName: String, // packageName/ActivityClass, ready for `am start -n`
    val icon: Drawable?,
)
