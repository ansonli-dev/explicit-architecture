#!/usr/bin/env python3
"""Validate deterministic structure shared by explicit-architecture packages."""

from __future__ import annotations

import argparse
import re
import tempfile
from pathlib import Path
from urllib.parse import unquote


PLACEHOLDER_RE = re.compile(r"\{\{[^{}\n]+\}\}")
LINK_RE = re.compile(r"!?\[[^\]]*]\(([^)]+)\)")
FULL_SHA_RE = re.compile(r"(?<![0-9a-fA-F])[0-9a-fA-F]{40}(?![0-9a-fA-F])")
URI_SCHEME_RE = re.compile(r"^[A-Za-z][A-Za-z0-9+.-]*:")
SPEC_ACTION_RE = re.compile(
    r"\b(?:KEEP|MODIFY|ADD|MOVE|RENAME|REPLACE|DELETE)\b"
)
CANDIDATE_VERDICT_RE = re.compile(r"\b(?:SELECTED|REJECTED|BLOCKED)\b")
SELECTION_RECORD_PATTERNS = (
    ("residual risk", re.compile(r"residual risk|剩余风险|残余风险", re.IGNORECASE)),
    ("fallback", re.compile(r"fallback|回退|降级", re.IGNORECASE)),
    (
        "invalidation condition",
        re.compile(r"invalidation condition|失效条件|重新裁决条件", re.IGNORECASE),
    ),
    ("verification", re.compile(r"verification|验证证据|验证", re.IGNORECASE)),
)
AS_IS_STATUS_RE = re.compile(r"\b(?:VERIFIED|CANDIDATE|PARTIAL|STALE)\b")

PROFILE_REQUIRED_FILES = {
    "spec": (
        "00-context.md",
        "01-domain.md",
        "02-application.md",
        "06-verification.md",
        "07-codebase-evolution.md",
        "traceability.md",
    ),
    "service-as-is": (
        "00-baseline.md",
        "01-landscape.md",
        "traceability.md",
    ),
}


def display_path(path: Path, root: Path) -> str:
    try:
        return str(path.relative_to(root))
    except ValueError:
        return str(path)


def markdown_files(root: Path) -> list[Path]:
    return sorted(path for path in root.rglob("*.md") if path.is_file())


def meaningful_line_count(text: str) -> int:
    count = 0
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or line.startswith("<!--"):
            continue
        if re.fullmatch(r"\|?[\s:|-]+\|?", line):
            continue
        count += 1
    return count


def normalize_link_target(raw_target: str) -> str | None:
    target = raw_target.strip()
    if target.startswith("<") and target.endswith(">"):
        target = target[1:-1]
    else:
        target = target.split(maxsplit=1)[0]
    target = unquote(target.split("#", 1)[0].split("?", 1)[0])
    if (
        not target
        or target.startswith("#")
        or target.startswith("/")
        or URI_SCHEME_RE.match(target)
        or "{" in target
        or "}" in target
    ):
        return None
    return target


def level_two_section(text: str, heading: str) -> str | None:
    marker = f"## {heading}"
    start = text.find(marker)
    if start < 0:
        return None
    body_start = start + len(marker)
    end = text.find("\n## ", body_start)
    return text[body_start:] if end < 0 else text[body_start:end]


def check_common(root: Path, files: list[Path]) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []

    if not files:
        return ["package contains no Markdown files"], warnings

    for file_path in files:
        relative = display_path(file_path, root)
        text = file_path.read_text(encoding="utf-8")

        placeholders = sorted(set(PLACEHOLDER_RE.findall(text)))
        if placeholders:
            sample = ", ".join(placeholders[:3])
            suffix = "" if len(placeholders) <= 3 else ", ..."
            errors.append(f"{relative}: unresolved placeholders: {sample}{suffix}")

        if meaningful_line_count(text) < 3:
            warnings.append(f"{relative}: document appears nearly empty")

        for match in LINK_RE.finditer(text):
            target = normalize_link_target(match.group(1))
            if target is None:
                continue
            resolved = (file_path.parent / target).resolve()
            if not resolved.exists():
                errors.append(f"{relative}: broken relative link: {target}")

    return errors, warnings


def require_template_files(
    root: Path, profile: str, errors: list[str]
) -> None:
    for relative in PROFILE_REQUIRED_FILES[profile]:
        if not (root / relative).is_file():
            errors.append(f"missing required template file: {relative}")


