package dev.podder

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformTest {

    @Test
    fun platformNameIsNotBlank() {
        assertTrue(Platform().name.isNotBlank(), "Platform name must not be blank")
    }
}
