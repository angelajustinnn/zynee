from __future__ import annotations

import argparse
import csv
from collections import Counter
from pathlib import Path

import matplotlib
import numpy as np

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402


def parse_args() -> argparse.Namespace:
    base_dir = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(
        description="Generate train/test dataset PNG summaries for Zynee report use."
    )
    parser.add_argument(
        "--train-dir",
        default=str(base_dir / "data" / "training"),
        help="Path to training CSV directory.",
    )
    parser.add_argument(
        "--test-dir",
        default=str(base_dir / "data" / "testing"),
        help="Path to testing CSV directory.",
    )
    parser.add_argument(
        "--out-dir",
        default=str(base_dir / "ml-report" / "charts" / "dataset-train-test"),
        help="Directory where train/test dataset PNG files are saved.",
    )
    parser.add_argument(
        "--dpi",
        type=int,
        default=180,
        help="Output chart DPI.",
    )
    return parser.parse_args()


def load_column_values(csv_path: Path, column_name: str) -> list[str]:
    values: list[str] = []
    with csv_path.open("r", encoding="utf-8", newline="", errors="ignore") as file:
        reader = csv.DictReader(file)
        for row in reader:
            value = str(row.get(column_name, "")).strip()
            if value:
                values.append(value)
    return values


def load_numeric_column(csv_path: Path, column_name: str) -> list[float]:
    values: list[float] = []
    with csv_path.open("r", encoding="utf-8", newline="", errors="ignore") as file:
        reader = csv.DictReader(file)
        for row in reader:
            raw = str(row.get(column_name, "")).strip()
            if not raw:
                continue
            try:
                values.append(float(raw))
            except ValueError:
                continue
    return values


def save_grouped_count_plot(
    train_counter: Counter[str],
    test_counter: Counter[str],
    title: str,
    x_label: str,
    output_path: Path,
    dpi: int,
) -> None:
    labels = sorted(set(train_counter.keys()) | set(test_counter.keys()))
    train_counts = [train_counter.get(label, 0) for label in labels]
    test_counts = [test_counter.get(label, 0) for label in labels]

    x_positions = np.arange(len(labels))
    width = 0.38

    fig, ax = plt.subplots(figsize=(10.0, 5.6))
    bars_train = ax.bar(
        x_positions - width / 2,
        train_counts,
        width=width,
        color="#4c78a8",
        label="Train",
    )
    bars_test = ax.bar(
        x_positions + width / 2,
        test_counts,
        width=width,
        color="#f58518",
        label="Test",
    )

    ax.set_title(title)
    ax.set_xlabel(x_label)
    ax.set_ylabel("Row count")
    ax.set_xticks(x_positions)
    ax.set_xticklabels(labels, rotation=25, ha="right")
    ax.grid(axis="y", alpha=0.25)
    ax.legend(loc="upper right")

    for bars in (bars_train, bars_test):
        for bar in bars:
            value = int(bar.get_height())
            ax.text(
                bar.get_x() + bar.get_width() / 2,
                bar.get_height() + max(1.0, max(train_counts + test_counts, default=1) * 0.01),
                str(value),
                ha="center",
                va="bottom",
                fontsize=8,
            )

    fig.tight_layout()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=dpi)
    plt.close(fig)


def save_stats_box_histograms(train_csv: Path, test_csv: Path, output_path: Path, dpi: int) -> None:
    fields = ["stress_index", "emotional_stability", "positivity_ratio", "confidence_score"]
    field_titles = {
        "stress_index": "Stress Index",
        "emotional_stability": "Emotional Stability",
        "positivity_ratio": "Positivity Ratio",
        "confidence_score": "Confidence Score",
    }

    fig, axes = plt.subplots(2, 2, figsize=(10.5, 7.2))
    axes_flat = axes.flatten()

    for idx, field in enumerate(fields):
        train_values = load_numeric_column(train_csv, field)
        test_values = load_numeric_column(test_csv, field)
        ax = axes_flat[idx]
        bins = 20
        ax.hist(train_values, bins=bins, alpha=0.65, color="#4c78a8", label="Train")
        ax.hist(test_values, bins=bins, alpha=0.65, color="#f58518", label="Test")
        ax.set_title(field_titles[field])
        ax.set_xlabel("Value")
        ax.set_ylabel("Count")
        ax.grid(alpha=0.2)
        if idx == 0:
            ax.legend(loc="upper right")

    fig.suptitle("Stats Box Dataset - Train vs Test Numeric Distributions", fontsize=13)
    fig.tight_layout(rect=[0, 0, 1, 0.96])
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=dpi)
    plt.close(fig)


