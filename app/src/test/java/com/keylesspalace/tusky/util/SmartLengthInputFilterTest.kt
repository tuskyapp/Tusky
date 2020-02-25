package com.keylesspalace.tusky.util

import android.text.SpannableStringBuilder
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [28])
@RunWith(AndroidJUnit4::class)
class SmartLengthInputFilterTest {


    @Test
    fun shouldNotTrimStatusWithLength0() {
        assertFalse(shouldTrimStatus(SpannableStringBuilder("")))
    }

    @Test
    fun shouldNotTrimStatusWithLength10() {
        assertFalse(shouldTrimStatus(SpannableStringBuilder("0123456789")))
    }

    @Test
    fun shouldNotTrimStatusWithLength500() {
        assertFalse(shouldTrimStatus(SpannableStringBuilder("u1Pc5TbDVYFnzIdqlQkb3xuZ2S61fFD1K4u" +
                "cb3q40dnELjAsWxnSH59jqly249Spr0Vod029zfwFHYQ0PkBCNQ7tuk90h6aY661RFC7vhIKJna4yDYOBFj" +
                "RR9u0CsUa6vlgEE5yUrk5LKn3bmnnzRCXmU6HyT2bFu256qoUWbmMQ6GFXUXjO28tru8Q3UiXKLgrotKdSH" +
                "mmqPwQgtatbMykTW4RZdKTE46nzlbD3mXHdWQkf4uVPYhVT1CMvVbCPMaimfQ0xuU8CpxyVqA8a6lCL3YX9" +
                "pNnZjD7DoCg2FCejANnjXsTF6vuqPSHjQZDjy696nSAFy95p9kBeJkc70fHzX5TcfUqSaNtvx3LUtpIkwh4" +
                "q2EYmKISPsxlANaspEMPuX6r9fSACiEwmHsitZkp4RMKZq5NqRsGPCiAXcNIN3jj9fCYVGxUwVxVeCescDG" +
                "5naEEszIR3FT1RO4MSn9c2ZZi0UdLizd8ciJAIuwwmcVyYyyM4")))
    }

    @Test
    fun shouldNotTrimStatusWithLength666() {
        assertFalse(shouldTrimStatus(SpannableStringBuilder("hIAXqY7DYynQGcr3zxcjCjNZFcdwAzwnWv" +
                "NHONtT55rO3r2faeMRZLTG3JlOshq8M1mtLRn0Ca8M9w82nIjJDm1jspxhFc4uLFpOjb9Gm2BokgRftA8ih" +
                "pv6wvMwF5Fg8V4qa8GcXcqt1q7S9g09S3PszCXG4wnrR6dp8GGc9TqVArgmoLSc9EVREIRcLPdzkhV1WWM9" +
                "ZWw7josT27BfBdMWk0ckQkClHAyqLtlKZ84WamxK2q3NtHR5gr7ohIjU8CZoKDjv1bA8ZI8wBesyOhqbmHf" +
                "0Ltypq39WKZ63VTGSf5Dd9kuTEjlXJtxZD1DXH4FFplY45DH5WuQ61Ih5dGx0WFEEVb1L3aku3Ht8rKG7YU" +
                "bOPeanGMBmeI9YRdiD4MmuTUkJfVLkA9rrpRtiEYw8RS3Jf9iqDkTpES9aLQODMip5xTsT4liIcUbLo0Z1d" +
                "NhHk7YKubigNQIm1mmh2iU3Q0ZEm8TraDpKu2o27gIwSKbAnTllrOokprPxWQWDVrN9bIliwGHzgTKPI5z8" +
                "gUybaqewxUYe12GvxnzqpfPFvvHricyZAC9i6Fkil5VmFdae75tLFWRBfE8Wfep0dSjL751m2yzvzZTc6uZ" +
                "RTcUiipvl42DaY8Z5eG2b6xPVhvXshMORvHzwhJhPkHSbnwXX5K")))
    }

