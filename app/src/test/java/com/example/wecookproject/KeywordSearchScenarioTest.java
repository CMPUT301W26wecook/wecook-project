package com.example.wecookproject;

import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KeywordSearchScenarioTest {
    private static final double KEYWORD_SCORE_THRESHOLD = 0.45d;
    private static final int SEMANTIC_TOP_N = 40;
    private static final double LEXICAL_WEIGHT = 0.55d;
    private static final double SEMANTIC_WEIGHT = 0.45d;

    private List<UserEventRecord> events;
    private OnDeviceSemanticSearchEngine semanticEngine;

    @Before
    public void setUp() {
        events = buildFifteenEvents();
        semanticEngine = new OnDeviceSemanticSearchEngine(new IntentAwareFakeEmbedder());
    }

    @Test
    public void keyword_bbq_returnsBarbecueRelatedEvents() {
        List<String> results = searchEventIds("bbq");
        assertAppearsInResults(results, "event_bbq", "event_picnic");
    }

    @Test
    public void keyword_hiking_returnsTrailAndNatureEvents() {
        List<String> results = searchEventIds("hiking trail");
        assertAppearsInResults(results, "event_hike", "event_nature");
    }

    @Test
    public void keyword_yoga_returnsWellnessEvents() {
        List<String> results = searchEventIds("yoga mindfulness");
        assertAppearsInResults(results, "event_yoga", "event_wellness");
    }

    @Test
    public void keyword_liveMusic_returnsConcertRelatedEvents() {
        List<String> results = searchEventIds("live music");
        assertAppearsInResults(results, "event_music", "event_jazz");
    }

    @Test
    public void keyword_cooking_returnsCulinaryEvents() {
        List<String> results = searchEventIds("cooking class");
        assertAppearsInResults(results, "event_cooking", "event_baking");
    }

    private List<String> searchEventIds(String query) {
        List<ScoredRecord> lexical = new ArrayList<>();
        for (UserEventRecord event : events) {
            double score = EventSearchMatcher.score(query, event);
            if (score >= KEYWORD_SCORE_THRESHOLD) {
                lexical.add(new ScoredRecord(event, score));
            }
        }
        lexical.sort((a, b) -> Double.compare(b.score, a.score));
        if (lexical.isEmpty()) {
            return new ArrayList<>();
        }

        List<ScoredRecord> semanticWindow = SemanticRankingUtils.topN(lexical, SEMANTIC_TOP_N);
        List<OnDeviceSemanticSearchEngine.EventCandidate> candidates = new ArrayList<>();
        List<SemanticRankingUtils.ScoredId> lexicalWindow = new ArrayList<>();
        Map<String, UserEventRecord> byId = new HashMap<>();
        for (ScoredRecord scored : semanticWindow) {
            String id = scored.event.getEventId();
            String text = EventSearchMatcher.buildSearchDocument(scored.event);
            candidates.add(new OnDeviceSemanticSearchEngine.EventCandidate(id, text));
            lexicalWindow.add(new SemanticRankingUtils.ScoredId(id, scored.score));
            byId.put(id, scored.event);
        }

        Map<String, Double> semanticScores = semanticEngine.score(query, candidates);
        List<SemanticRankingUtils.ScoredId> blended = SemanticRankingUtils.blendTopWindow(
                lexicalWindow,
                semanticScores,
                LEXICAL_WEIGHT,
                SEMANTIC_WEIGHT
        );

        List<String> result = new ArrayList<>();
        for (SemanticRankingUtils.ScoredId item : blended) {
            if (byId.containsKey(item.id)) {
                result.add(item.id);
            }
        }
        for (int i = semanticWindow.size(); i < lexical.size(); i++) {
            result.add(lexical.get(i).event.getEventId());
        }
        return result;
    }

    private void assertAppearsInResults(List<String> resultIds, String expectedFirst, String expectedSecond) {
        int checkWindow = Math.min(10, resultIds.size());
        List<String> window = resultIds.subList(0, checkWindow);
        assertTrue("Expected results to contain " + expectedFirst + " but was " + window, window.contains(expectedFirst));
        assertTrue("Expected results to contain " + expectedSecond + " but was " + window, window.contains(expectedSecond));
    }

    private List<UserEventRecord> buildFifteenEvents() {
        Map<String, String[]> data = new LinkedHashMap<>();
        data.put("event_bbq", new String[]{"Backyard Barbecue Night", "Community barbecue and grill social with smokehouse favorites."});
        data.put("event_picnic", new String[]{"Summer Picnic Barbecue Gathering", "Outdoor picnic with bbq barbecue grill food, family games, and park meetups."});
        data.put("event_hike", new String[]{"River Valley Hiking Club", "Guided hiking routes, trail safety, and beginner trekking tips."});
        data.put("event_nature", new String[]{"Nature Hiking Walk Adventure", "Scenic hiking trails and outdoor exploration for mountain and forest lovers."});
        data.put("event_yoga", new String[]{"Sunrise Yoga Session", "Morning yoga flow, breathing exercises, and mindfulness practice."});
        data.put("event_wellness", new String[]{"Wellness Reset Workshop", "Meditation, stress relief, and mindful movement for wellness."});
        data.put("event_music", new String[]{"Live Music Concert Night", "Local bands perform acoustic and indie music downtown."});
        data.put("event_jazz", new String[]{"Jazz Jam Evening", "Live jazz ensemble with improvisation and lounge atmosphere."});
        data.put("event_cooking", new String[]{"Italian Cooking Class", "Hands-on culinary workshop for pasta sauces and kitchen basics."});
        data.put("event_baking", new String[]{"Baking Fundamentals Lab", "Learn dessert baking, dough shaping, and oven techniques."});
        data.put("event_soccer", new String[]{"Sunday Soccer League", "Community football drills, small games, and team scrimmages."});
        data.put("event_running", new String[]{"City Running Meetup", "Group jogging and endurance training around downtown routes."});
        data.put("event_art", new String[]{"Urban Sketch Art Club", "Drawing and painting session for beginners and hobby artists."});
        data.put("event_book", new String[]{"Neighborhood Book Circle", "Fiction discussion and reading circle with weekly themes."});
        data.put("event_tech", new String[]{"Android Dev Study Group", "Mobile app architecture and coding practice for developers."});

        List<UserEventRecord> records = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : data.entrySet()) {
            records.add(new UserEventRecord(
                    entry.getKey(),
                    entry.getValue()[0],
                    "Edmonton",
                    "org-" + entry.getKey(),
                    entry.getValue()[1],
                    null,
                    120,
                    120,
                    "entrant-test",
                    null,
                    null,
                    null,
                    false,
                    Arrays.asList(),
                    ""
            ));
        }
        return records;
    }

    private static final class ScoredRecord {
        private final UserEventRecord event;
        private final double score;

        private ScoredRecord(UserEventRecord event, double score) {
            this.event = event;
            this.score = score;
        }
    }

    private static final class IntentAwareFakeEmbedder implements OnDeviceSemanticSearchEngine.Embedder {
        @Override
        public boolean warmup() {
            return true;
        }

        @Override
        public float[] embed(String text) {
            String normalized = EventSearchMatcher.normalize(text);
            float[] vector = new float[8];
            vector[0] = hasAny(normalized, "bbq", "barbecue", "grill", "picnic");
            vector[1] = hasAny(normalized, "hike", "hiking", "trail", "trek", "nature");
            vector[2] = hasAny(normalized, "yoga", "mindfulness", "meditation", "wellness");
            vector[3] = hasAny(normalized, "music", "concert", "jazz", "band", "live");
            vector[4] = hasAny(normalized, "cook", "cooking", "culinary", "kitchen", "baking");
            vector[5] = hasAny(normalized, "soccer", "football", "running", "jogging", "training");
            vector[6] = hasAny(normalized, "art", "drawing", "painting");
            vector[7] = hasAny(normalized, "book", "reading", "fiction");
            return vector;
        }

        @Override
        public void close() {
            // no-op
        }

        private float hasAny(String text, String... tokens) {
            for (String token : tokens) {
                if (text.contains(token)) {
                    return 1f;
                }
            }
            return 0f;
        }
    }
}
