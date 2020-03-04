/*
 * Copyright (C) 2020 Marijn Suijten
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sony.opentelephony.hookmediator

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "HookMediator-Service"

class HookMediatorService : Service() {
    private val responseHandler = object : IGenericOemHookResponse {
        override fun onResponse(requestId: Int, error: Int, data: ArrayList<Byte>) {
            val client = requests[requestId]
            if (client == null)
                Log.w(TAG, "No client for request $requestId")
        }
    }
    private val oemHook by lazy { getOemHook(responseHandler) }
    private var sNextSerial = AtomicInteger(1000)

    override fun onBind(intent: Intent?): IBinder? = null

    class Client

    private val requests = hashMapOf<Int, Client>()

    private fun sendRequest(forClient: Client, data: ArrayList<Byte>) {
        val requestId = sNextSerial.getAndUpdate { (it + 1) % Integer.MAX_VALUE }
        requests[requestId] = forClient
        oemHook.sendCommand(requestId, data)
    }
}
