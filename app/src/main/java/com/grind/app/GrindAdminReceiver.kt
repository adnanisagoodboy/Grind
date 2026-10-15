package com.grind.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** While this is active, Android refuses to uninstall Grind until admin is turned off first. **/
class GrindAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Turning this off lets you uninstall Grind. Is that really what you want?"
}
