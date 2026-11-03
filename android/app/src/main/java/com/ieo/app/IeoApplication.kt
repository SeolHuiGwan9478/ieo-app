package com.ieo.app

import android.app.Application
import com.ieo.app.di.AppContainer
import com.ieo.app.di.DefaultAppContainer

/** 앱이 켜질 때 한 번 만들어진다. 앱 전체가 쓰는 [container]를 들고 있다 */
class IeoApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
