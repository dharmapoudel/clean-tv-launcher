package com.gathin.tvhome;

import android.app.admin.DeviceAdminReceiver;

/**
 * Device admin for one thing only: force-lock, so Home's screen-off timer can truly sleep the
 * TV (the accessibility lock-screen action does not put this set in standby). The owner grants
 * it once, from the system screen Launcher settings opens when a screen-off time is chosen.
 */
public class SleepAdminReceiver extends DeviceAdminReceiver {
}
