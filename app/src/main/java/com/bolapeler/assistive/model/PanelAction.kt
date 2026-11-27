package com.bolapeler.assistive.model

import android.graphics.drawable.Drawable

data class PanelAction(
    val id: String,
    val label: String,
    val iconRes: Int = 0,
    val appDrawable: Drawable? = null,
    val packageName: String? = null
)
