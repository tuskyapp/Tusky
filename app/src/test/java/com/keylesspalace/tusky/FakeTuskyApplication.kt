package com.keylesspalace.tusky

/**
 * Created by charlag on 3/7/18.
 */

class FakeTuskyApplication : TuskyApplication() {

    lateinit var locator: ServiceLocator

    override fun initAppInjector() {
        // No-op
    }

    override fun initPicasso() {
        // No-op
    }

    override fun getServiceLocator(): ServiceLocator {
        return locator
    }
}