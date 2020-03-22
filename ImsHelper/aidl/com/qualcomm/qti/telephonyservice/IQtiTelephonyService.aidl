package com.qualcomm.qti.telephonyservice;

import com.qualcomm.qti.telephonyservice.InitResponse;

interface IQtiTelephonyService {
    String vers();
    InitResponse init(in byte[] sp, String nqdn, int slotId, int appl, boolean fbs);
    byte[] get(int slotId, int appl, boolean secure);
}
