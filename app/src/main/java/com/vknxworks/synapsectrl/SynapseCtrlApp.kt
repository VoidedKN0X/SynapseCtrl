package com.vknxworks.synapsectrl

import android.app.Application
import com.vknxworks.synapsectrl.data.notify.Notifier

class SynapseCtrlApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
    }
}
