# Zynee AI Microservice

This service now supports Gemini (default) and Ollama (optional fallback).

## 1) Set Gemini API key (required for default provider)

```bash
export GEMINI_API_KEY="your_gemini_api_key"
export GEMINI_MODEL="gemini-2.5-flash-lite"
```

## 2) Create Python env and install packages

```bash
cd /Users/angelajustin/Desktop/netset/zynee/zynee/assistant-microservice
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## 3) Run the microservice

```bash
uvicorn app:app --host 127.0.0.1 --port 8001 --reload
```

## 4) Run Zynee app

In another terminal:

```bash
cd /Users/angelajustin/Desktop/netset/zynee/zynee
./mvnw spring-boot:run
```

The Java app calls:

- `http://127.0.0.1:8001/chat` (Python service)
- Python service calls Gemini API by default

Optional environment variables for Python service:

- `LLM_PROVIDER` (default `gemini`, optional: `ollama`)
- `GEMINI_API_KEY` (required when provider is `gemini`)
- `GEMINI_MODEL` (default `gemini-2.5-flash-lite`)
- `GEMINI_API_BASE` (default `https://generativelanguage.googleapis.com/v1beta`)
- `LLM_TIMEOUT_SECONDS` (default `90`)
- `LLM_TEMPERATURE` (default `0.5`)
- `LLM_NUM_PREDICT` (default `260`, lower is faster)
- `LLM_LONG_NUM_PREDICT` (default `520`)

Optional Ollama fallback variables:

- `OLLAMA_URL` (default `http://127.0.0.1:11434/api/chat`)
- `OLLAMA_MODEL` (default `llama3.2`)
- `OLLAMA_KEEP_ALIVE` (default `30m`)
- `OLLAMA_REPEAT_PENALTY` (default `1.15`)

## 5) Train suicide-risk model from your datasets

The assistant supports a hybrid safety flow:
- ML classifier (trained from your CSV data) for suicide-risk screening
- LLM chat (Gemini by default) for non-crisis messages

Use this command to train and export the model bundle:

```bash
cd /Users/angelajustin/Desktop/netset/zynee/zynee/assistant-microservice
source .venv/bin/activate
python train_suicide_model.py \
  --suicide-data /Users/angelajustin/Desktop/netset/zynee/zynee/assistant-microservice/data/raw/Suicide_Detection.csv \
  --emotion-data /Users/angelajustin/Desktop/netset/zynee/zynee/assistant-microservice/data/raw/Emotion_classify_Data.csv \
  --sentiment-data /Users/angelajustin/Desktop/netset/zynee/zynee/assistant-microservice/data/raw/sentiment_analysis.csv
```

Outputs:
- `models/suicide_risk_model.joblib` (runtime model)
- `models/suicide_risk_metrics.json` (metrics + thresholds)
- `data/processed/suicide_train_split.csv` (training split)
- `data/processed/suicide_test_split.csv` (testing split)

## 6) Run assistant with model

The chat endpoint auto-loads:

`assistant-microservice/models/suicide_risk_model.joblib`

Optional overrides:
- `SUICIDE_MODEL_PATH`
- `SUICIDE_RISK_MEDIUM_THRESHOLD`
- `SUICIDE_RISK_HIGH_THRESHOLD`

Health check now returns model status:

```bash
curl http://127.0.0.1:8001/health
```

## 7) Train wellness models + generate analytics datasets

This creates:
- journal emotion model
- journal sentiment model
- train/test CSVs for forecasting, pattern analysis, weekly wellness, stats-box metrics, and cosmic insights

```bash
cd /Users/angelajustin/Desktop/netset/zynee/zynee/assistant-microservice
source .venv/bin/activate
python train_wellness_models.py \
  --emotion-data /Users/angelajustin/Desktop/netset/zynee/zynee/assistant-microservice/data/raw/Emotion_classify_Data.csv \
  --sentiment-data /Users/angelajustin/Desktop/netset/zynee/zynee/assistant-microservice/data/raw/sentiment_analysis.csv
```

Outputs:
- `models/journal_emotion_model.joblib`
- `models/journal_sentiment_model.joblib`
- `models/wellness_models_metrics.json`
- `data/training/*.csv`
- `data/testing/*.csv`

## 8) Insights endpoint used by Zynee

The Java app calls:

- `http://127.0.0.1:8001/insights-analyze`

This endpoint blends:
- Mood logs
- Journal entries
- Quick check-ins
- Sun-sign traits (+ LLM wording fallback)
