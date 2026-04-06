# On-device MiniLM assets

Semantic search uses the on-device quantized ONNX model:
- app/src/main/assets/semantic/all-MiniLM-L6-v2-int8.onnx

Current files:
- `all-MiniLM-L6-v2-int8.onnx` (downloaded from `Xenova/all-MiniLM-L6-v2`, `onnx/model_quantized.onnx`)
- `vocab.txt` (downloaded from `sentence-transformers/all-MiniLM-L6-v2`)
- `tokenizer_config.json` and `special_tokens_map.json`

The app tokenizer uses BERT-style lowercasing + WordPiece against `vocab.txt`.
