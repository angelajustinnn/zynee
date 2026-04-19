from __future__ import annotations

import argparse
import csv
import json
import random
import re
from collections import Counter
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, f1_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline

SEED = 42


@dataclass(frozen=True)
class EmotionRow:
    text: str
    label: str


@dataclass(frozen=True)
class SentimentRow:
    text: str
    label: str


def parse_args() -> argparse.Namespace:
    base_dir = Path(__file__).resolve().parent
    raw_dir = base_dir / "data" / "raw"
    parser = argparse.ArgumentParser(
        description="Train Zynee wellness NLP models and generate CSV datasets for analytics."
    )
    parser.add_argument(
        "--emotion-data",
        default=str(raw_dir / "Emotion_classify_Data.csv"),
        help="Path to Emotion_classify_Data.csv",
    )
    parser.add_argument(
        "--sentiment-data",
        default=str(raw_dir / "sentiment_analysis.csv"),
        help="Path to sentiment_analysis.csv",
    )
    parser.add_argument(
        "--models-dir",
        default=str(base_dir / "models"),
        help="Directory where model artifacts will be saved.",
    )
    parser.add_argument(
        "--train-dir",
        default=str(base_dir / "data" / "training"),
        help="Directory for generated training CSV datasets.",
    )
    parser.add_argument(
        "--test-dir",
        default=str(base_dir / "data" / "testing"),
        help="Directory for generated testing CSV datasets.",
    )
    return parser.parse_args()


def clean_text(raw: object) -> str:
    value = "" if raw is None else str(raw)
    value = re.sub(r"\s+", " ", value).strip()
    return value


def write_rows(path: Path, header: list[str], rows: Iterable[dict[str, object]]) -> int:
    path.parent.mkdir(parents=True, exist_ok=True)
    count = 0
    with path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=header)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)
            count += 1
    return count


def load_emotion_rows(path: Path) -> list[EmotionRow]:
    if not path.exists():
        raise FileNotFoundError(f"Emotion dataset not found: {path}")

    rows: list[EmotionRow] = []
    with path.open("r", encoding="utf-8", newline="", errors="ignore") as file:
        reader = csv.DictReader(file)
        for row in reader:
            text = clean_text(row.get("Comment") or row.get("text"))
            label = clean_text(row.get("Emotion") or row.get("label")).lower()
            if not text or not label:
                continue
            rows.append(EmotionRow(text=text, label=label))
    if not rows:
        raise ValueError("No usable rows in emotion dataset.")
    return rows


def load_sentiment_rows(path: Path) -> list[SentimentRow]:
    if not path.exists():
        raise FileNotFoundError(f"Sentiment dataset not found: {path}")

    allowed = {"positive", "negative", "neutral"}
    rows: list[SentimentRow] = []
    with path.open("r", encoding="utf-8", newline="", errors="ignore") as file:
        reader = csv.DictReader(file)
        for row in reader:
            text = clean_text(row.get("text"))
            label = clean_text(row.get("sentiment")).lower()
            if not text or label not in allowed:
                continue
            rows.append(SentimentRow(text=text, label=label))
    if not rows:
        raise ValueError("No usable rows in sentiment dataset.")
    return rows


def build_text_classifier(texts: list[str], labels: list[str], seed: int) -> tuple[Pipeline, dict[str, float]]:
    x_train, x_test, y_train, y_test = train_test_split(
        texts,
        labels,
        test_size=0.2,
        random_state=seed,
        stratify=labels,
    )

    pipeline = Pipeline(
        steps=[
            (
                "tfidf",
                TfidfVectorizer(
                    lowercase=True,
                    strip_accents="unicode",
                    ngram_range=(1, 2),
                    min_df=2,
                    max_df=0.95,
                    max_features=120000,
                    sublinear_tf=True,
                ),
            ),
            (
                "clf",
                LogisticRegression(
                    solver="liblinear",
                    max_iter=500,
                    class_weight="balanced",
                    random_state=seed,
                ),
            ),
        ]
    )
    pipeline.fit(x_train, y_train)
    y_pred = pipeline.predict(x_test)

    metrics = {
        "accuracy": round(float(accuracy_score(y_test, y_pred)), 4),
        "macro_f1": round(float(f1_score(y_test, y_pred, average="macro")), 4),
        "samples_train": float(len(y_train)),
        "samples_test": float(len(y_test)),
    }
    return pipeline, metrics


