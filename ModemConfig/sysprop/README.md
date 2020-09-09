### Updating sysprop files

#### Android 11 (R)

Optional when not lunched for a device:
```sh
lunch sdk # (Can use sdk-eng too)
export ALLOW_MISSING_DEPENDENCIES=true # Ignore errors about hostapd, wpa_supplicant etc missing
```

```sh
build/soong/scripts/gen-sysprop-api-files.sh "vendor/oss/opentelephony/ModemConfig/sysprop" "SomcModemProperties"
m SomcModemProperties-dump-api && rm -rf vendor/oss/opentelephony/ModemConfig/sysprop/api/SomcModemProperties-current.txt && cp -f out/soong/.intermediates/vendor/oss/opentelephony/ModemConfig/sysprop/SomcModemProperties_sysprop_library/api-dump.txt vendor/oss/opentelephony/ModemConfig/sysprop/api/SomcModemProperties-current.txt
```
