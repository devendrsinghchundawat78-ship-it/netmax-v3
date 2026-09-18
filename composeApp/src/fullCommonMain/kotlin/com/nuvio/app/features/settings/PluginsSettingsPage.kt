package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import com.nuvio.app.features.plugins.PluginStorePageContent
import com.nuvio.app.features.plugins.PluginsSettingsPageContent

internal actual fun LazyListScope.pluginsSettingsContent() {
    item {
        PluginsSettingsPageContent(
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal actual fun LazyListScope.pluginStoreSettingsContent() {
    item {
        PluginStorePageContent(
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
