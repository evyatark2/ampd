package xyz.stalinsky.ampd.car

import android.content.Intent
import androidx.annotation.OptIn
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.media.model.MediaPlaybackTemplate
import androidx.car.app.model.Header
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver

class CarScreen(context: CarContext) : Screen(context) {
    @OptIn(ExperimentalCarApi::class)
    override fun onGetTemplate(): Template {
        return MediaPlaybackTemplate.Builder().setHeader(Header.Builder().setTitle("AMPD").build()).build()
    }
}

class CarSession : Session(), DefaultLifecycleObserver {

    override fun onCreateScreen(intent: Intent): Screen {
        lifecycle.addObserver(this)
        return CarScreen(carContext)
    }
}

class CarService : CarAppService() {
    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        return CarSession()
    }

    override fun createHostValidator(): HostValidator {
        return HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
    }
}