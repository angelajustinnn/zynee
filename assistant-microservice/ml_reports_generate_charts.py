from __future__ import annotations

import argparse
import csv
from pathlib import Path
from typing import Any

import joblib
import matplotlib
import numpy as np
from sklearn.metrics import (
    accuracy_score,
    auc,
    confusion_matrix,
    f1_score,
    precision_recall_curve,
    roc_curve,
)

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402


def parse_args() -> argparse.Namespace:
    base_dir = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(
        description="Generate ML evaluation charts for Zynee models without modifying runtime code."
    )
    parser.add_argument(
        "--suicide-model",
        default=str(base_dir / "models" / "suicide_risk_model.joblib"),
        help="Path to suicide-risk model bundle.",
    )
    parser.add_argument(
        "--suicide-test-csv",
        default=str(base_dir / "data" / "processed" / "suicide_test_split.csv"),
        help="Path to suicide test split CSV (text,label).",
    )
    parser.add_argument(
        "--emotion-model",
        default=str(base_dir / "models" / "journal_emotion_model.joblib"),
        help="Path to journal emotion model bundle.",
    )
    parser.add_argument(
        "--emotion-test-csv",
        default=str(base_dir / "data" / "testing" / "journal_emotion_test.csv"),
        help="Path to journal emotion test split CSV (text,label).",
    )
    parser.add_argument(
        "--sentiment-model",
        default=str(base_dir / "models" / "journal_sentiment_model.joblib"),
        help="Path to journal sentiment model bundle.",
    )
    parser.add_argument(
        "--sentiment-test-csv",
        default=str(base_dir / "data" / "testing" / "journal_sentiment_test.csv"),
        help="Path to journal sentiment test split CSV (text,label).",
    )
    parser.add_argument(
        "--out-dir",
        default=str(base_dir / "ml-report" / "charts"),
        help="Directory where chart PNG files are saved.",
    )
    parser.add_argument(
        "--dpi",
        type=int,
        default=180,
        help="Output chart DPI.",
    )
    return parser.parse_args()


def load_model(path: Path) -> tuple[Any, dict[str, Any]]:
    if not path.exists():
        raise FileNotFoundError(f"Model not found: {path}")
    loaded = joblib.load(path)
    if isinstance(loaded, dict) and "pipeline" in loaded:
        metadata = loaded.get("metadata", {})
        return loaded["pipeline"], metadata if isinstance(metadata, dict) else {}
    return loaded, {}


def load_text_label_rows(path: Path) -> tuple[list[str], list[str]]:
    if not path.exists():
        raise FileNotFoundError(f"CSV not found: {path}")
    texts: list[str] = []
    labels: list[str] = []
    with path.open("r", encoding="utf-8", newline="", errors="ignore") as file:
        reader = csv.DictReader(file)
        for row in reader:
            text = str(row.get("text", "")).strip()
            label = str(row.get("label", "")).strip()
            if not text or not label:
                continue
            texts.append(text)
            labels.append(label)
    if not texts:
        raise ValueError(f"No usable rows in {path}")
    return texts, labels


def normalize_suicide_label(raw: str) -> int:
    value = raw.strip().lower()
    if value in {"suicide", "suicidal", "1", "true", "yes", "positive"}:
        return 1
    if value in {"non-suicide", "non suicide", "0", "false", "no", "negative", "normal"}:
        return 0
    raise ValueError(f"Unexpected suicide label: {raw}")


def save_confusion_matrix_plot(
    cm: np.ndarray,
    labels: list[str],
    title: str,
    output_path: Path,
    dpi: int,
) -> None:
    fig, ax = plt.subplots(figsize=(7.2, 5.6))
    image = ax.imshow(cm, interpolation="nearest", cmap="Blues")
    fig.colorbar(image, ax=ax, fraction=0.046, pad=0.04)
    ax.set_title(title)
    ax.set_xlabel("Predicted label")
    ax.set_ylabel("True label")
    ax.set_xticks(np.arange(len(labels)))
    ax.set_yticks(np.arange(len(labels)))
    ax.set_xticklabels(labels, rotation=25, ha="right")
    ax.set_yticklabels(labels)

    threshold = cm.max() / 2.0 if cm.size else 0.0
    for row_idx in range(cm.shape[0]):
        for col_idx in range(cm.shape[1]):
            value = int(cm[row_idx, col_idx])
            text_color = "white" if value > threshold else "black"
            ax.text(col_idx, row_idx, str(value), ha="center", va="center", color=text_color)

    fig.tight_layout()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=dpi)
    plt.close(fig)


