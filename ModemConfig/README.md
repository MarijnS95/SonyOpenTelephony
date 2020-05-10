# ModemConfig

Simple configurator that uses SIM information from `TelephonyManager` and a pre-extracted mapping to resolve to a configuration name, passed to the modem switcher which provisions the modem with the appropriate firmware.

#### Updating the

#### Testing:
Note that the subscription ID, passed in at the end, must be valid.

```sh
am broadcast -n com.sony.opentelephony.modemconfig/.ModemConfigReceiver -a android.telephony.action.DEFAULT_SUBSCRIPTION_CHANGED --ei android.telephony.extra.SUBSCRIPTION_INDEX 1
```

```sh
m ModemConfig && adb push out/target/product/bahamut/vendor/app/ModemConfig/ModemConfig.apk vendor/app/ModemConfig && adb shell killall com.sony.opentelephony.modemconfig; am broadcast -n com.sony.opentelephony.modemconfig/.ModemConfigReceiver -a android.telephony.action.DEFAULT_SUBSCRIPTION_CHANGED --ei android.telephony.extra.SUBSCRIPTION_INDEX 1
```
