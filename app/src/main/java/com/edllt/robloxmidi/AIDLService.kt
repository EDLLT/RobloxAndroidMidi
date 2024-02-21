/*
 * Copyright 2023 John "topjohnwu" Wu
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
package com.edllt.robloxmidi

import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.topjohnwu.superuser.ipc.RootService
import java.util.UUID

// Demonstrate RootService using AIDL (daemon mode)
internal class AIDLService : RootService() {
    // Demonstrate we can also run native code via JNI with RootServices

    external fun touchDownCommit(x: Int, y: Int, contact: Int): Int
    external fun touchUpCommit(contact: Int): Int
    external fun tapCommit(x: Int, y: Int, contact: Int): Int
    val uuid = UUID.randomUUID().toString()
    internal inner class AIDLIPC : IAIDLIPCService.Stub() {
        override fun pianoKey(x: Int, y: Int, contact: Int, isDown: String): String {
            var status = 500
            // Do something!

            // More accurate but roblox piano doesn't support it
//            if (isDown == "Down") {
//                status = touchDownCommit(x, y, contact)
//            } else if (isDown == "Up") {
//                status = touchUpCommit(contact)
//            }

            if (isDown == "Down") {
                status = tapCommit(x, y, contact)
            }

            if (status == 0) {
                return "Acknowledged : $x $y $contact $isDown"
            } else {
                return "Failure"
            }
        }
    }


    override fun onCreate() {
        Log.d(MainActivity.TAG, "AIDLService: onCreate, $uuid")
    }

    override fun onRebind(intent: Intent) {
        // This callback will be called when we are reusing a previously started root process
        Log.d(MainActivity.TAG, "AIDLService: onRebind, daemon process reused")
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(MainActivity.TAG, "AIDLService: onBind")
        return AIDLIPC()
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.d(MainActivity.TAG, "AIDLService: onUnbind, client process unbound")
        // Return true here so onRebind will be called
        return true
    }

    override fun onDestroy() {
        Log.d(MainActivity.TAG, "AIDLService: onDestroy")
    }

    companion object {
        init {
            // Only load the library when this class is loaded in a root process.
            // The classloader will load this class (and call this static block) in the non-root
            // process because we accessed it when constructing the Intent to send.
            // Add this check so we don't unnecessarily load native code that'll never be used.
            if (Process.myUid() == 0) {
                System.loadLibrary("robloxmidi")
            } else {
                Log.e(MainActivity.TAG, "FAILED to acquire ROOT access!")
            }
        }
    }
}
