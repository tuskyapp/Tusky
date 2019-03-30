package tech.bigfig.roma

/**
 * Created by charlag on 3/7/18.
 */

class FakeRomaApplication : RomaApplication() {

    private lateinit var locator: ServiceLocator

    override fun initSecurityProvider() {
        // No-op
    }

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