def save_suicide_roc_plot(y_true: np.ndarray, probabilities: np.ndarray, output_path: Path, dpi: int) -> float:
    fpr, tpr, _ = roc_curve(y_true, probabilities)
    roc_auc_value = auc(fpr, tpr)

    fig, ax = plt.subplots(figsize=(7.2, 5.2))
    ax.plot(fpr, tpr, color="#1f77b4", linewidth=2.0, label=f"ROC AUC = {roc_auc_value:.4f}")
    ax.plot([0, 1], [0, 1], linestyle="--", color="gray", linewidth=1.2, label="Random baseline")
    ax.set_title("Suicide Risk Model - ROC Curve")
    ax.set_xlabel("False Positive Rate")
    ax.set_ylabel("True Positive Rate")
    ax.grid(alpha=0.25)
    ax.legend(loc="lower right")
    fig.tight_layout()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=dpi)
    plt.close(fig)
    return float(roc_auc_value)


def save_suicide_pr_plot(y_true: np.ndarray, probabilities: np.ndarray, output_path: Path, dpi: int) -> None:
    precision, recall, _ = precision_recall_curve(y_true, probabilities)
    baseline = float(np.mean(y_true))

    fig, ax = plt.subplots(figsize=(7.2, 5.2))
    ax.plot(recall, precision, color="#2ca02c", linewidth=2.0, label="PR curve")
    ax.axhline(y=baseline, linestyle="--", color="gray", linewidth=1.2, label=f"Baseline = {baseline:.4f}")
    ax.set_title("Suicide Risk Model - Precision/Recall Curve")
    ax.set_xlabel("Recall")
    ax.set_ylabel("Precision")
    ax.grid(alpha=0.25)
    ax.legend(loc="lower left")
    fig.tight_layout()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=dpi)
    plt.close(fig)


def save_suicide_probability_histogram(
    y_true: np.ndarray, probabilities: np.ndarray, output_path: Path, dpi: int
) -> None:
    fig, ax = plt.subplots(figsize=(7.2, 5.2))
    ax.hist(
        probabilities[y_true == 0],
        bins=30,
        alpha=0.65,
        color="#6baed6",
        label="True non-suicide",
    )
    ax.hist(
        probabilities[y_true == 1],
        bins=30,
        alpha=0.65,
        color="#fb6a4a",
        label="True suicide",
    )
    ax.axvline(x=0.5, color="black", linestyle="--", linewidth=1.2, label="Decision threshold (0.5)")
    ax.set_title("Suicide Risk Model - Probability Distribution")
    ax.set_xlabel("Predicted suicide probability")
    ax.set_ylabel("Number of test samples")
    ax.grid(alpha=0.25)
    ax.legend(loc="upper center")
    fig.tight_layout()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=dpi)
    plt.close(fig)


def save_model_comparison_plot(
    model_names: list[str],
    accuracies: list[float],
    f1_scores: list[float],
    output_path: Path,
    dpi: int,
) -> None:
    x_positions = np.arange(len(model_names))
    width = 0.34

    fig, ax = plt.subplots(figsize=(8.2, 5.2))
    acc_bars = ax.bar(x_positions - width / 2, accuracies, width=width, label="Accuracy", color="#4c78a8")
    f1_bars = ax.bar(x_positions + width / 2, f1_scores, width=width, label="F1", color="#f58518")
    ax.set_ylim(0.0, 1.0)
    ax.set_ylabel("Score")
    ax.set_title("Zynee ML Models - Accuracy vs F1")
    ax.set_xticks(x_positions)
    ax.set_xticklabels(model_names, rotation=10)
    ax.grid(axis="y", alpha=0.25)
    ax.legend(loc="upper right")

    for bars in (acc_bars, f1_bars):
        for bar in bars:
            value = bar.get_height()
            ax.text(
                bar.get_x() + (bar.get_width() / 2),
                value + 0.015,
                f"{value:.3f}",
                ha="center",
                va="bottom",
                fontsize=9,
            )

    fig.tight_layout()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=dpi)
    plt.close(fig)


