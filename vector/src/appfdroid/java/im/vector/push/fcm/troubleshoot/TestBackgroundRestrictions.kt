/*
 * Copyright 2018 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package im.vector.push.fcm.troubleshoot

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.net.ConnectivityManagerCompat
import androidx.fragment.app.Fragment
import im.vector.R
import im.vector.fragments.troubleshoot.TroubleshootTest

class TestBackgroundRestrictions(val fragment: Fragment) : TroubleshootTest(R.string.settings_troubleshoot_test_bg_restricted_title) {

    override fun perform() {
        (fragment.context!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).apply {
            // Checks if the device is on a metered network
            if (isActiveNetworkMetered) {
                // Checks user’s Data Saver settings.
                val restrictBackgroundStatus = ConnectivityManagerCompat.getRestrictBackgroundStatus(this)
                when (restrictBackgroundStatus) {
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> {
                        // Background data usage is blocked for this app. Wherever possible,
                        // the app should also use less data in the foreground.
                        description = fragment.getString(R.string.settings_troubleshoot_test_bg_restricted_failed,
                                "RESTRICT_BACKGROUND_STATUS_ENABLED")
                        status = TestStatus.FAILED
                        quickFix = null
                    }
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> {
                        // The app is whitelisted. Wherever possible,
                        // the app should use less data in the foreground and background.
                        description = fragment.getString(R.string.settings_troubleshoot_test_bg_restricted_success,
                                "RESTRICT_BACKGROUND_STATUS_WHITELISTED")
                        status = TestStatus.SUCCESS
                        quickFix = null
                    }
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> {
                        // Data Saver is disabled. Since the device is connected to a
                        // metered network, the app should use less data wherever possible.
                        description = fragment.getString(R.string.settings_troubleshoot_test_bg_restricted_success,
                                "RESTRICT_BACKGROUND_STATUS_DISABLED")
                        status = TestStatus.SUCCESS
                        quickFix = null
                    }

                }

            } else {
                // The device is not on a metered network.
                // Use data as required to perform syncs, downloads, and updates.
                description = fragment.getString(R.string.settings_troubleshoot_test_bg_restricted_success, "")
                status = TestStatus.SUCCESS
                quickFix = null
            }
        }
    }

}