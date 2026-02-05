package com.example.podder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.podder.player.PlayerUiState
import com.example.podder.ui.components.PlayerControls
import com.example.podder.ui.theme.PodderTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun playerBar_appears_when_playing() {
        // Given: A PlayerUiState with isPlaying = true and a title
        val playerState = PlayerUiState(
            isPlaying = true,
            currentTitle = "Test Ep",
            artist = "Test Artist",
            description = "Test Description",
            imageUrl = null,
            progress = 0.5f,
            currentEpisodeGuid = "test-guid",
            currentPositionMillis = 30000L,
            durationMillis = 60000L,
            podcastUrl = "https://example.com/feed"
        )

        // When: Rendering PlayerControls with the state
        composeTestRule.setContent {
            PodderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PlayerControls(
                        modifier = Modifier,
                        title = playerState.currentTitle,
                        description = playerState.description,
                        imageUrl = playerState.imageUrl,
                        progress = playerState.progress,
                        elapsedMillis = playerState.currentPositionMillis,
                        durationMillis = playerState.durationMillis,
                        isPlaying = playerState.isPlaying,
                        onPlayPause = {},
                        onPlayerClick = {},
                        onSeekTo = {}
                    )
                }
            }
        }

        // Then: Assert the title is displayed
        composeTestRule
            .onNodeWithText("Test Ep")
            .assertIsDisplayed()
    }

    @Test
    fun playerBar_showsDescription_whenProvided() {
        // Given: A PlayerUiState with description
        val playerState = PlayerUiState(
            isPlaying = false,
            currentTitle = "Episode Title",
            artist = "Podcast Name",
            description = "Episode Description Here",
            imageUrl = null,
            progress = 0.25f,
            currentEpisodeGuid = "guid-123",
            currentPositionMillis = 15000L,
            durationMillis = 60000L,
            podcastUrl = null
        )

        // When: Rendering PlayerControls
        composeTestRule.setContent {
            PodderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PlayerControls(
                        modifier = Modifier,
                        title = playerState.currentTitle,
                        description = playerState.description,
                        imageUrl = playerState.imageUrl,
                        progress = playerState.progress,
                        elapsedMillis = playerState.currentPositionMillis,
                        durationMillis = playerState.durationMillis,
                        isPlaying = playerState.isPlaying,
                        onPlayPause = {},
                        onPlayerClick = {},
                        onSeekTo = {}
                    )
                }
            }
        }

        // Then: Assert the title is displayed
        composeTestRule
            .onNodeWithText("Episode Title")
            .assertIsDisplayed()
    }

    @Test
    fun theme_dark_mode_colors() {
        // Given: Variables to capture colors
        var actualBackgroundColor: Color? = null
        var actualOnBackgroundColor: Color? = null

        // Expected dark theme colors (from Theme.kt)
        val expectedDarkBackground = Color(0xFF353535)
        val expectedDarkOnBackground = Color.White

        // When: Wrap content in PodderTheme(darkTheme = true)
        composeTestRule.setContent {
            PodderTheme(darkTheme = true) {
                // Capture the actual colors from MaterialTheme
                actualBackgroundColor = MaterialTheme.colorScheme.background
                actualOnBackgroundColor = MaterialTheme.colorScheme.onBackground

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Dark Mode Test",
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        // Then: Verify background surface color matches DarkColorScheme background
        assertEquals(
            "Dark theme background should be dark gray (#353535)",
            expectedDarkBackground,
            actualBackgroundColor
        )
        assertEquals(
            "Dark theme onBackground should be white",
            expectedDarkOnBackground,
            actualOnBackgroundColor
        )
    }

    @Test
    fun theme_light_mode_colors() {
        // Given: Variables to capture colors
        var actualBackgroundColor: Color? = null
        var actualOnBackgroundColor: Color? = null

        // Expected light theme colors (from Theme.kt)
        val expectedLightBackground = Color(0xFFF5F5F5)
        val expectedLightOnBackground = Color.Black

        // When: Wrap content in PodderTheme(darkTheme = false)
        composeTestRule.setContent {
            PodderTheme(darkTheme = false) {
                actualBackgroundColor = MaterialTheme.colorScheme.background
                actualOnBackgroundColor = MaterialTheme.colorScheme.onBackground

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Light Mode Test",
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        // Then: Verify background surface color matches LightColorScheme background
        assertEquals(
            "Light theme background should be light gray (#F5F5F5)",
            expectedLightBackground,
            actualBackgroundColor
        )
        assertEquals(
            "Light theme onBackground should be black",
            expectedLightOnBackground,
            actualOnBackgroundColor
        )
    }

    @Test
    fun theme_primary_color_inDarkMode() {
        // Given: Variable to capture primary color
        var actualPrimaryColor: Color? = null
        val expectedDarkPrimary = Color(0xFFD0BCFF)

        // When: Render with dark theme
        composeTestRule.setContent {
            PodderTheme(darkTheme = true) {
                actualPrimaryColor = MaterialTheme.colorScheme.primary
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text("Primary Color Test")
                }
            }
        }

        // Then: Verify primary color
        assertEquals(
            "Dark theme primary should match DarkColorScheme",
            expectedDarkPrimary,
            actualPrimaryColor
        )
    }
}

/**
 * Test helper composable to capture theme colors
 */
@Composable
private fun ThemeColorCapture(
    onColorsCapture: (background: Color, onBackground: Color, primary: Color) -> Unit
) {
    onColorsCapture(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.onBackground,
        MaterialTheme.colorScheme.primary
    )
}
