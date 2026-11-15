package com.example.universalgamebot

import android.os.Looper
import android.view.WindowManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@LooperMode(LooperMode.Mode.PAUSED)
class EFootballBadgeWindowTest {
    @Test fun queuedShowCannotResurrectAfterHideOrAccessibilityCleanup(){
        val controller=Robolectric.buildService(BotAccessibilityService::class.java).create()
        val service=controller.get();var added=0
        val wm=Proxy.newProxyInstance(WindowManager::class.java.classLoader,arrayOf(WindowManager::class.java)){_,method,_->
            if(method.name=="addView")added++
            null
        } as WindowManager
        val badge=EFootballStatusBadge(service,wm)
        badge.show();badge.hide();shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0,added)
        badge.show();badge.close();shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0,added)
        badge.show();shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0,added)
        controller.destroy()
    }
}
