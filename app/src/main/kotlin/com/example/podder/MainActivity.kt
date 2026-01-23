package com.example.podder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.podder.ui.theme.PodderTheme
import com.example.podder.ui.screens.PodcastViewModel
import com.example.podder.data.PodcastRepository
import com.example.podder.domain.PodcastUseCase
import com.example.podder.domain.PodcastUseCaseImpl
import com.example.podder.ui.AppNavigation
import com.example.podder.core.PodcastAction

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val podcastRepository = PodcastRepository()
        val podcastUseCase: PodcastUseCase = PodcastUseCaseImpl(podcastRepository)
        val podcastViewModel = PodcastViewModel(podcastUseCase)

        val podcastUrls = listOf(
            "https://podcastfeeds.nbcnews.com/allin",
            "https://audioboom.com/channels/5149464.rss",
            "https://feeds.simplecast.com/dxZsm5kX",
            "https://www.marketplace.org/feed/podcast/marketplace",
            "https://feeds.megaphone.fm/VMP5489734702",
            "https://audioboom.com/channels/5114286.rss",
            "https://feeds.megaphone.fm/pivot",
            "https://feeds.npr.org/510318/podcast.xml",
            "https://podcastfeeds.nbcnews.com/rachelmaddow",
            "https://feeds.simplecast.com/l2i9YnTd",
            "https://www.omnycontent.com/d/playlist/e73c998e-6e60-432f-8610-ae210140c5b1/8a94442e-5a74-4fa2-8b8d-ae27003a8d6b/982f5071-765c-403d-969d-ae27003a8d83/podcast.rss",
            "https://www.omnycontent.com/d/playlist/d83f52e4-2455-47f4-982e-ab790120b954/5155908f-a515-4bbf-aa13-ab8600cec954/ae5224b3-9ebb-45c2-bb5f-ab8600cec959/podcast.rss",
            "https.feeds.npr.org/510289/podcast.xml",
            "https://cafe.com/feed/f-1dKXSHr159",
            "https://feeds.megaphone.fm/VMP1684715893",
            "https://feeds.npr.org/510355/podcast.xml",
            "https://feeds.simplecast.com/AeLYwXNc",
            "https://feeds.megaphone.fm/recodedecode",
            "https://feeds.simplecast.com/Y8lFbOT4",
            "https://feeds.simplecast.com/fQ3mywpV",
            "http://feeds.wnyc.org/onthemedia",
            "https://feeds.npr.org/510325/podcast.xml",
            "https://podcastfeeds.nbcnews.com/with",
            "https://rss.art19.com/sean-carrolls-mindscape",
            "https://audioboom.com/channels/5124335.rss",
            "https://media.rss.com/csquared/feed.xml",
            "https://api.substack.com/feed/podcast/906465.rss",
            "https://feeds.npr.org/510333/podcast.xml",
            "https://feeds.megaphone.fm/BLU4811105299",
            "https://feeds.npr.org/344098539/podcast.xml",
            "https://feeds.megaphone.fm/BVLLC2163264914",
            "https://feeds.megaphone.fm/VMP7937548048",
            "https://feeds.megaphone.fm/STU4418364045",
            "https://feeds.npr.org/510310/podcast.xml",
            "http://feeds.wnyc.org/radiolab",
            "https://feeds.megaphone.fm/watergate",
            "https://feeds.megaphone.fm/LBE1877691396",
            "https://podcastfeeds.nbcnews.com/rachel-maddow-presents-ultra",
            "https://feeds.buzzsprout.com/2546567.rss",
            "https://feeds.captivate.fm/theemilyshow/",
            "https://feeds.megaphone.fm/landofthegiants",
            "https://feeds.npr.org/510313/podcast.xml",
            "https://feeds.captivate.fm/wongnotes/",
            "https://feeds.megaphone.fm/recodemedia",
            "https://feeds.npr.org/510311/podcast.xml",
            "https://rss.art19.com/smartless",
            "https://www.omnycontent.com/d/playlist/796469f9-ea34-46a2-8776-ad0f015d6beb/89cff1ac-62ad-4005-b66f-b1540162fcb4/f26c966a-c6b5-49d4-91cd-b154016527e5/podcast.rss",
            "https://api.substack.com/feed/podcast/1190614.rss",
            "https://api.substack.com/feed/podcast/2325511/s/121146.rss",
            "https://rss.art19.com/flipping-the-bird-elon-vs-twitter",
            "http://feeds.wnyc.org/TheAnthropoceneReviewed",
            "https://feeds.megaphone.fm/replyall",
            "https://www.omnycontent.com/d/playlist/e73c998e-6e60-432f-8610-ae210140c5b1/416fbd2e-f519-4bf3-af87-ae320005bc15/cb306f79-8f13-4de6-9c32-ae320005bc35/podcast.rss"
        )

        podcastViewModel.process(PodcastAction.FetchPodcasts(urls = podcastUrls, source = "MainActivity", timestamp = System.currentTimeMillis()))

        setContent {
            PodderTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel = podcastViewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // The PodcastPlayerService is no longer directly controlled by MainActivity
        // Its lifecycle should be managed by Media3 itself or by the player logic within the ViewModel.
        // Removing the explicit stopService call.
    }
}
