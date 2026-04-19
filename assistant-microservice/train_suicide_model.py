from __future__ import annotations

import argparse
import csv
import json
import random
import re
from datetime import datetime, timezone
from pathlib import Path

import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    confusion_matrix,
    precision_recall_curve,
    precision_recall_fscore_support,
    roc_auc_score,
)
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline

RANDOM_SEED = 42
SUICIDE_KEYWORD_PATTERN = re.compile(
    r"\b(suicid(?:e|al)?|kill myself|end my life|self[- ]harm|want to die)\b",
    re.IGNORECASE,
)


def parse_args() -> argparse.Namespace:
    base_dir = Path(__file__).resolve().parent
    raw_dir = base_dir / "data" / "raw"
    parser = argparse.ArgumentParser(
        description="Train Zynee suicide-risk text classifier and export model + test split."
    )
    parser.add_argument(
        "--suicide-data",
        default=str(raw_dir / "Suicide_Detection.csv"),
        help="Path to Suicide_Detection.csv (must contain text + class columns).",
    )
    parser.add_argument(
        "--emotion-data",
        default=str(raw_dir / "Emotion_classify_Data.csv"),
        help="Optional path to Emotion_classify_Data.csv for extra non-suicide examples.",
    )
    parser.add_argument(
        "--sentiment-data",
        default=str(raw_dir / "sentiment_analysis.csv"),
        help="Optional path to sentiment_analysis.csv for extra non-suicide examples.",
    )
    parser.add_argument(
        "--max-suicide-rows",
        type=int,
        default=200000,
        help="Max rows to read from Suicide_Detection (0 means all rows).",
    )
    parser.add_argument(
        "--max-extra-rows",
        type=int,
        default=25000,
        help="Max rows to read from each extra dataset (0 means all rows).",
    )
    parser.add_argument(
        "--test-size",
        type=float,
        default=0.2,
        help="Test split fraction.",
    )
    parser.add_argument(
        "--model-out",
        default=str(base_dir / "models" / "suicide_risk_model.joblib"),
        help="Output path for serialized model bundle.",
    )
    parser.add_argument(
        "--metrics-out",
        default=str(base_dir / "models" / "suicide_risk_metrics.json"),
        help="Output path for JSON metrics.",
    )
    parser.add_argument(
        "--train-split-out",
        default=str(base_dir / "data" / "processed" / "suicide_train_split.csv"),
        help="Output CSV path for training split (text,label).",
    )
    parser.add_argument(
        "--test-split-out",
        default=str(base_dir / "data" / "processed" / "suicide_test_split.csv"),
        help="Output CSV path for testing split (text,label).",
    )
    parser.add_argument(
        "--negative-ratio",
        type=float,
        default=1.25,
        help="Max non-suicide ratio vs suicide rows after balancing.",
    )
    return parser.parse_args()


def clean_text(raw: object) -> str:
    value = "" if raw is None else str(raw)
    value = re.sub(r"\s+", " ", value).strip()
    return value


def normalize_binary_label(raw: object) -> int | None:
    value = clean_text(raw).lower()
    if value in {"suicide", "suicidal", "1", "true", "yes", "positive"}:
        return 1
    if value in {"non-suicide", "non suicide", "0", "false", "no", "negative", "normal"}:
        return 0
    return None


def read_suicide_dataset(path: Path, max_rows: int) -> tuple[list[str], list[int]]:
    if not path.exists():
        raise FileNotFoundError(f"Suicide dataset not found: {path}")

    texts: list[str] = []
    labels: list[int] = []
    with path.open("r", encoding="utf-8", newline="", errors="ignore") as file:
        reader = csv.DictReader(file)
        for row in reader:
            text = clean_text(row.get("text") or row.get("Text"))
            label = normalize_binary_label(row.get("class") or row.get("label") or row.get("status"))
            if not text or label is None:
                continue
            texts.append(text)
            labels.append(label)
            if max_rows > 0 and len(texts) >= max_rows:
                break

    if not texts:
        raise ValueError("No valid rows found in suicide dataset.")
    return texts, labels


