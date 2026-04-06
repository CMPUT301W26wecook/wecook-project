package com.example.wecookproject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Utility functions for semantic reranking over lexical candidates.
 */
public final class SemanticRankingUtils {
    private SemanticRankingUtils() {
    }

    /**
     * Returns a top-N copy of lexical candidates preserving lexical order.
     */
    public static <T> List<T> topN(List<T> lexicalCandidates, int topN) {
        if (lexicalCandidates == null || lexicalCandidates.isEmpty() || topN <= 0) {
            return Collections.emptyList();
        }
        return new ArrayList<>(lexicalCandidates.subList(0, Math.min(topN, lexicalCandidates.size())));
    }

    /**
     * Blends lexical+semantic scores and returns reordered top window.
     */
    public static List<ScoredId> blendTopWindow(List<ScoredId> lexicalWindow,
                                                Map<String, Double> semanticScores,
                                                double lexicalWeight,
                                                double semanticWeight) {
        if (lexicalWindow == null || lexicalWindow.isEmpty()) {
            return Collections.emptyList();
        }

        double lexicalMax = 0d;
        for (ScoredId item : lexicalWindow) {
            lexicalMax = Math.max(lexicalMax, item.score);
        }
        double safeLexicalMax = lexicalMax <= 0d ? 1d : lexicalMax;

        List<ScoredId> blended = new ArrayList<>();
        for (ScoredId item : lexicalWindow) {
            double normalizedLexical = item.score / safeLexicalMax;
            double semantic = semanticScores.getOrDefault(item.id, 0d);
            double combined = (lexicalWeight * normalizedLexical) + (semanticWeight * semantic);
            blended.add(new ScoredId(item.id, combined));
        }
        blended.sort(Comparator.comparingDouble((ScoredId x) -> x.score).reversed());
        return blended;
    }

    public static final class ScoredId {
        public final String id;
        public final double score;

        public ScoredId(String id, double score) {
            this.id = id;
            this.score = score;
        }
    }
}
