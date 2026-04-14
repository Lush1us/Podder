package dev.podder.domain.player

actual class PodderPlayerFactory {
    actual fun create(): PodderPlayer = IosStubPodderPlayer()
}