def read_extra_non_suicide_emotion(path: Path, max_rows: int) -> list[str]:
    if not path.exists():
        return []

    rows: list[str] = []
    with path.open("r", encoding="utf-8", newline="", errors="ignore") as file:
        reader = csv.DictReader(file)
        for row in reader:
            text = clean_text(row.get("Comment") or row.get("text"))
            if not text:
                continue
            if SUICIDE_KEYWORD_PATTERN.search(text):
                continue
            rows.append(text)
            if max_rows > 0 and len(rows) >= max_rows:
                break
    return rows


def read_extra_non_suicide_sentiment(path: Path, max_rows: int) -> list[str]:
    if not path.exists():
        return []

    rows: list[str] = []
    with path.open("r", encoding="utf-8", newline="", errors="ignore") as file:
        reader = csv.DictReader(file)
        for row in reader:
            sentiment = clean_text(row.get("sentiment")).lower()
            if sentiment not in {"positive", "neutral"}:
                continue
            text = clean_text(row.get("text"))
            if not text:
                continue
            if SUICIDE_KEYWORD_PATTERN.search(text):
                continue
            rows.append(text)
            if max_rows > 0 and len(rows) >= max_rows:
                break
    return rows


def balance_dataset(
    texts: list[str],
    labels: list[int],
    negative_ratio: float,
    rng: random.Random,
) -> tuple[list[str], list[int]]:
    positive = [text for text, label in zip(texts, labels) if label == 1]
    negative = [text for text, label in zip(texts, labels) if label == 0]

    if not positive or not negative:
        return texts, labels

    max_negative = int(len(positive) * max(1.0, negative_ratio))
    if len(negative) > max_negative:
        rng.shuffle(negative)
        negative = negative[:max_negative]

    combined = [(text, 1) for text in positive] + [(text, 0) for text in negative]
    rng.shuffle(combined)
    balanced_texts = [item[0] for item in combined]
    balanced_labels = [item[1] for item in combined]
    return balanced_texts, balanced_labels


def pick_thresholds(y_true: list[int], probabilities: list[float]) -> tuple[float, float]:
    precision, recall, thresholds = precision_recall_curve(y_true, probabilities)
    if len(thresholds) == 0:
        return 0.52, 0.78

    medium_candidates = [
        float(threshold)
        for prec, rec, threshold in zip(precision[:-1], recall[:-1], thresholds)
        if rec >= 0.96 and prec >= 0.45
    ]
    high_candidates = [
        float(threshold)
        for prec, rec, threshold in zip(precision[:-1], recall[:-1], thresholds)
        if rec >= 0.88 and prec >= 0.78
    ]

    medium = min(medium_candidates) if medium_candidates else 0.52
    medium = max(0.45, min(0.9, medium))
    high = max(high_candidates) if high_candidates else max(0.78, medium + 0.18)
    high = min(0.96, max(high, medium + 0.1))
    return round(medium, 3), round(high, 3)