def save_split_dataset(path_train: Path, path_test: Path, texts: list[str], labels: list[str], seed: int) -> dict[str, int]:
    x_train, x_test, y_train, y_test = train_test_split(
        texts,
        labels,
        test_size=0.2,
        random_state=seed,
        stratify=labels,
    )

    train_rows = [{"text": text, "label": label} for text, label in zip(x_train, y_train)]
    test_rows = [{"text": text, "label": label} for text, label in zip(x_test, y_test)]

    write_rows(path_train, ["text", "label"], train_rows)
    write_rows(path_test, ["text", "label"], test_rows)
    return {"train": len(train_rows), "test": len(test_rows)}


def pick_weighted(weights: dict[str, float], rng: random.Random) -> str:
    items = list(weights.items())
    labels = [item[0] for item in items]
    probabilities = [item[1] for item in items]
    return rng.choices(labels, weights=probabilities, k=1)[0]


def generate_mood_forecast_dataset(path: Path, rows_per_case: int, rng: random.Random) -> int:
    labels = ["Very Sad", "Low", "Calm", "Good", "Happy"]
    header = [
        "case_label",
        "mood_day_minus_3",
        "mood_day_minus_2",
        "mood_day_minus_1",
        "journal_sentiment_score",
        "stress_score",
        "sleep_score",
        "energy_score",
        "trigger_cluster",
        "next_day_mood_label",
    ]
    trigger_clusters = ["work_studies", "health_sleep", "relationships", "money_future", "growth_positive"]

    rows: list[dict[str, object]] = []
    for idx, label in enumerate(labels, start=1):
        for _ in range(rows_per_case):
            base = idx
            mood_1 = max(1, min(5, int(round(base + rng.uniform(-1.2, 1.2)))))
            mood_2 = max(1, min(5, int(round(base + rng.uniform(-1.0, 1.0)))))
            mood_3 = max(1, min(5, int(round(base + rng.uniform(-0.8, 0.8)))))
            sentiment_score = round(((mood_1 + mood_2 + mood_3) / 3.0 - 3.0) / 2.0, 3)
            stress = max(1, min(5, int(round(6 - ((mood_1 + mood_2 + mood_3) / 3.0) + rng.uniform(-0.8, 0.8)))))
            sleep = max(1, min(5, int(round((mood_2 + mood_3) / 2.0 + rng.uniform(-0.8, 0.8)))))
            energy = max(1, min(5, int(round((mood_1 + mood_2 + mood_3) / 3.0 + rng.uniform(-0.6, 0.9)))))

            rows.append(
                {
                    "case_label": label,
                    "mood_day_minus_3": mood_1,
                    "mood_day_minus_2": mood_2,
                    "mood_day_minus_1": mood_3,
                    "journal_sentiment_score": sentiment_score,
                    "stress_score": stress,
                    "sleep_score": sleep,
                    "energy_score": energy,
                    "trigger_cluster": rng.choice(trigger_clusters),
                    "next_day_mood_label": label,
                }
            )
    return write_rows(path, header, rows)


def generate_mood_pattern_dataset(path: Path, rows_per_case: int, rng: random.Random) -> int:
    patterns = [
        "Recovery",
        "Stable Positive",
        "Stress Spike",
        "Emotional Drift",
        "High Variability",
        "Burnout Risk",
    ]
    emotions = ["joy", "sadness", "anger", "fear", "love", "surprise"]
    sentiments = ["positive", "negative", "neutral"]
    triggers = ["work_studies", "health_sleep", "relationships", "time_pressure", "social_media", "growth_positive"]
    times = ["Morning", "Afternoon", "Evening", "Late Night"]

    header = [
        "pattern_label",
        "entry_emotion",
        "entry_sentiment",
        "dominant_trigger",
        "time_bucket",
        "mood_swing_index",
        "pattern_confidence",
    ]
    rows: list[dict[str, object]] = []
    for pattern in patterns:
        for _ in range(rows_per_case):
            rows.append(
                {
                    "pattern_label": pattern,
                    "entry_emotion": rng.choice(emotions),
                    "entry_sentiment": rng.choice(sentiments),
                    "dominant_trigger": rng.choice(triggers),
                    "time_bucket": rng.choice(times),
                    "mood_swing_index": round(rng.uniform(0.1, 1.0), 3),
                    "pattern_confidence": rng.randint(55, 96),
                }
            )
    return write_rows(path, header, rows)


