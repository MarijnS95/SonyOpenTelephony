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

import android.hardware.radio.V1_0.RadioResponseInfo
import android.hardware.radio.deprecated.V1_0.IOemHook
import android.hardware.radio.deprecated.V1_0.IOemHookResponse
import android.util.Log
import vendor.qti.hardware.radio.qcrilhook.V1_0.IQtiOemHook
import vendor.qti.hardware.radio.qcrilhook.V1_0.IQtiOemHookResponse
import vendor.qti.hardware.radio.qcrilhook.V1_0.RadioError

private const val TAG = "HookMediator-Generic"

internal fun getOemHook(responseHandler: IGenericOemHookResponse): IGenericOemHook {
    return try {
        val qtiOemHook = IQtiOemHook.getService("oemhook0")
        Log.i(TAG, "Using IQtiOemHook $qtiOemHook")
        QtiOemHook(responseHandler, qtiOemHook)
    } catch (unused: NoSuchElementException) {
        Log.w(TAG, "Failed to get IQtiOemHook, falling back to deprecated IOemHook")
        val oemHook = IOemHook.getService("slot1")
        Log.d(TAG, "Using IOemHook $oemHook")
        AndroidOemHook(responseHandler, oemHook)
    }
}

internal interface IGenericOemHook {
    fun sendCommand(requestId: Int, data: ArrayList<Byte>)
}

internal interface IGenericOemHookResponse {
    fun onResponse(requestId: Int, error: Int, data: ArrayList<Byte>)
}

internal class AndroidOemHook(
        responseHandler: IGenericOemHookResponse,
        private val mOemHook: IOemHook
) : IGenericOemHook {
    private val respCallback = object : IOemHookResponse.Stub() {
        override fun sendRequestRawResponse(responseInfo: RadioResponseInfo, data: ArrayList<Byte>) {
            Log.d(TAG, "sendRequestRawResponse: $responseInfo $data")
            responseHandler.onResponse(responseInfo.serial, responseInfo.error, data)
        }

        override fun sendRequestStringsResponse(responseInfo: RadioResponseInfo, data: ArrayList<String>) {
            Log.w(TAG, "Unexpected sendRequestStringsResponse: $responseInfo $data")
        }
    }

    init {
        mOemHook.setResponseFunctions(respCallback, /* Not interested in unsolicited indications */ null)
    }

    override fun sendCommand(requestId: Int, data: ArrayList<Byte>) {
        Log.d(TAG, "Sending request for $requestId")
        mOemHook.sendRequestRaw(requestId, data)
    }
}

internal class QtiOemHook(
        responseHandler: IGenericOemHookResponse,
        private val mQtiOemHook: IQtiOemHook
) : IGenericOemHook {
    init {
        mQtiOemHook.setCallback(object : IQtiOemHookResponse.Stub() {
            override fun oemHookRawResponse(serial: Int, error: Int, data: ArrayList<Byte>) {
                Log.d(TAG, "oemHookRawResponse[$serial]: ${RadioError.toString(error)} $data")
                responseHandler.onResponse(serial, error, data)
            }
        }, /* Not interested in unsolicited indications */ null)
    }

    override fun sendCommand(requestId: Int, data: ArrayList<Byte>) {
        Log.d(TAG, "Sending request for $requestId")
        mQtiOemHook.oemHookRawRequest(requestId, data)
    }
}