def save_row_count_overview(
    train_dir: Path,
    test_dir: Path,
    datasets: list[str],
    output_path: Path,
    dpi: int,
) -> None:
    train_counts: list[int] = []
    test_counts: list[int] = []
    for dataset in datasets:
        train_csv = train_dir / f"{dataset}_train.csv"
        test_csv = test_dir / f"{dataset}_test.csv"
        train_count = sum(1 for _ in csv.DictReader(train_csv.open("r", encoding="utf-8", newline="", errors="ignore")))
        test_count = sum(1 for _ in csv.DictReader(test_csv.open("r", encoding="utf-8", newline="", errors="ignore")))
        train_counts.append(train_count)
        test_counts.append(test_count)

    x_positions = np.arange(len(datasets))
    width = 0.38

    fig, ax = plt.subplots(figsize=(11.2, 5.8))
    ax.bar(x_positions - width / 2, train_counts, width=width, color="#4c78a8", label="Train")
    ax.bar(x_positions + width / 2, test_counts, width=width, color="#f58518", label="Test")
    ax.set_title("Train vs Test Row Counts by Dataset")
    ax.set_xlabel("Dataset")
    ax.set_ylabel("Rows")
    ax.set_xticks(x_positions)
    ax.set_xticklabels(datasets, rotation=25, ha="right")
    ax.grid(axis="y", alpha=0.25)
    ax.legend(loc="upper right")
    fig.tight_layout()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=dpi)
    plt.close(fig)


def main() -> None:
    args = parse_args()
    train_dir = Path(args.train_dir).expanduser()
    test_dir = Path(args.test_dir).expanduser()
    out_dir = Path(args.out_dir).expanduser()
    out_dir.mkdir(parents=True, exist_ok=True)

    label_plots = [
        ("mood_forecast", "next_day_mood_label", "Mood Forecast"),
        ("mood_pattern", "pattern_label", "Mood Pattern"),
        ("weekly_wellness", "weekly_label", "Weekly Wellness"),
        ("cosmic_insights", "sun_sign", "Cosmic Insights"),
        ("journal_emotion", "label", "Journal Emotion"),
        ("journal_sentiment", "label", "Journal Sentiment"),
    ]

    for dataset_name, label_col, pretty_name in label_plots:
        train_csv = train_dir / f"{dataset_name}_train.csv"
        test_csv = test_dir / f"{dataset_name}_test.csv"
        if not train_csv.exists() or not test_csv.exists():
            continue

        train_labels = load_column_values(train_csv, label_col)
        test_labels = load_column_values(test_csv, label_col)
        train_counter = Counter(train_labels)
        test_counter = Counter(test_labels)

        save_grouped_count_plot(
            train_counter=train_counter,
            test_counter=test_counter,
            title=f"{pretty_name} Dataset - Train vs Test Label Distribution",
            x_label=label_col,
            output_path=out_dir / f"{dataset_name}_train_test_distribution.png",
            dpi=args.dpi,
        )

    stats_train = train_dir / "stats_box_train.csv"
    stats_test = test_dir / "stats_box_test.csv"
    if stats_train.exists() and stats_test.exists():
        save_stats_box_histograms(
            train_csv=stats_train,
            test_csv=stats_test,
            output_path=out_dir / "stats_box_train_test_histograms.png",
            dpi=args.dpi,
        )

    save_row_count_overview(
        train_dir=train_dir,
        test_dir=test_dir,
        datasets=[
            "mood_forecast",
            "mood_pattern",
            "weekly_wellness",
            "stats_box",
            "cosmic_insights",
            "journal_emotion",
            "journal_sentiment",
        ],
        output_path=out_dir / "all_datasets_train_test_row_counts.png",
        dpi=args.dpi,
    )

    summary_path = out_dir / "dataset_png_summary.md"
    summary_path.write_text(
        "\n".join(
            [
                "# Zynee Train/Test Dataset PNG Summary",
                "",
                "Generated PNG files in this folder:",
                "",
                "- mood_forecast_train_test_distribution.png",
                "- mood_pattern_train_test_distribution.png",
                "- weekly_wellness_train_test_distribution.png",
                "- stats_box_train_test_histograms.png",
                "- cosmic_insights_train_test_distribution.png",
                "- journal_emotion_train_test_distribution.png",
                "- journal_sentiment_train_test_distribution.png",
                "- all_datasets_train_test_row_counts.png",
                "",
            ]
        )
        + "\n",
        encoding="utf-8",
    )

    print(f"Dataset PNGs generated in: {out_dir}")
    print(f"Summary: {summary_path}")


if __name__ == "__main__":
    main()