def generate_weekly_wellness_dataset(path: Path, rows_per_case: int, rng: random.Random) -> int:
    labels = ["High Strain", "Needs Support", "Mixed", "Steady", "Resilient"]
    header = [
        "weekly_label",
        "avg_mood_score",
        "mood_variability",
        "journal_negative_ratio",
        "quickcheckin_avg",
        "low_signal_rate",
        "stress_mean",
        "energy_mean",
        "recovery_score",
    ]

    rows: list[dict[str, object]] = []
    anchors = {
        "High Strain": (1.7, 0.7, 0.8, 1.9, 0.75, 4.6, 1.8, 0.2),
        "Needs Support": (2.4, 0.6, 0.62, 2.5, 0.55, 4.0, 2.3, 0.35),
        "Mixed": (3.0, 0.5, 0.45, 3.0, 0.38, 3.2, 3.0, 0.5),
        "Steady": (3.7, 0.35, 0.3, 3.6, 0.2, 2.6, 3.7, 0.68),
        "Resilient": (4.4, 0.25, 0.18, 4.3, 0.1, 1.9, 4.4, 0.84),
    }

    for label in labels:
        a = anchors[label]
        for _ in range(rows_per_case):
            rows.append(
                {
                    "weekly_label": label,
                    "avg_mood_score": round(max(1.0, min(5.0, a[0] + rng.uniform(-0.45, 0.45))), 3),
                    "mood_variability": round(max(0.05, min(1.0, a[1] + rng.uniform(-0.18, 0.18))), 3),
                    "journal_negative_ratio": round(max(0.0, min(1.0, a[2] + rng.uniform(-0.15, 0.15))), 3),
                    "quickcheckin_avg": round(max(1.0, min(5.0, a[3] + rng.uniform(-0.45, 0.45))), 3),
                    "low_signal_rate": round(max(0.0, min(1.0, a[4] + rng.uniform(-0.16, 0.16))), 3),
                    "stress_mean": round(max(1.0, min(5.0, a[5] + rng.uniform(-0.4, 0.4))), 3),
                    "energy_mean": round(max(1.0, min(5.0, a[6] + rng.uniform(-0.45, 0.45))), 3),
                    "recovery_score": round(max(0.0, min(1.0, a[7] + rng.uniform(-0.14, 0.14))), 3),
                }
            )
    return write_rows(path, header, rows)


def generate_stats_box_dataset(path: Path, row_count: int, rng: random.Random) -> int:
    header = [
        "avg_mood_score",
        "mood_variability",
        "journal_negative_ratio",
        "stress_mean",
        "anxiety_mean",
        "support_mean",
        "stress_index",
        "emotional_stability",
        "positivity_ratio",
        "confidence_score",
    ]
    rows: list[dict[str, object]] = []
    for _ in range(row_count):
        avg_mood = rng.uniform(1.0, 5.0)
        variability = rng.uniform(0.08, 1.0)
        negative_ratio = rng.uniform(0.0, 1.0)
        stress = rng.uniform(1.0, 5.0)
        anxiety = rng.uniform(1.0, 5.0)
        support = rng.uniform(1.0, 5.0)

        stress_index = int(round(max(0, min(100, (stress * 16) + (anxiety * 10) + (negative_ratio * 20) - (avg_mood * 8)))))
        emotional_stability = int(round(max(0, min(100, (avg_mood * 18) + ((1 - variability) * 22) + (support * 8) - (negative_ratio * 14)))))
        positivity_ratio = int(round(max(0, min(100, (1 - negative_ratio) * 100))))
        confidence_score = int(round(max(35, min(96, 35 + (100 - variability * 45) / 2 + support * 6))))

        rows.append(
            {
                "avg_mood_score": round(avg_mood, 3),
                "mood_variability": round(variability, 3),
                "journal_negative_ratio": round(negative_ratio, 3),
                "stress_mean": round(stress, 3),
                "anxiety_mean": round(anxiety, 3),
                "support_mean": round(support, 3),
                "stress_index": stress_index,
                "emotional_stability": emotional_stability,
                "positivity_ratio": positivity_ratio,
                "confidence_score": confidence_score,
            }
        )
    return write_rows(path, header, rows)


