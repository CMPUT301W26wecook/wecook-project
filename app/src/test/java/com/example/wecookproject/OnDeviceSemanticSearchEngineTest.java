package com.example.wecookproject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class OnDeviceSemanticSearchEngineTest {

    @Test
    public void score_whenEmbedderUnavailable_returnsEmpty() {
        FakeEmbedder embedder = new FakeEmbedder(false);
        OnDeviceSemanticSearchEngine engine = new OnDeviceSemanticSearchEngine(embedder);
        List<OnDeviceSemanticSearchEngine.EventCandidate> candidates = Arrays.asList(
                new OnDeviceSemanticSearchEngine.EventCandidate("e1", "Cooking workshop downtown")
        );

        Map<String, Double> scores = engine.score("cooking", candidates);

        assertTrue(scores.isEmpty());
    }

    @Test
    public void score_relevantEventRanksHigherThanUnrelated() {
        FakeEmbedder embedder = new FakeEmbedder(true);
        OnDeviceSemanticSearchEngine engine = new OnDeviceSemanticSearchEngine(embedder);
        List<OnDeviceSemanticSearchEngine.EventCandidate> candidates = Arrays.asList(
                new OnDeviceSemanticSearchEngine.EventCandidate("cook", "Cooking workshop for beginners"),
                new OnDeviceSemanticSearchEngine.EventCandidate("astro", "Astronomy meetup with telescopes")
        );

        Map<String, Double> scores = engine.score("cooking class", candidates);

        assertTrue(scores.get("cook") > scores.get("astro"));
    }

    @Test
    public void score_sameQueryReusesCachedQueryEmbedding() {
        FakeEmbedder embedder = new FakeEmbedder(true);
        OnDeviceSemanticSearchEngine engine = new OnDeviceSemanticSearchEngine(embedder);
        List<OnDeviceSemanticSearchEngine.EventCandidate> candidates = Arrays.asList(
                new OnDeviceSemanticSearchEngine.EventCandidate("e1", "Community cooking event")
        );

        engine.score("cooking", candidates);
        int callsAfterFirst = embedder.getCalls();
        engine.score("cooking", candidates);

        assertEquals(callsAfterFirst, embedder.getCalls());
    }

    @Test
    public void score_eventTextChangeInvalidatesEmbeddingCacheEntry() {
        FakeEmbedder embedder = new FakeEmbedder(true);
        OnDeviceSemanticSearchEngine engine = new OnDeviceSemanticSearchEngine(embedder);

        engine.score(
                "fitness",
                Arrays.asList(new OnDeviceSemanticSearchEngine.EventCandidate("e1", "Fitness bootcamp"))
        );
        int callsAfterFirst = embedder.getCalls();

        engine.score(
                "fitness",
                Arrays.asList(new OnDeviceSemanticSearchEngine.EventCandidate("e1", "Fitness and yoga bootcamp"))
        );

        assertTrue(embedder.getCalls() > callsAfterFirst);
    }

    private static final class FakeEmbedder implements OnDeviceSemanticSearchEngine.Embedder {
        private final boolean available;
        private int calls;

        private FakeEmbedder(boolean available) {
            this.available = available;
        }

        @Override
        public boolean warmup() {
            return available;
        }

        @Override
        public float[] embed(String text) {
            if (!available) {
                return null;
            }
            calls++;
            String normalized = EventSearchMatcher.normalize(text);
            float cook = normalized.contains("cook") ? 1f : 0f;
            float fit = normalized.contains("fit") || normalized.contains("yoga") ? 1f : 0f;
            float astro = normalized.contains("astro") || normalized.contains("telescope") ? 1f : 0f;
            return new float[]{cook, fit, astro};
        }

        @Override
        public void close() {
            // no-op
        }

        private int getCalls() {
            return calls;
        }
    }
}
