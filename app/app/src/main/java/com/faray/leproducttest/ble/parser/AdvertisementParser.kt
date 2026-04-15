package com.faray.leproducttest.ble.parser

import com.faray.leproducttest.common.MacAddressCodec

class AdvertisementParser {

    fun parseMacFromName(advName: String, expectedPrefix: String): Long? {
        if (!advName.startsWith(expectedPrefix)) {
            return null
        }
        val suffix = advName.removePrefix(expectedPrefix)
        if (!MacAddressCodec.isUppercaseHex12(suffix)) {
            return null
        }
        return MacAddressCodec.parseToLong(suffix)
    }
}
