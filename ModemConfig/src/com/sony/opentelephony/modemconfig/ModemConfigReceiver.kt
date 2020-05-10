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

package com.sony.opentelephony.modemconfig

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.Log
import org.xmlpull.v1.XmlPullParser

private const val TAG = "ModemConfigReceiver"
private val newlineMatch = Regex("[\n\r]")

class ModemConfigReceiver : BroadcastReceiver() {
    private class ProviderFilter(val sim_config_id: String,
                                 val mcc: String? = null,
                                 val mnc: String? = null,
                                 val gid1: String? = null,
                                 val imsi: Regex? = null,
                                 val sp: Regex? = null
    ) {
        override fun toString() = "[mcc: $mcc, mnc: $mnc, gid1: $gid1, imsi: ${imsi?.pattern}, sp: ${sp?.pattern}] => $sim_config_id"
    }

    // TODO: Do a lateinit here?
    private /*lateinit*/ var providers: List<ProviderFilter>? = null

    private fun getProviders(context: Context): List<ProviderFilter> {
        if (providers != null)
            return providers!!

        val list = arrayListOf<ProviderFilter>()

        val currentMap = hashMapOf<String, String>()
        var simConfigId: String? = null

        // TODO: Kotlin .use {} block doesn't work here, while it does in regular apps...
        /*
modemconfig/ModemConfigReceiver.kt:63:70: error: unresolved reference. None of the following candidates is applicable because of receiver type mismatch:
@InlineOnly public inline fun <T : Closeable?, R> ???.use(block: (???) -> ???): ??? defined in kotlin.io
context.resources.getXml(R.xml.service_provider_sim_configs).use {
         */
        val xml = context.resources.getXml(R.xml.service_provider_sim_configs)
        try {
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.END_TAG && xml.name == "service_provider_sim_config") {
                    // When a node is closed, collect all elements and create a filter entry:
                    list.add(ProviderFilter(
                            simConfigId!!,
                            currentMap["mcc"],
                            currentMap["mnc"],
                            currentMap["gid1"],
                            currentMap["imsi"]?.let { Regex(it) },
                            currentMap["sp"]?.let { Regex(it) }
                    ))
                    currentMap.clear()
                    continue
                }

                if (xml.eventType != XmlPullParser.START_TAG)
                    continue;

                if (xml.name == "service_provider_sim_config") {
                    // Start collection for this config id:
                    simConfigId = xml.getAttributeValue(null, "sim_config_id")
                    if (TextUtils.isEmpty(simConfigId))
                        throw Exception("Unexpected empty sim_config_id attribute!")
                    currentMap.clear()
                } else if (simConfigId != null) {
                    // If not in a service_provider_sim_config element, and a valid simConfigId exists
                    // parse all members/subelements:
                    val text = cleanString(xml.nextText())
                    if (currentMap.containsKey(xml.name))
                        throw Exception("Already parsed ${xml.name}!")
                    currentMap[xml.name] = text
                }
            }

        } finally {
            xml.close()
        }

        providers = list
        return list
    }

    private fun cleanString(str: String) = str.replace(newlineMatch, "").trim()

    private fun findConfigurationName(context: Context, tm: TelephonyManager): String? {
        val operator = tm.simOperator
        val mcc = operator.substring(0, 3)
        val mnc = operator.substring(3)

        val sp = cleanString(tm.simOperatorName)

        val imsi = tm.subscriberId
        val gid1 = tm.groupIdLevel1
        val iccid2 = tm.simSerialNumber

        Log.d(TAG, "Matching providers against: $sp $mcc/$mnc, imsi: $imsi, gid: $gid1, iccid2: $iccid2")

        val result = getProviders(context).filter f@{ info ->
            if (info.mcc != null && info.mcc != mcc)
                return@f false
            if (info.mnc != null && info.mnc != mnc)
                return@f false

            if (info.gid1 != null && !gid1.startsWith(info.gid1))
                return@f false

            if (info.imsi != null && !info.imsi.matches(imsi))
                return@f false

            if (info.sp != null && !info.sp.matches(sp))
                return@f false

            true
        }

        return if (result.isEmpty()) {
            Log.w(TAG, "No matching config found")
            null
        } else {
            if (result.size > 1)
                Log.w(TAG, "Multiple matches found: ${result}, using last")

            val match = result.last()
            Log.i(TAG, "Matched with $match")
            match.sim_config_id
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received intent! $intent")
        if (SubscriptionManager.ACTION_DEFAULT_SUBSCRIPTION_CHANGED != intent.action)
            throw Exception("Expected ${SubscriptionManager.ACTION_DEFAULT_SUBSCRIPTION_CHANGED}, got ${intent.action}. Is the intent filter correct??")
        val subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.d(TAG, "Received invalid subId. SIM removed?")
            return
        }
        val slotIdx = SubscriptionManager.getSlotIndex(subId) // Can be INVALID_SIM_SLOT_INDEX
        if (slotIdx == SubscriptionManager.INVALID_SIM_SLOT_INDEX)
            throw Exception("Invalid slot index")
        Log.d(TAG, "Finding configuration for subId $subId, slotIdx: $slotIdx")

        val globalTm = context.getSystemService(TelephonyManager::class.java)
        val tm = globalTm!!.createForSubscriptionId(subId)
        val name = findConfigurationName(context, tm)
    }
}
