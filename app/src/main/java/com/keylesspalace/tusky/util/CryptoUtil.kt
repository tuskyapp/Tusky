/* Copyright 2022 Tusky contributors
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package com.keylesspalace.tusky.util

import android.util.Base64
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.interfaces.ECPrivateKey
import org.bouncycastle.jce.interfaces.ECPublicKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Security

object CryptoUtil {
    const val CURVE_PRIME256_V1 = "prime256v1"

    private const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

    init {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.addProvider(BouncyCastleProvider())
    }

    private fun secureRandomBytes(len: Int): ByteArray {
        val ret = ByteArray(len)
        SecureRandom.getInstance("SHA1PRNG").nextBytes(ret)
        return ret
    }

    fun secureRandomBytesEncoded(len: Int): String {
        return Base64.encodeToString(secureRandomBytes(len), BASE64_FLAGS)
    }

    data class EncodedKeyPair(val pubkey: String, val privKey: String)

    fun generateECKeyPair(curve: String): EncodedKeyPair {
        val spec = ECNamedCurveTable.getParameterSpec(curve)
        val gen = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
        gen.initialize(spec)
        val keyPair = gen.genKeyPair()
        val pubKey = keyPair.public as ECPublicKey
        val privKey = keyPair.private as ECPrivateKey
        val encodedPubKey = Base64.encodeToString(pubKey.q.getEncoded(false), BASE64_FLAGS)
        val encodedPrivKey = Base64.encodeToString(privKey.d.toByteArray(), BASE64_FLAGS)
        return EncodedKeyPair(encodedPubKey, encodedPrivKey)
    }
}
