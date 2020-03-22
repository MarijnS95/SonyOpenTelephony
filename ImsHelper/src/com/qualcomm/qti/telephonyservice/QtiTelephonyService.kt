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

package com.qualcomm.qti.telephonyservice

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.google.protobuf.ByteString
import com.sony.opentelephony.ImsHelper
import com.sony.opentelephony.hookmediator.IHooks
import java.util.concurrent.CompletableFuture

private const val TAG = "QtiTelephonyService"

class QtiTelephonyService : Service() {
    private var mHooks = CompletableFuture<IHooks>()

    private val binder = object : IQtiTelephonyService.Stub() {
        override fun vers() = "1"

        override fun init(
                sp: ByteArray,
                nqdn: String,
                slotId: Int,
                appl: Int,
                bs: Boolean
        ): InitResponse? {
            val req = ImsHelper.Command2Req.newBuilder().apply {
                this.sp = ByteString.copyFrom(sp)
                this.nqdn = nqdn
                this.slotId = ImsHelper.Magic1.forNumber(slotId)
                this.appl = ImsHelper.Magic2.forNumber(appl)
                this.bs = bs
            }.build()

            val resBuf = sendProtoMessage(ImsHelper.Magic4.CMD_1, slotId, req.toByteString())
            val res = ImsHelper.Command2Res.parseFrom(resBuf)

            return InitResponse().apply {
                typ = res.typ.number
                response = res.resp.toByteArray()
                bstrxId = res.btrxId
                lt = res.lt
            }
        }

        override fun get(
                slotId: Int,
                appl: Int,
                secure: Boolean
        ): ByteArray? {
            val req = ImsHelper.Command1Req.newBuilder().apply {
                this.slotId = ImsHelper.Magic1.forNumber(slotId)
                this.appl = ImsHelper.Magic2.forNumber(appl)
                this.secure = secure
            }.build()

            val resBuf = sendProtoMessage(ImsHelper.Magic4.CMD_2, slotId, req.toByteString())
            val res = ImsHelper.Command1Res.parseFrom(resBuf)

            return res.data.toByteArray()
        }
    }

    private fun sendProtoMessage(msgId: ImsHelper.Magic4, slotId: Int, pl: ByteString): ByteString {
        val msg = ImsHelper.RilOemMessage.newBuilder().apply {
            token = -1 // TODO
            type = ImsHelper.MessageType.MESSAGE_REQUEST
            id = msgId
            payload = pl
        }.build()

        val hook = mHooks.get() ?: throw IllegalStateException("IHooks is null!")

        val resBuf = hook.sendProtobufCommand(slotId, msg.toByteArray())
        val res = ImsHelper.RilOemMessage.parseFrom(resBuf)

        if (res.type != ImsHelper.MessageType.MESSAGE_RESPONSE)
            throw IllegalStateException("Excepted a response type message")

        if (res.id != msgId)
            throw IllegalStateException("Expected a response for the same message id $msgId")

        if (/* res.hasField(...error) &&  */res.error != ImsHelper.MessageError.STATUS_SUCCESS)
            throw IllegalStateException("Protobuf msg failed with ${res.error}")

        return res.payload
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "Got bound by $intent")
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        val intent = Intent()
        val pkg = IHooks::class.java.getPackage()!!.name
        intent.setClassName(pkg, "$pkg.HookMediatorService")
        val connection = object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
                val hooks = IHooks.Stub.asInterface(service)
                mHooks.complete(hooks)
            }

            override fun onServiceDisconnected(className: ComponentName?) {
                // TODO: Try to reconnect?
                // mHooks.obtrudeValue(null)
            }
        }
        if (!bindService(intent, connection, Context.BIND_AUTO_CREATE))
            throw RuntimeException("Failed to bind IHooks service!")
    }
}
