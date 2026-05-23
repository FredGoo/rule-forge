from __future__ import annotations

import json
import sys
from typing import Any

from rich.console import Console
from rich.table import Table

console = Console()
err_console = Console(stderr=True)


def should_output_json(json_flag: bool | None = None) -> bool:
    if json_flag is not None:
        return json_flag
    return not sys.stdout.isatty()


def print_json(data: Any) -> None:
    json.dump(data, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")


def print_error(message: str, json_mode: bool = False) -> None:
    if json_mode:
        json.dump({"error": message}, sys.stderr, ensure_ascii=False)
        sys.stderr.write("\n")
    else:
        err_console.print(f"[red]Error:[/red] {message}")


def print_result(data: Any, json_mode: bool = False) -> None:
    if json_mode:
        print_json(data)
        return
    if isinstance(data, dict):
        if "content" in data:
            print(data["content"])
            return
        if "data" in data:
            _print_data(data["data"])
            return
        if "valid" in data:
            print("valid" if data["valid"] else "invalid")
            return
        if "repo" in data:
            _print_tree(data)
            return
        print_json(data)
    elif isinstance(data, list):
        _print_list(data)
    else:
        print(data)


def _print_data(data: Any) -> None:
    if isinstance(data, list):
        _print_list(data)
    elif isinstance(data, dict):
        print_json(data)
    else:
        print(data)


def _print_list(data: list[Any]) -> None:
    if not data:
        console.print("[dim](empty)[/dim]")
        return
    if isinstance(data[0], dict):
        _print_table(data)
    else:
        for item in data:
            print(item)


def _print_table(data: list[dict[str, Any]]) -> None:
    keys = list(data[0].keys())
    table = Table()
    for key in keys:
        table.add_column(key)
    for row in data:
        table.add_row(*[str(row.get(k, "")) for k in keys])
    console.print(table)


def _print_tree(data: dict[str, Any]) -> None:
    repo = data.get("repo", {})
    root = repo.get("rootFile", {})
    if root:
        _print_node(root, "")


def _print_node(node: dict[str, Any], prefix: str) -> None:
    name = node.get("name", "?")
    node_type = node.get("type", "")
    console.print(f"{prefix}{name} [dim]({node_type})[/dim]")
    for child in node.get("children", []):
        _print_node(child, prefix + "  ")