    @Test
    fun shouldTrimStatusWithLength667() {
        assertTrue(shouldTrimStatus(SpannableStringBuilder("hIAXqY7DYynQGcr3zxcjCjNZFcdwAzwnWv" +
                "NHONtT55rO3r2faeMRZLTG3JlOshq8M1mtLRn0Ca8M9w82nIjJDm1jspxhFc4uLFpOjb9Gm2BokgRftA8ih" +
                "pv6wvMwF5Fg8V4qa8GcXcqt1q7S9g09S3PszCXG4wnrR6dp8GGc9TqVArgmoLSc9EVREIRcLPdzkhV1WWM9" +
                "ZWw7josT27BfBdMWk0ckQkClHAyqLtlKZ84WamxK2q3NtHR5gr7ohIjU8CZoKDjv1bA8ZI8wBesyOhqbmHf" +
                "0Ltypq39WKZ63VTGSf5Dd9kuTEjlXJtxZD1DXH4FFplY45DH5WuQ61Ih5dGx0WFEEVb1L3aku3Ht8rKG7YU" +
                "bOPeanGMBmeI9YRdiD4MmuTUkJfVLkA9rrpRtiEYw8RS3Jf9iqDkTpES9aLQODMip5xTsT4liIcUbLo0Z1d" +
                "NhHk7YKubigNQIm1mmh2iU3Q0ZEm8TraDpKu2o27gIwSKbAnTllrOokprPxWQWDVrN9bIliwGHzgTKPI5z8" +
                "gUybaqewxUYe12GvxnzqpfPFvvHricyZAC9i6Fkil5VmFdae75tLFWRBfE8Wfep0dSjL751m2yzvzZTc6uZ" +
                "RTcUiipvl42DaY8Z5eG2b6xPVhvXshMORvHzwhJhPkHSbnwXX5K1")))
    }

    @Test
    fun shouldTrimStatusWithLength1000() {
        assertTrue(shouldTrimStatus(SpannableStringBuilder("u1Pc5TbDVYFnzIdqlQkb3xuZ2S61fFD1K4u" +
                "cb3q40dnELjAsWxnSH59jqly249Spr0Vod029zfwFHYQ0PkBCNQ7tuk90h6aY661RFC7vhIKJna4yDYOBFj" +
                "RR9u0CsUa6vlgEE5yUrk5LKn3bmnnzRCXmU6HyT2bFu256qoUWbmMQ6GFXUXjO28tru8Q3UiXKLgrotKdSH" +
                "mmqPwQgtatbMykTW4RZdKTE46nzlbD3mXHdWQkf4uVPYhVT1CMvVbCPMaimfQ0xuU8CpxyVqA8a6lCL3YX9" +
                "pNnZjD7DoCg2FCejANnjXsTF6vuqPSHjQZDjy696nSAFy95p9kBeJkc70fHzX5TcfUqSaNtvx3LUtpIkwh4" +
                "q2EYmKISPsxlANaspEMPuX6r9fSACiEwmHsitZkp4RMKZq5NqRsGPCiAXcNIN3jj9fCYVGxUwVxVeCescDG" +
                "5naEEszIR3FT1RO4MSn9c2ZZi0UdLizd8ciJAIuwwmcVyYyyM4"+
                "u1Pc5TbDVYFnzIdqlQkb3xuZ2S61fFD1K4u" +
                "cb3q40dnELjAsWxnSH59jqly249Spr0Vod029zfwFHYQ0PkBCNQ7tuk90h6aY661RFC7vhIKJna4yDYOBFj" +
                "RR9u0CsUa6vlgEE5yUrk5LKn3bmnnzRCXmU6HyT2bFu256qoUWbmMQ6GFXUXjO28tru8Q3UiXKLgrotKdSH" +
                "mmqPwQgtatbMykTW4RZdKTE46nzlbD3mXHdWQkf4uVPYhVT1CMvVbCPMaimfQ0xuU8CpxyVqA8a6lCL3YX9" +
                "pNnZjD7DoCg2FCejANnjXsTF6vuqPSHjQZDjy696nSAFy95p9kBeJkc70fHzX5TcfUqSaNtvx3LUtpIkwh4" +
                "q2EYmKISPsxlANaspEMPuX6r9fSACiEwmHsitZkp4RMKZq5NqRsGPCiAXcNIN3jj9fCYVGxUwVxVeCescDG" +
                "5naEEszIR3FT1RO4MSn9c2ZZi0UdLizd8ciJAIuwwmcVyYyyM4")))
    }
}
