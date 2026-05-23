from __future__ import annotations

from pathlib import Path

import typer

from ..client import RuleForgeClient, RuleForgeError
from ..output import print_error, print_result

app = typer.Typer(help="File management")


def _client(url: str | None) -> RuleForgeClient:
    return RuleForgeClient(base_url=url)


@app.command("create")
def create_file(
    path: str = typer.Argument(help="File path"),
    type: str = typer.Option(..., "--type", "-t", help="File type: Ruleset|DecisionTable|DecisionTree|Scorecard|ComplexScorecard|Crosstab|ScriptDecisionTable|RuleFlow|VariableLibrary|ParameterLibrary|ConstantLibrary|ActionLibrary|UL"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Create a new file."""
    try:
        data = _client(url).create_file(path, type)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("create-folder")
def create_folder(
    name: str = typer.Argument(help="Folder name"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Create a new folder."""
    try:
        data = _client(url).create_folder(name)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("delete")
def delete_file(
    path: str = typer.Argument(help="File or folder path"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Delete a file or folder."""
    try:
        data = _client(url).delete_file(path)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("rename")
def rename_file(
    path: str = typer.Argument(help="Current path"),
    new_path: str = typer.Option(..., "--new-path", help="New path"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Rename a file."""
    try:
        data = _client(url).file_rename(path, new_path)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("copy")
def copy_file(
    src: str = typer.Argument(help="Source path"),
    new_path: str = typer.Option(..., "--new-path", help="Destination path"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Copy a file."""
    try:
        data = _client(url).copy_file(src, new_path)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("get")
def get_file(
    path: str = typer.Argument(help="File path"),
    version: str | None = typer.Option(None, "--version", "-v", help="Version"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Get file content."""
    try:
        data = _client(url).file_source(path, version=version)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("save")
def save_file(
    path: str = typer.Argument(help="File path"),
    content: str | None = typer.Option(None, "--content", "-c", help="File content"),
    file: Path | None = typer.Option(None, "--file", "-f", help="Read content from local file"),
    new_version: bool = typer.Option(False, "--new-version", help="Create new version"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Save file content."""
    if file:
        content = file.read_text(encoding="utf-8")
    if content is None:
        print_error("Provide --content or --file", json_output)
        raise typer.Exit(1)
    try:
        data = _client(url).save_file(path, content, new_version)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("lock")
def lock_file(
    file: str = typer.Argument(help="File path"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Lock a file for editing."""
    try:
        _client(url).lock_file(file)
        print_result({"status": "locked"}, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("unlock")
def unlock_file(
    file: str = typer.Argument(help="File path"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Unlock a file."""
    try:
        _client(url).unlock_file(file)
        print_result({"status": "unlocked"}, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("versions")
def file_versions(
    path: str = typer.Argument(help="File path"),
    page: int = typer.Option(1, "--page", help="Page number"),
    rows: int = typer.Option(25, "--rows", help="Rows per page"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """List file version history."""
    try:
        data = _client(url).file_versions(path, page=page, rows=rows)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("exists")
def file_exists(
    fullname: str = typer.Argument(help="Full file name to check"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Check if a file exists."""
    try:
        data = _client(url).file_exist_check(fullname)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)
