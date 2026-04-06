package com.example.wecookproject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Hybrid keyword + semantic-like scorer for event search.
 */
public final class EventSearchMatcher {
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
            "in", "into", "is", "it", "of", "on", "or", "the", "to", "with"
    ));
    private static final Map<String, Set<String>> SYNONYM_GROUPS = buildSynonymGroups();

    private EventSearchMatcher() {
    }

    /**
     * Scores an event for the given query.
     *
     * @param rawQuery entrant query
     * @param eventRecord event candidate
     * @return relevance score (higher is better)
     */
    public static double score(String rawQuery, UserEventRecord eventRecord) {
        String normalizedQuery = normalize(rawQuery);
        if (normalizedQuery.isEmpty()) {
            return 0;
        }

        String normalizedText = normalize(buildEventText(eventRecord));
        if (normalizedText.isEmpty()) {
            return 0;
        }

        Set<String> queryTokens = tokenizeToSet(normalizedQuery);
        Set<String> eventTokens = tokenizeToSet(normalizedText);
        if (queryTokens.isEmpty() || eventTokens.isEmpty()) {
            return 0;
        }

        int exactOverlap = overlapCount(queryTokens, eventTokens);
        int synonymOverlap = overlapCount(expandWithSynonyms(queryTokens), eventTokens) - exactOverlap;
        double jaccard = jaccard(queryTokens, eventTokens);
        double trigramCosine = trigramCosineSimilarity(normalizedQuery, normalizedText);
        double phraseBonus = normalizedText.contains(normalizedQuery) ? 1.0 : 0.0;

        return (exactOverlap * 3.0)
                + (Math.max(0, synonymOverlap) * 1.5)
                + (jaccard * 3.0)
                + (trigramCosine * 2.0)
                + (phraseBonus * 2.0);
    }

    private static String buildEventText(UserEventRecord eventRecord) {
        String name = valueOrEmpty(eventRecord.getEventName());
        String description = valueOrEmpty(eventRecord.getDescription());
        String location = valueOrEmpty(eventRecord.getLocation());
        return name + " " + description + " " + location;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String lower = input.toLowerCase(Locale.ROOT);
        String replaced = lower.replaceAll("[^a-z0-9\\s]", " ");
        return replaced.replaceAll("\\s+", " ").trim();
    }

    static Set<String> tokenizeToSet(String normalizedText) {
        if (normalizedText == null || normalizedText.trim().isEmpty()) {
            return Collections.emptySet();
        }
        String[] tokens = normalizedText.split("\\s+");
        Set<String> result = new HashSet<>();
        for (String token : tokens) {
            if (token.length() < 2 || STOP_WORDS.contains(token)) {
                continue;
            }
            result.add(token);
        }
        return result;
    }

    private static int overlapCount(Set<String> a, Set<String> b) {
        int count = 0;
        for (String token : a) {
            if (b.contains(token)) {
                count++;
            }
        }
        return count;
    }

    private static double jaccard(Set<String> left, Set<String> right) {
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        if (union.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        return (double) intersection.size() / (double) union.size();
    }

    private static Set<String> expandWithSynonyms(Set<String> tokens) {
        Set<String> expanded = new HashSet<>(tokens);
        for (String token : tokens) {
            Set<String> group = SYNONYM_GROUPS.get(token);
            if (group != null) {
                expanded.addAll(group);
            }
        }
        return expanded;
    }

    private static Map<String, Set<String>> buildSynonymGroups() {
        Map<String, Set<String>> groups = new HashMap<>();
        addGroup(groups, Arrays.asList("soccer", "football", "futbol"));
        addGroup(groups, Arrays.asList("bbq", "barbecue", "grill", "grilling"));
        addGroup(groups, Arrays.asList("fitness", "workout", "exercise", "training", "gym"));
        addGroup(groups, Arrays.asList("hike", "hiking", "trail", "trek"));
        addGroup(groups, Arrays.asList("run", "running", "jog", "jogging"));
        addGroup(groups, Arrays.asList("cook", "cooking", "culinary", "kitchen", "baking"));
        addGroup(groups, Arrays.asList("music", "concert", "live", "band", "jam"));
        addGroup(groups, Arrays.asList("art", "painting", "drawing", "craft"));
        addGroup(groups, Arrays.asList("yoga", "mindfulness", "meditation", "wellness"));
        return groups;
    }

    private static void addGroup(Map<String, Set<String>> groups, List<String> words) {
        Set<String> allWords = new HashSet<>(words);
        for (String word : words) {
            groups.put(word, allWords);
        }
    }

    private static double trigramCosineSimilarity(String left, String right) {
        Map<String, Integer> leftVector = ngramVector(left, 3);
        Map<String, Integer> rightVector = ngramVector(right, 3);
        if (leftVector.isEmpty() || rightVector.isEmpty()) {
            return 0;
        }

        double dot = 0;
        for (Map.Entry<String, Integer> entry : leftVector.entrySet()) {
            Integer other = rightVector.get(entry.getKey());
            if (other != null) {
                dot += entry.getValue() * other;
            }
        }
        double leftNorm = vectorNorm(leftVector);
        double rightNorm = vectorNorm(rightVector);
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (leftNorm * rightNorm);
    }

    private static Map<String, Integer> ngramVector(String text, int n) {
        String padded = " " + text + " ";
        if (padded.length() < n) {
            return Collections.emptyMap();
        }
        Map<String, Integer> vector = new HashMap<>();
        for (int i = 0; i <= padded.length() - n; i++) {
            String gram = padded.substring(i, i + n);
            vector.put(gram, vector.getOrDefault(gram, 0) + 1);
        }
        return vector;
    }

    private static double vectorNorm(Map<String, Integer> vector) {
        double sum = 0;
        for (Integer value : vector.values()) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }
}