def write_split_csv(path: Path, texts: list[str], labels: list[int]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.writer(file)
        writer.writerow(["text", "label"])
        for text, label in zip(texts, labels):
            writer.writerow([text, "suicide" if label == 1 else "non-suicide"])


def main() -> None:
    args = parse_args()
    rng = random.Random(RANDOM_SEED)

    suicide_path = Path(args.suicide_data).expanduser()
    emotion_path = Path(args.emotion_data).expanduser()
    sentiment_path = Path(args.sentiment_data).expanduser()

    print(f"Loading base suicide dataset: {suicide_path}")
    texts, labels = read_suicide_dataset(suicide_path, max_rows=args.max_suicide_rows)
    print(f"Base rows loaded: {len(texts)}")

    extra_emotion = read_extra_non_suicide_emotion(emotion_path, max_rows=args.max_extra_rows)
    extra_sentiment = read_extra_non_suicide_sentiment(sentiment_path, max_rows=args.max_extra_rows)

    if extra_emotion:
        print(f"Using extra non-suicide rows from emotion dataset: {len(extra_emotion)}")
        texts.extend(extra_emotion)
        labels.extend([0] * len(extra_emotion))
    if extra_sentiment:
        print(f"Using extra non-suicide rows from sentiment dataset: {len(extra_sentiment)}")
        texts.extend(extra_sentiment)
        labels.extend([0] * len(extra_sentiment))

    texts, labels = balance_dataset(texts, labels, negative_ratio=args.negative_ratio, rng=rng)
    suicide_count = sum(1 for label in labels if label == 1)
    non_suicide_count = len(labels) - suicide_count
    print(f"Balanced rows: {len(labels)} (suicide={suicide_count}, non-suicide={non_suicide_count})")

    x_train, x_test, y_train, y_test = train_test_split(
        texts,
        labels,
        test_size=args.test_size,
        random_state=RANDOM_SEED,
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
                    max_features=160000,
                    sublinear_tf=True,
                ),
            ),
            (
                "classifier",
                LogisticRegression(
                    solver="liblinear",
                    max_iter=400,
                    class_weight="balanced",
                    random_state=RANDOM_SEED,
                ),
            ),
        ]
    )

    print("Training model...")
    pipeline.fit(x_train, y_train)

    probabilities = pipeline.predict_proba(x_test)[:, 1]
    predictions = (probabilities >= 0.5).astype(int)

    precision, recall, f1, _ = precision_recall_fscore_support(
        y_test,
        predictions,
        average="binary",
        zero_division=0,
    )
    roc_auc = roc_auc_score(y_test, probabilities)
    accuracy = accuracy_score(y_test, predictions)
    cm = confusion_matrix(y_test, predictions).tolist()
    medium_threshold, high_threshold = pick_thresholds(y_test, probabilities.tolist())

    model_out = Path(args.model_out).expanduser()
    metrics_out = Path(args.metrics_out).expanduser()
    train_split_out = Path(args.train_split_out).expanduser()
    test_split_out = Path(args.test_split_out).expanduser()
    model_out.parent.mkdir(parents=True, exist_ok=True)
    metrics_out.parent.mkdir(parents=True, exist_ok=True)

    metadata = {
        "created_at_utc": datetime.now(timezone.utc).isoformat(),
        "training_row_count": len(y_train),
        "test_row_count": len(y_test),
        "suicide_rows": suicide_count,
        "non_suicide_rows": non_suicide_count,
        "metrics": {
            "accuracy": round(float(accuracy), 4),
            "precision": round(float(precision), 4),
            "recall": round(float(recall), 4),
            "f1": round(float(f1), 4),
            "roc_auc": round(float(roc_auc), 4),
            "confusion_matrix": cm,
        },
        "medium_threshold": medium_threshold,
        "high_threshold": high_threshold,
        "datasets_used": {
            "suicide": str(suicide_path),
            "emotion": str(emotion_path) if emotion_path.exists() else "",
            "sentiment": str(sentiment_path) if sentiment_path.exists() else "",
        },
    }

    bundle = {"pipeline": pipeline, "metadata": metadata}
    joblib.dump(bundle, model_out, compress=3)

    with metrics_out.open("w", encoding="utf-8") as file:
        json.dump(metadata, file, indent=2)

    write_split_csv(train_split_out, x_train, y_train)
    write_split_csv(test_split_out, x_test, y_test)

    print(f"Model saved: {model_out}")
    print(f"Metrics saved: {metrics_out}")
    print(f"Training split CSV: {train_split_out}")
    print(f"Testing split CSV: {test_split_out}")
    print(f"Suggested thresholds -> medium: {medium_threshold}, high: {high_threshold}")


if __name__ == "__main__":
    main()
