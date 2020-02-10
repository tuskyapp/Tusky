package com.keylesspalace.tusky.di

import com.keylesspalace.tusky.TuskyApplication

object AppInjector {
    fun init(app: TuskyApplication) {
        // inject the Application, but no Activities or Fragments
        DaggerAppComponent.builder().application(app)
                .build().inject(app)
    }
}