def generate_cosmic_dataset(path: Path, rows_per_sign: int, rng: random.Random) -> int:
    signs = [
        "Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo",
        "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces",
    ]
    trait_map = {
        "Aries": ("bold", "impatient"),
        "Taurus": ("grounded", "stubborn"),
        "Gemini": ("curious", "restless"),
        "Cancer": ("nurturing", "sensitive"),
        "Leo": ("radiant", "proud"),
        "Virgo": ("precise", "self-critical"),
        "Libra": ("harmonizing", "indecisive"),
        "Scorpio": ("intense", "guarded"),
        "Sagittarius": ("optimistic", "impulsive"),
        "Capricorn": ("disciplined", "rigid"),
        "Aquarius": ("visionary", "detached"),
        "Pisces": ("empathetic", "escapist"),
    }
    emotions = ["joy", "sadness", "fear", "anger", "love", "surprise"]
    moods = ["Very Sad", "Low", "Calm", "Good", "Happy"]
    sentiments = ["negative", "neutral", "positive"]
    trends = ["dropping", "steady", "improving"]

    header = [
        "sun_sign",
        "trait_primary",
        "trait_shadow",
        "dominant_emotion",
        "dominant_mood",
        "sentiment_band",
        "mood_trend",
        "cosmic_mood_focus",
        "cosmic_emotion_focus",
        "cosmic_feeling_focus",
    ]
    rows: list[dict[str, object]] = []
    for sign in signs:
        primary, shadow = trait_map[sign]
        for _ in range(rows_per_sign):
            emotion = rng.choice(emotions)
            mood = rng.choice(moods)
            sentiment = rng.choice(sentiments)
            trend = rng.choice(trends)
            rows.append(
                {
                    "sun_sign": sign,
                    "trait_primary": primary,
                    "trait_shadow": shadow,
                    "dominant_emotion": emotion,
                    "dominant_mood": mood,
                    "sentiment_band": sentiment,
                    "mood_trend": trend,
                    "cosmic_mood_focus": f"{primary} balance",
                    "cosmic_emotion_focus": f"{emotion} regulation",
                    "cosmic_feeling_focus": f"from {shadow} to grounded",
                }
            )
    return write_rows(path, header, rows)


