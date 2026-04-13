package com.lush1us.podder.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(
    val label: String,
    val icon: ImageVector,
    val route: String,
) {
    FEED      ("Feed",       Icons.Default.Home,         "feed"),
    PODCASTS  ("Podcasts",   Icons.Default.Mic,          "podcasts"),
    MUSIC     ("Music",      Icons.Default.LibraryMusic,  "music"),
    AUDIOBOOKS("Audiobooks", Icons.Default.MenuBook,      "audiobooks"),
    RADIO     ("Radio",      Icons.Default.Radio,         "radio"),
}
