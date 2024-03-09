package xyz.stalinsky.ampd.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.coroutines.flow.map
import xyz.stalinsky.ampd.Settings
import xyz.stalinsky.ampd.SettingsKt
import xyz.stalinsky.ampd.settings
import java.io.InputStream
import java.io.OutputStream

private const val DATA_STORE_FILE_NAME = "settings.pb"

object SettingsSerializer : Serializer<Settings> {
    override val defaultValue: Settings = settings {
        tabs.addAll(listOf(SettingsKt.tab {
            enabled = true
            type = Settings.TabType.TAB_TYPE_ARTISTS
        },
        SettingsKt.tab {
            enabled = true
            type = Settings.TabType.TAB_TYPE_ALBUMS
        }))
    }

    override suspend fun readFrom(input: InputStream): Settings {
        try {
            return Settings.parseFrom(input)
        } catch (e: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", e)
        }
    }

    override suspend fun writeTo(t: Settings, output: OutputStream) =
        t.writeTo(output)
}

val Context.settingsStore: DataStore<Settings> by dataStore(
    fileName = DATA_STORE_FILE_NAME,
    serializer = SettingsSerializer
)

class UiConfigRepository(private val settingsStore: DataStore<Settings>) {
    val tabs = settingsStore.data.map {
        it.tabsList
    }

    val defaultTab = settingsStore.data.map {
        it.defaultTab
    }

    suspend fun setDefaultTab(tab: Int) {
        settingsStore.updateData {
            it.toBuilder().setDefaultTab(tab).build()
        }
    }

    suspend fun setTabs(tabs: List<Settings.Tab>) {
        settingsStore.updateData {
            it.toBuilder().clearTabs().addAllTabs(tabs).build()
        }
    }
}

class SettingsRepository(private val settingsStore: DataStore<Settings>) {
    val mpdHost = settingsStore.data.map {
        it.mpdHost
    }

    val mpdPort =  settingsStore.data.map {
        it.mpdPort
    }

    val libraryHost = settingsStore.data.map {
        it.libraryHost
    }

    val libraryPort = settingsStore.data.map {
        it.libraryPort
    }

    suspend fun setMpdHost(host: String) {
        settingsStore.updateData {
            it.toBuilder().setMpdHost(host).build()
        }
    }

    suspend fun setMpdPort(port: Int) {
        settingsStore.updateData {
            it.toBuilder().setMpdPort(port).build()
        }
    }

    suspend fun setLibraryHost(host: String) {
        settingsStore.updateData {
            it.toBuilder().setLibraryHost(host).build()
        }
    }

    suspend fun setLibraryPort(port: Int) {
        settingsStore.updateData {
            it.toBuilder().setLibraryPort(port).build()
        }
    }
}