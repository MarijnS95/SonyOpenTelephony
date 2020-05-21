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

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemProperties
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.Log
import org.xmlpull.v1.XmlPullParser

private const val TAG = "ModemConfigReceiver"
private const val VERBOSE = false
private val newlineMatch = Regex("[\n\r]")

class ModemConfigService : Service() {
    private class ProviderFilter(val sim_config_id: String,
                                 val mcc: String? = null,
                                 val mnc: String? = null,
                                 val gid1: String? = null,
                                 val imsi: Regex? = null,
                                 val sp: Regex? = null
    ) {
        override fun toString() = "[mcc: $mcc, mnc: $mnc, gid1: $gid1, imsi: ${imsi?.pattern}, " +
                                  "sp: ${sp?.pattern}] => $sim_config_id"

        fun specificity() = listOfNotNull(mcc, mnc, gid1, imsi, sp).count()
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
modemconfig/ModemConfigReceiver.kt:63:70: error: unresolved reference. None of the following
    candidates is applicable because of receiver type mismatch:
@InlineOnly public inline fun <T : Closeable?, R> ???.use(block: (???) -> ???): ???
    defined in kotlin.io
context.resources.getXml(R.xml.service_provider_sim_configs).use {
         */
        val xml = context.resources.getXml(R.xml.service_provider_sim_configs)
        try {
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.END_TAG &&
                    xml.name == "service_provider_sim_config") {
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
                    continue

                if (xml.name == "service_provider_sim_config") {
                    // Start collection for this config id:
                    simConfigId = xml.getAttributeValue(null, "sim_config_id")
                    if (TextUtils.isEmpty(simConfigId))
                        throw Exception("Unexpected empty sim_config_id attribute!")
                    currentMap.clear()
                } else if (simConfigId != null) {
                    // If not in a service_provider_sim_config element, and a valid simConfigId
                    // /exists parse all members/subelements:
                    val text = cleanString(xml.nextText())
                    if (currentMap.containsKey(xml.name))
                        throw Exception("Already parsed ${xml.name}!")
                    currentMap[xml.name] = text
                }
            }

        } finally {
            xml.close()
        }

        list.sortByDescending { it.specificity() }
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

        if (VERBOSE) Log.v(TAG, "Matching providers against: $sp $mcc/$mnc, imsi: $imsi" +
                                ", gid: $gid1, iccid2: $iccid2")

        val result = getProviders(context).firstOrNull f@{ info ->
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

        return if (result == null) {
            Log.w(TAG, "No matching config found")
            null
        } else {
            Log.i(TAG, "Matched with $result")
            result.sim_config_id
        }
    }

    private fun handleSubscription(sub: SubscriptionInfo) {
        if (VERBOSE) Log.v(TAG, "Checking sub $sub")

        val globalTm = getSystemService(TelephonyManager::class.java)
        val tm = globalTm!!.createForSubscriptionId(sub.subscriptionId)
        val name = findConfigurationName(this, tm)

        if (name != null) {
            val prop = "persist.vendor.somc.cust.modem${sub.simSlotIndex}"
            if (VERBOSE) Log.v(TAG, "Setting $prop to $name")
            SystemProperties.set(prop, name)
        }
    }

    override fun onCreate() {
        Log.e(TAG, "Starting")
        val sm = getSystemService(SubscriptionManager::class.java)
                 ?: throw Exception("Expected SubscriptionManager, got null")

        // Our callback is invoked once on .add too; no need to run the contents manually at startup
        sm.addOnSubscriptionsChangedListener(
                object : SubscriptionManager.OnSubscriptionsChangedListener() {
                    // Even though this callback is invoked a lot (when sims are changed), apply no
                    // caching at all. The modem-switcher itself makes sure to not needlessly flash
                    // firmware - let it handle the validation.
                    override fun onSubscriptionsChanged() {
                        sm.activeSubscriptionInfoList?.forEach(::handleSubscription)
                    }
                })
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