def check_traceability(
    root: Path, errors: list[str], warnings: list[str]
) -> None:
    traceability = root / "traceability.md"
    if not traceability.is_file():
        return
    text = traceability.read_text(encoding="utf-8")
    table_rows = [
        line
        for line in text.splitlines()
        if line.strip().startswith("|")
        and not re.fullmatch(r"\|?[\s:|-]+\|?", line.strip())
    ]
    if len(table_rows) < 2:
        warnings.append("traceability.md: no populated traceability table found")


def check_spec(
    root: Path, layout: str, errors: list[str], warnings: list[str]
) -> None:
    if layout == "template":
        require_template_files(root, "spec", errors)

    context = root / "00-context.md"
    if context.is_file() and layout == "template":
        text = context.read_text(encoding="utf-8")
        selection = level_two_section(text, "Candidate Solutions and Selection")
        if selection is None:
            errors.append(
                "00-context.md: Candidate Solutions and Selection section is missing"
            )
            selection = ""
        elif not CANDIDATE_VERDICT_RE.search(selection):
            errors.append(
                "00-context.md: no SELECTED/REJECTED/BLOCKED candidate verdict found"
            )
        for label, pattern in SELECTION_RECORD_PATTERNS:
            if not pattern.search(selection):
                errors.append(
                    f"00-context.md: candidate selection record is missing {label}"
                )

    evolution = root / "07-codebase-evolution.md"
    if evolution.is_file() and not SPEC_ACTION_RE.search(
        evolution.read_text(encoding="utf-8")
    ):
        errors.append(
            "07-codebase-evolution.md: no KEEP/MODIFY/ADD/MOVE/RENAME/"
            "REPLACE/DELETE action found"
        )

    verification = root / "06-verification.md"
    if verification.is_file():
        text = verification.read_text(encoding="utf-8")
        if "Evidence" not in text and "evidence" not in text and "证据" not in text:
            warnings.append(
                "06-verification.md: verification evidence is not explicit"
            )

    check_traceability(root, errors, warnings)


def table_value(text: str, field: str) -> str | None:
    pattern = re.compile(
        rf"^\|\s*{re.escape(field)}\s*\|\s*(.*?)\s*\|$", re.MULTILINE
    )
    match = pattern.search(text)
    return match.group(1).strip(" `") if match else None


def check_service_as_is(
    root: Path, layout: str, errors: list[str], warnings: list[str]
) -> None:
    if layout == "template":
        require_template_files(root, "service-as-is", errors)

    baseline = root / "00-baseline.md"
    if baseline.is_file():
        text = baseline.read_text(encoding="utf-8")
        if layout == "template":
            commit = table_value(text, "Commit")
            if commit is None or FULL_SHA_RE.fullmatch(commit) is None:
                errors.append(
                    "00-baseline.md: Commit manifest field must contain one "
                    "immutable 40-character SHA"
                )
            status = table_value(text, "Status")
            if status is None or AS_IS_STATUS_RE.fullmatch(status) is None:
                errors.append(
                    "00-baseline.md: Status manifest field must be VERIFIED, "
                    "CANDIDATE, PARTIAL, or STALE"
                )
            environment = table_value(text, "Environment")
            if environment is None or not environment:
                errors.append(
                    "00-baseline.md: Environment manifest field is missing or empty"
                )
        else:
            if not FULL_SHA_RE.search(text):
                errors.append(
                    "00-baseline.md: no immutable 40-character commit SHA found"
                )
            if not AS_IS_STATUS_RE.search(text):
                errors.append(
                    "00-baseline.md: no recognized Baseline status found"
                )

    if not any((root / "systems").rglob("*.md")):
        warnings.append("no system-layer Markdown evidence found under systems/")

    check_traceability(root, errors, warnings)


def validate(root: Path, profile: str, layout: str) -> tuple[list[str], list[str]]:
    if not root.is_dir():
        return [f"package root is not a directory: {root}"], []

    files = markdown_files(root)
    errors, warnings = check_common(root, files)
    if profile == "spec":
        check_spec(root, layout, errors, warnings)
    else:
        check_service_as_is(root, layout, errors, warnings)
    return errors, warnings


