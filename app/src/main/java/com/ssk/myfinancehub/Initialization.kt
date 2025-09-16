package com.ssk.myfinancehub

import android.app.Application
import com.zoho.catalyst.setup.ZCatalystApp
import com.zoho.catalyst.setup.ZCatalystSDKConfigs

class Initialization:Application() {
    override fun onCreate() {
        super.onCreate()
        ZCatalystApp.init(
            context = applicationContext,
            environment =  ZCatalystSDKConfigs.Environment.DEVELOPMENT
        )

    }
}