def main() -> None:
    args = parse_args()
    rng = random.Random(SEED)

    emotion_data_path = Path(args.emotion_data).expanduser()
    sentiment_data_path = Path(args.sentiment_data).expanduser()
    models_dir = Path(args.models_dir).expanduser()
    train_dir = Path(args.train_dir).expanduser()
    test_dir = Path(args.test_dir).expanduser()
    models_dir.mkdir(parents=True, exist_ok=True)
    train_dir.mkdir(parents=True, exist_ok=True)
    test_dir.mkdir(parents=True, exist_ok=True)

    print(f"Loading emotion data: {emotion_data_path}")
    emotion_rows = load_emotion_rows(emotion_data_path)
    print(f"Emotion samples: {len(emotion_rows)} | labels: {dict(Counter(row.label for row in emotion_rows))}")

    print(f"Loading sentiment data: {sentiment_data_path}")
    sentiment_rows = load_sentiment_rows(sentiment_data_path)
    print(f"Sentiment samples: {len(sentiment_rows)} | labels: {dict(Counter(row.label for row in sentiment_rows))}")

    emotion_texts = [row.text for row in emotion_rows]
    emotion_labels = [row.label for row in emotion_rows]
    sentiment_texts = [row.text for row in sentiment_rows]
    sentiment_labels = [row.label for row in sentiment_rows]

    emotion_model, emotion_metrics = build_text_classifier(emotion_texts, emotion_labels, SEED)
    sentiment_model, sentiment_metrics = build_text_classifier(sentiment_texts, sentiment_labels, SEED)

    emotion_bundle = {
        "pipeline": emotion_model,
        "metadata": {
            "created_at_utc": datetime.now(timezone.utc).isoformat(),
            "task": "journal_emotion_classification",
            "labels": sorted(set(emotion_labels)),
            "metrics": emotion_metrics,
            "source_dataset": str(emotion_data_path),
        },
    }
    sentiment_bundle = {
        "pipeline": sentiment_model,
        "metadata": {
            "created_at_utc": datetime.now(timezone.utc).isoformat(),
            "task": "journal_sentiment_classification",
            "labels": sorted(set(sentiment_labels)),
            "metrics": sentiment_metrics,
            "source_dataset": str(sentiment_data_path),
        },
    }

    emotion_model_path = models_dir / "journal_emotion_model.joblib"
    sentiment_model_path = models_dir / "journal_sentiment_model.joblib"
    joblib.dump(emotion_bundle, emotion_model_path, compress=3)
    joblib.dump(sentiment_bundle, sentiment_model_path, compress=3)

    split_emotion = save_split_dataset(
        train_dir / "journal_emotion_train.csv",
        test_dir / "journal_emotion_test.csv",
        emotion_texts,
        emotion_labels,
        SEED,
    )
    split_sentiment = save_split_dataset(
        train_dir / "journal_sentiment_train.csv",
        test_dir / "journal_sentiment_test.csv",
        sentiment_texts,
        sentiment_labels,
        SEED,
    )

    generated_counts = {
        "mood_forecast_train": generate_mood_forecast_dataset(train_dir / "mood_forecast_train.csv", 1400, rng),
        "mood_forecast_test": generate_mood_forecast_dataset(test_dir / "mood_forecast_test.csv", 350, rng),
        "mood_pattern_train": generate_mood_pattern_dataset(train_dir / "mood_pattern_train.csv", 1000, rng),
        "mood_pattern_test": generate_mood_pattern_dataset(test_dir / "mood_pattern_test.csv", 300, rng),
        "weekly_wellness_train": generate_weekly_wellness_dataset(train_dir / "weekly_wellness_train.csv", 1000, rng),
        "weekly_wellness_test": generate_weekly_wellness_dataset(test_dir / "weekly_wellness_test.csv", 300, rng),
        "stats_box_train": generate_stats_box_dataset(train_dir / "stats_box_train.csv", 6000, rng),
        "stats_box_test": generate_stats_box_dataset(test_dir / "stats_box_test.csv", 1800, rng),
        "cosmic_insights_train": generate_cosmic_dataset(train_dir / "cosmic_insights_train.csv", 1000, rng),
        "cosmic_insights_test": generate_cosmic_dataset(test_dir / "cosmic_insights_test.csv", 200, rng),
    }

    metrics_payload = {
        "created_at_utc": datetime.now(timezone.utc).isoformat(),
        "emotion_model": {
            "path": str(emotion_model_path),
            "metrics": emotion_metrics,
            "split_rows": split_emotion,
        },
        "sentiment_model": {
            "path": str(sentiment_model_path),
            "metrics": sentiment_metrics,
            "split_rows": split_sentiment,
        },
        "generated_dataset_rows": generated_counts,
        "train_dir": str(train_dir),
        "test_dir": str(test_dir),
    }

    metrics_path = models_dir / "wellness_models_metrics.json"
    with metrics_path.open("w", encoding="utf-8") as file:
        json.dump(metrics_payload, file, indent=2)

    print(f"Saved emotion model: {emotion_model_path}")
    print(f"Saved sentiment model: {sentiment_model_path}")
    print(f"Saved metrics: {metrics_path}")
    print("Generated datasets:")
    for name, count in generated_counts.items():
        print(f"  - {name}: {count} rows")


if __name__ == "__main__":
    main()
