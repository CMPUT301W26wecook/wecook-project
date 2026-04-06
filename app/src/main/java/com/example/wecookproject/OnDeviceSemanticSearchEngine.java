package com.example.wecookproject;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/**
 * On-device semantic scorer for event search based on MiniLM embeddings.
 */
public class OnDeviceSemanticSearchEngine {
    private static final String TAG = "OnDeviceSemanticSearch";
    private static final String MODEL_ID = "sentence-transformers/all-MiniLM-L6-v2";
    private static final String MODEL_ASSET_PATH = "semantic/all-MiniLM-L6-v2-int8.onnx";
    private static final String VOCAB_ASSET_PATH = "semantic/vocab.txt";
    private static final int MAX_SEQUENCE_LENGTH = 128;
    private static final int MAX_EMBEDDING_CACHE = 256;

    private final Embedder embedder;
    private final Map<String, float[]> embeddingCache = new LinkedHashMap<String, float[]>(MAX_EMBEDDING_CACHE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
            return size() > MAX_EMBEDDING_CACHE;
        }
    };
    private final Map<String, String> normalizedTextCache = new HashMap<>();
    private String cachedQueryKey = "";
    private float[] cachedQueryEmbedding;

    /**
     * Candidate item for semantic scoring.
     */
    public static class EventCandidate {
        private final String eventId;
        private final String text;

        public EventCandidate(String eventId, String text) {
            this.eventId = eventId == null ? "" : eventId;
            this.text = text == null ? "" : text;
        }

        public String getEventId() {
            return eventId;
        }

        public String getText() {
            return text;
        }
    }

    interface Embedder {
        boolean warmup();
        float[] embed(String text);
        void close();
    }

    public OnDeviceSemanticSearchEngine(Context context) {
        this(new OnnxMiniLmEmbedder(context.getApplicationContext()));
    }

    OnDeviceSemanticSearchEngine(Embedder embedder) {
        this.embedder = embedder;
    }

    public String getModelIdentifier() {
        return MODEL_ID;
    }

    public boolean warmup() {
        return embedder.warmup();
    }

    public void close() {
        embedder.close();
    }

    /**
     * Scores candidates by cosine similarity to query embedding.
     */
    public Map<String, Double> score(String query, List<EventCandidate> candidates) {
        if (query == null || query.trim().isEmpty() || candidates == null || candidates.isEmpty()) {
            return Collections.emptyMap();
        }

        float[] queryEmbedding = queryEmbedding(query);
        if (queryEmbedding == null) {
            return Collections.emptyMap();
        }

        Map<String, Double> results = new HashMap<>();
        for (EventCandidate candidate : candidates) {
            if (candidate.getEventId().trim().isEmpty()) {
                continue;
            }
            String normalizedText = normalizedText(candidate);
            if (normalizedText.isEmpty()) {
                continue;
            }
            String cacheKey = cacheKey(candidate.getEventId(), normalizedText);
            float[] candidateEmbedding = embeddingCache.get(cacheKey);
            if (candidateEmbedding == null) {
                candidateEmbedding = embedder.embed(normalizedText);
                if (candidateEmbedding != null) {
                    embeddingCache.put(cacheKey, candidateEmbedding);
                }
            }
            if (candidateEmbedding == null) {
                continue;
            }
            double similarity = cosine(queryEmbedding, candidateEmbedding);
            results.put(candidate.getEventId(), similarity);
        }
        return results;
    }

    private float[] queryEmbedding(String query) {
        String normalized = EventSearchMatcher.normalize(query);
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.equals(cachedQueryKey) && cachedQueryEmbedding != null) {
            return cachedQueryEmbedding;
        }
        cachedQueryEmbedding = embedder.embed(normalized);
        cachedQueryKey = normalized;
        return cachedQueryEmbedding;
    }

    private String normalizedText(EventCandidate candidate) {
        String existing = normalizedTextCache.get(candidate.getEventId());
        String normalized = EventSearchMatcher.normalize(candidate.getText());
        if (existing == null || !existing.equals(normalized)) {
            normalizedTextCache.put(candidate.getEventId(), normalized);
        }
        return normalized;
    }

    private String cacheKey(String eventId, String normalizedText) {
        return eventId + ":" + sha256(normalizedText);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format(Locale.US, "%02x", item));
            }
            return builder.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private double cosine(float[] left, float[] right) {
        int size = Math.min(left.length, right.length);
        if (size == 0) {
            return 0d;
        }
        double dot = 0d;
        double leftNorm = 0d;
        double rightNorm = 0d;
        for (int i = 0; i < size; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0d;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static final class OnnxMiniLmEmbedder implements Embedder {
        private final Context appContext;
        private final Object lock = new Object();
        private OrtEnvironment environment;
        private OrtSession session;
        private MiniLmTokenizer tokenizer;
        private boolean failed;

        private OnnxMiniLmEmbedder(Context appContext) {
            this.appContext = appContext;
        }

        @Override
        public boolean warmup() {
            return ensureInitialized();
        }

        @Override
        public float[] embed(String text) {
            if (!ensureInitialized()) {
                return null;
            }
            MiniLmTokenizer.Tokenized tokenized = tokenizer.tokenize(text, MAX_SEQUENCE_LENGTH);
            long[][] inputIds = new long[][]{tokenized.inputIds};
            long[][] attentionMask = new long[][]{tokenized.attentionMask};
            long[][] tokenTypeIds = new long[][]{tokenized.tokenTypeIds};
            OnnxTensor inputTensor = null;
            OnnxTensor maskTensor = null;
            OnnxTensor tokenTypeTensor = null;
            OrtSession.Result result = null;
            try {
                inputTensor = OnnxTensor.createTensor(environment, LongBuffer.wrap(inputIds[0]), new long[]{1, inputIds[0].length});
                maskTensor = OnnxTensor.createTensor(environment, LongBuffer.wrap(attentionMask[0]), new long[]{1, attentionMask[0].length});
                tokenTypeTensor = OnnxTensor.createTensor(environment, LongBuffer.wrap(tokenTypeIds[0]), new long[]{1, tokenTypeIds[0].length});
                Map<String, OnnxTensor> inputs = new HashMap<>();
                Set<String> inputNames = session.getInputNames();
                if (inputNames.contains("input_ids")) {
                    inputs.put("input_ids", inputTensor);
                }
                if (inputNames.contains("attention_mask")) {
                    inputs.put("attention_mask", maskTensor);
                }
                if (inputNames.contains("token_type_ids")) {
                    inputs.put("token_type_ids", tokenTypeTensor);
                }
                result = session.run(inputs);
                return firstEmbedding(result, tokenized.attentionMask);
            } catch (Exception exception) {
                Log.w(TAG, "Embedding inference failed", exception);
                return null;
            } finally {
                safeClose(result);
                safeClose(inputTensor);
                safeClose(maskTensor);
                safeClose(tokenTypeTensor);
            }
        }

        @Override
        public void close() {
            synchronized (lock) {
                safeClose(session);
                session = null;
                environment = null;
                tokenizer = null;
            }
        }

        private boolean ensureInitialized() {
            synchronized (lock) {
                if (failed) {
                    return false;
                }
                if (session != null && tokenizer != null && environment != null) {
                    return true;
                }
                try {
                    environment = OrtEnvironment.getEnvironment();
                    String modelPath = copyAssetToCache(MODEL_ASSET_PATH, "all-MiniLM-L6-v2-int8.onnx");
                    session = environment.createSession(modelPath, new OrtSession.SessionOptions());
                    tokenizer = MiniLmTokenizer.fromAsset(appContext, VOCAB_ASSET_PATH);
                    return true;
                } catch (Exception exception) {
                    failed = true;
                    Log.w(TAG, "Failed to initialize on-device semantic engine", exception);
                    safeClose(session);
                    session = null;
                    tokenizer = null;
                    return false;
                }
            }
        }

        private String copyAssetToCache(String assetPath, String fileName) throws IOException {
            File outFile = new File(appContext.getCacheDir(), fileName);
            if (outFile.exists() && outFile.length() > 0) {
                return outFile.getAbsolutePath();
            }
            try (InputStream input = appContext.getAssets().open(assetPath);
                 FileOutputStream output = new FileOutputStream(outFile, false)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            }
            return outFile.getAbsolutePath();
        }

        private float[] firstEmbedding(OrtSession.Result result, long[] attentionMask) throws OrtException {
            if (result == null || result.size() == 0) {
                return null;
            }
            Object value = null;
            if (result.get("sentence_embedding").isPresent()) {
                value = result.get("sentence_embedding").get().getValue();
            } else {
                value = result.get(0).getValue();
            }
            if (value instanceof float[][]) {
                float[][] matrix = (float[][]) value;
                return matrix.length > 0 ? matrix[0] : null;
            }
            if (value instanceof float[][][]) {
                float[][][] tensor = (float[][][]) value;
                if (tensor.length == 0 || tensor[0].length == 0) {
                    return null;
                }
                int hidden = tensor[0][0].length;
                float[] pooled = new float[hidden];
                double tokenCount = 0d;
                int sequenceLength = tensor[0].length;
                for (int i = 0; i < sequenceLength; i++) {
                    boolean include = attentionMask != null
                            && i < attentionMask.length
                            && attentionMask[i] == 1L;
                    if (!include) {
                        continue;
                    }
                    tokenCount += 1d;
                    for (int j = 0; j < hidden; j++) {
                        pooled[j] += tensor[0][i][j];
                    }
                }
                if (tokenCount <= 0d) {
                    return null;
                }
                for (int j = 0; j < hidden; j++) {
                    pooled[j] /= (float) tokenCount;
                }
                return pooled;
            }
            return null;
        }

        private void safeClose(AutoCloseable closeable) {
            if (closeable == null) {
                return;
            }
            try {
                closeable.close();
            } catch (Exception ignored) {
                // no-op
            }
        }
    }

    private static final class MiniLmTokenizer {
        private static final String CLS_TOKEN = "[CLS]";
        private static final String SEP_TOKEN = "[SEP]";
        private static final String UNK_TOKEN = "[UNK]";
        private static final String PAD_TOKEN = "[PAD]";

        private final Map<String, Integer> vocab;
        private final int clsId;
        private final int sepId;
        private final int unkId;
        private final int padId;

        private MiniLmTokenizer(Map<String, Integer> vocab) {
            this.vocab = vocab;
            clsId = vocab.getOrDefault(CLS_TOKEN, 101);
            sepId = vocab.getOrDefault(SEP_TOKEN, 102);
            unkId = vocab.getOrDefault(UNK_TOKEN, 100);
            padId = vocab.getOrDefault(PAD_TOKEN, 0);
        }

        private static MiniLmTokenizer fromAsset(Context context, String assetPath) throws IOException {
            Map<String, Integer> vocab = new HashMap<>();
            try (InputStream inputStream = context.getAssets().open(assetPath);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                int index = 0;
                while ((line = reader.readLine()) != null) {
                    String token = line.trim();
                    if (!token.isEmpty()) {
                        vocab.put(token, index);
                    }
                    index++;
                }
            }
            return new MiniLmTokenizer(vocab);
        }

        private Tokenized tokenize(String text, int maxLength) {
            List<String> words = basicTokenize(text);
            List<Integer> tokenIds = new ArrayList<>();
            tokenIds.add(clsId);
            for (String word : words) {
                List<Integer> wordPieces = wordPieceTokenize(word);
                for (Integer piece : wordPieces) {
                    if (tokenIds.size() >= maxLength - 1) {
                        break;
                    }
                    tokenIds.add(piece);
                }
                if (tokenIds.size() >= maxLength - 1) {
                    break;
                }
            }
            tokenIds.add(sepId);

            long[] inputIds = new long[maxLength];
            long[] attentionMask = new long[maxLength];
            long[] tokenTypeIds = new long[maxLength];
            for (int i = 0; i < maxLength; i++) {
                if (i < tokenIds.size()) {
                    inputIds[i] = tokenIds.get(i);
                    attentionMask[i] = 1L;
                    tokenTypeIds[i] = 0L;
                } else {
                    inputIds[i] = padId;
                    attentionMask[i] = 0L;
                    tokenTypeIds[i] = 0L;
                }
            }
            return new Tokenized(inputIds, attentionMask, tokenTypeIds);
        }

        private List<String> basicTokenize(String text) {
            if (text == null) {
                return Collections.emptyList();
            }
            String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT);
            normalized = normalized.replaceAll("[^a-z0-9\\s]", " ");
            normalized = normalized.replaceAll("\\s+", " ").trim();
            if (normalized.isEmpty()) {
                return Collections.emptyList();
            }
            String[] tokens = normalized.split("\\s+");
            List<String> result = new ArrayList<>();
            for (String token : tokens) {
                if (!token.isEmpty()) {
                    result.add(token);
                }
            }
            return result;
        }

        private List<Integer> wordPieceTokenize(String token) {
            if (token == null || token.isEmpty()) {
                return Collections.emptyList();
            }
            if (vocab.containsKey(token)) {
                return Collections.singletonList(vocab.get(token));
            }
            List<Integer> pieces = new ArrayList<>();
            int start = 0;
            while (start < token.length()) {
                int end = token.length();
                Integer found = null;
                while (start < end) {
                    String part = token.substring(start, end);
                    if (start > 0) {
                        part = "##" + part;
                    }
                    Integer id = vocab.get(part);
                    if (id != null) {
                        found = id;
                        break;
                    }
                    end--;
                }
                if (found == null) {
                    return Collections.singletonList(unkId);
                }
                pieces.add(found);
                start = end;
            }
            return pieces;
        }

        private static final class Tokenized {
            private final long[] inputIds;
            private final long[] attentionMask;
            private final long[] tokenTypeIds;

            private Tokenized(long[] inputIds, long[] attentionMask, long[] tokenTypeIds) {
                this.inputIds = inputIds;
                this.attentionMask = attentionMask;
                this.tokenTypeIds = tokenTypeIds;
            }
        }
    }
}
