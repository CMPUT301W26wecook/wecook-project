package com.example.wecookproject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SemanticRankingUtilsTest {

    @Test
    public void topN_returnsOnlyRequestedPrefix() {
        List<String> source = Arrays.asList("a", "b", "c", "d");
        List<String> top = SemanticRankingUtils.topN(source, 2);
        assertEquals(Arrays.asList("a", "b"), top);
    }

    @Test
    public void blendTopWindow_reordersByCombinedScore() {
        List<SemanticRankingUtils.ScoredId> lexical = Arrays.asList(
                new SemanticRankingUtils.ScoredId("a", 10.0),
                new SemanticRankingUtils.ScoredId("b", 9.0)
        );
        Map<String, Double> semantic = new HashMap<>();
        semantic.put("a", 0.1);
        semantic.put("b", 0.95);

        List<SemanticRankingUtils.ScoredId> blended = SemanticRankingUtils.blendTopWindow(lexical, semantic, 0.55, 0.45);

        assertEquals("b", blended.get(0).id);
        assertTrue(blended.get(0).score >= blended.get(1).score);
    }
}