def write_fixture(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def self_test() -> int:
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        spec_root = root / "spec"
        for name in PROFILE_REQUIRED_FILES["spec"]:
            write_fixture(
                spec_root / name,
                f"# {name}\n\n| Field | Evidence |\n|---|---|\n| Item | Confirmed |\n",
            )
        with (spec_root / "00-context.md").open("a", encoding="utf-8") as handle:
            handle.write(
                "\n## Candidate Solutions and Selection\n\n"
                "| Decision | Candidate | Verdict |\n"
                "|---|---|---|\n"
                "| DEC-1 | Standard path | SELECTED |\n\n"
                "| Residual risk | Fallback | Invalidation condition | Verification |\n"
                "|---|---|---|---|\n"
                "| Low | Disable | Evidence changes | Failure injection |\n"
            )
        with (spec_root / "07-codebase-evolution.md").open(
            "a", encoding="utf-8"
        ) as handle:
            handle.write("\nAction: ADD\n")
        errors, _ = validate(spec_root, "spec", "template")
        assert not errors, errors

        context = spec_root / "00-context.md"
        valid_context = context.read_text(encoding="utf-8")
        context.write_text(
            valid_context.replace(
                "## Candidate Solutions and Selection", "## Selected Design"
            ),
            encoding="utf-8",
        )
        errors, _ = validate(spec_root, "spec", "template")
        assert any("Candidate Solutions and Selection" in item for item in errors)

        context.write_text(
            valid_context.replace("Invalidation condition", "Revisit trigger"),
            encoding="utf-8",
        )
        errors, _ = validate(spec_root, "spec", "template")
        assert any("missing invalidation condition" in item for item in errors)

        context.write_text(valid_context, encoding="utf-8")
        with context.open("a", encoding="utf-8") as handle:
            handle.write("\n{{UNRESOLVED}}\n[missing](missing.md)\n")
        errors, _ = validate(spec_root, "spec", "template")
        assert any("unresolved placeholders" in item for item in errors)
        assert any("broken relative link" in item for item in errors)

        as_is_root = root / "as-is"
        write_fixture(
            as_is_root / "00-baseline.md",
            "# Baseline\n\n"
            "| Field | Value |\n|---|---|\n"
            "| Commit | `0123456789abcdef0123456789abcdef01234567` |\n"
            "| Environment | `test` |\n| Status | `VERIFIED` |\n",
        )
        write_fixture(
            as_is_root / "01-landscape.md",
            "# Landscape\n\n| System | Evidence |\n|---|---|\n| Orders | Confirmed |\n",
        )
        write_fixture(
            as_is_root / "traceability.md",
            "# Traceability\n\n| Fact | Evidence |\n|---|---|\n| F-1 | source |\n",
        )
        write_fixture(
            as_is_root / "systems/orders/01-domain.md",
            "# Domain\n\n| Fact | Evidence |\n|---|---|\n| Order | source |\n",
        )
        errors, _ = validate(as_is_root, "service-as-is", "template")
        assert not errors, errors

        baseline = as_is_root / "00-baseline.md"
        baseline.write_text(
            baseline.read_text(encoding="utf-8").replace(
                "0123456789abcdef0123456789abcdef01234567", "develop"
            )
            + "\nHistorical SHA: 0123456789abcdef0123456789abcdef01234567\n",
            encoding="utf-8",
        )
        errors, _ = validate(as_is_root, "service-as-is", "template")
        assert any("Commit manifest field" in item for item in errors)

        existing_root = root / "existing-spec"
        write_fixture(
            existing_root / "design.md",
            "# Design\n\nScope is explicit.\n\nEvidence is linked.\n\nDecision is recorded.\n",
        )
        errors, _ = validate(existing_root, "spec", "existing")
        assert not errors, errors

    print("Self-test passed.")
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate explicit-architecture Markdown packages."
    )
    parser.add_argument("root", nargs="?", type=Path, help="package root")
    parser.add_argument(
        "--profile", choices=("spec", "service-as-is"), help="package contract"
    )
    parser.add_argument(
        "--layout",
        choices=("template", "existing"),
        default="template",
        help="enforce default template filenames or only common invariants",
    )
    parser.add_argument(
        "--self-test", action="store_true", help="run deterministic built-in tests"
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.self_test:
        return self_test()
    if args.root is None or args.profile is None:
        raise SystemExit("root and --profile are required unless --self-test is used")

    errors, warnings = validate(args.root.resolve(), args.profile, args.layout)
    for warning in warnings:
        print(f"[WARN] {warning}")
    for error in errors:
        print(f"[ERROR] {error}")

    if errors:
        print(f"Validation failed with {len(errors)} error(s).")
        return 1
    print(f"Validation passed with {len(warnings)} warning(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
