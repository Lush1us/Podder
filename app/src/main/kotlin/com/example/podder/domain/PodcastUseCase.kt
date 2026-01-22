package com.example.podder.domain

import com.example.podder.parser.Podcast
import com.example.podder.data.PodcastRepository

interface PodcastUseCase {
    suspend fun getPodcast(url: String): Podcast?
}

class PodcastUseCaseImpl(private val repository: PodcastRepository) : PodcastUseCase {
    override suspend fun getPodcast(url: String): Podcast? {
        return repository.getPodcast(url)
    }
}
