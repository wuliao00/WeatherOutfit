package com.jianyi.outfit.ui.glass

import android.os.Build

/** Android 12+ 才有 RenderEffect；低版本整体退回静态着色 */
actual val supportsRealtimeBlur: Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