def main() -> None:
    args = parse_args()
    out_dir = Path(args.out_dir).expanduser()
    out_dir.mkdir(parents=True, exist_ok=True)

    suicide_model, _ = load_model(Path(args.suicide_model).expanduser())
    suicide_texts, suicide_labels_raw = load_text_label_rows(Path(args.suicide_test_csv).expanduser())
    suicide_true = np.array([normalize_suicide_label(value) for value in suicide_labels_raw], dtype=int)

    if hasattr(suicide_model, "predict_proba"):
        suicide_prob_matrix = suicide_model.predict_proba(suicide_texts)
        class_names = [str(value).strip().lower() for value in getattr(suicide_model, "classes_", [])]
        if "suicide" in class_names:
            suicide_probs = suicide_prob_matrix[:, class_names.index("suicide")]
        elif suicide_prob_matrix.shape[1] >= 2:
            suicide_probs = suicide_prob_matrix[:, 1]
        else:
            suicide_probs = suicide_prob_matrix[:, 0]
    else:
        raise RuntimeError("Suicide model does not support predict_proba, cannot produce ROC/PR charts.")

    suicide_pred = (suicide_probs >= 0.5).astype(int)
    suicide_cm = confusion_matrix(suicide_true, suicide_pred, labels=[0, 1])
    suicide_accuracy = float(accuracy_score(suicide_true, suicide_pred))
    suicide_f1 = float(f1_score(suicide_true, suicide_pred, average="binary", zero_division=0))

    save_confusion_matrix_plot(
        cm=suicide_cm,
        labels=["non-suicide", "suicide"],
        title="Suicide Risk Model - Confusion Matrix",
        output_path=out_dir / "suicide_confusion_matrix.png",
        dpi=args.dpi,
    )
    save_suicide_roc_plot(
        y_true=suicide_true,
        probabilities=suicide_probs,
        output_path=out_dir / "suicide_roc_curve.png",
        dpi=args.dpi,
    )
    save_suicide_pr_plot(
        y_true=suicide_true,
        probabilities=suicide_probs,
        output_path=out_dir / "suicide_precision_recall_curve.png",
        dpi=args.dpi,
    )
    save_suicide_probability_histogram(
        y_true=suicide_true,
        probabilities=suicide_probs,
        output_path=out_dir / "suicide_probability_histogram.png",
        dpi=args.dpi,
    )

    emotion_model, _ = load_model(Path(args.emotion_model).expanduser())
    emotion_texts, emotion_true = load_text_label_rows(Path(args.emotion_test_csv).expanduser())
    emotion_pred = [str(value).strip().lower() for value in emotion_model.predict(emotion_texts)]
    emotion_true = [value.strip().lower() for value in emotion_true]
    emotion_labels = sorted(set(emotion_true) | set(emotion_pred))
    emotion_cm = confusion_matrix(emotion_true, emotion_pred, labels=emotion_labels)
    emotion_accuracy = float(accuracy_score(emotion_true, emotion_pred))
    emotion_f1 = float(f1_score(emotion_true, emotion_pred, average="macro", zero_division=0))
    save_confusion_matrix_plot(
        cm=emotion_cm,
        labels=emotion_labels,
        title="Journal Emotion Model - Confusion Matrix",
        output_path=out_dir / "journal_emotion_confusion_matrix.png",
        dpi=args.dpi,
    )

    sentiment_model, _ = load_model(Path(args.sentiment_model).expanduser())
    sentiment_texts, sentiment_true = load_text_label_rows(Path(args.sentiment_test_csv).expanduser())
    sentiment_pred = [str(value).strip().lower() for value in sentiment_model.predict(sentiment_texts)]
    sentiment_true = [value.strip().lower() for value in sentiment_true]
    sentiment_labels = sorted(set(sentiment_true) | set(sentiment_pred))
    sentiment_cm = confusion_matrix(sentiment_true, sentiment_pred, labels=sentiment_labels)
    sentiment_accuracy = float(accuracy_score(sentiment_true, sentiment_pred))
    sentiment_f1 = float(f1_score(sentiment_true, sentiment_pred, average="macro", zero_division=0))
    save_confusion_matrix_plot(
        cm=sentiment_cm,
        labels=sentiment_labels,
        title="Journal Sentiment Model - Confusion Matrix",
        output_path=out_dir / "journal_sentiment_confusion_matrix.png",
        dpi=args.dpi,
    )

    save_model_comparison_plot(
        model_names=["Suicide Risk", "Journal Emotion", "Journal Sentiment"],
        accuracies=[suicide_accuracy, emotion_accuracy, sentiment_accuracy],
        f1_scores=[suicide_f1, emotion_f1, sentiment_f1],
        output_path=out_dir / "model_accuracy_f1_comparison.png",
        dpi=args.dpi,
    )

    summary_markdown = out_dir / "model_metrics_summary.md"
    summary_markdown.write_text(
        "\n".join(
            [
                "# Zynee ML Model Metrics Summary",
                "",
                "Generated from saved models + test CSV files.",
                "",
                "## Suicide Risk Model",
                f"- Accuracy: {suicide_accuracy:.4f}",
                f"- F1 (binary): {suicide_f1:.4f}",
                "",
                "## Journal Emotion Model",
                f"- Accuracy: {emotion_accuracy:.4f}",
                f"- F1 (macro): {emotion_f1:.4f}",
                "",
                "## Journal Sentiment Model",
                f"- Accuracy: {sentiment_accuracy:.4f}",
                f"- F1 (macro): {sentiment_f1:.4f}",
                "",
                "## Charts",
                "- suicide_confusion_matrix.png",
                "- suicide_roc_curve.png",
                "- suicide_precision_recall_curve.png",
                "- suicide_probability_histogram.png",
                "- journal_emotion_confusion_matrix.png",
                "- journal_sentiment_confusion_matrix.png",
                "- model_accuracy_f1_comparison.png",
                "",
            ]
        )
        + "\n",
        encoding="utf-8",
    )

    print(f"Charts generated in: {out_dir}")
    print(f"Metrics summary: {summary_markdown}")


if __name__ == "__main__":
    main()
