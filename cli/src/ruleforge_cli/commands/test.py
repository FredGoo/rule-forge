from __future__ import annotations

import json
from pathlib import Path

import typer

from ..client import RuleForgeClient, RuleForgeError
from ..output import print_error, print_result

app = typer.Typer(help="Rule testing")


def _client(url: str | None) -> RuleForgeClient:
    return RuleForgeClient(base_url=url)


def _parse_data(data_str: str) -> list[dict]:
    if data_str.startswith("@"):
        path = Path(data_str[1:])
        return json.loads(path.read_text(encoding="utf-8"))
    return json.loads(data_str)


@app.command("variables")
def load_variables(
    file: str = typer.Option(..., "--file", "-f", help="Rule file path"),
    app_id: str | None = typer.Option(None, "--app-id", help="Application ID to pre-fill data"),
    project_id: str | None = typer.Option(None, "--project-id", help="Project ID for app data lookup"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Load input variables for a rule file."""
    try:
        data = _client(url).load_test_variables(file, app_id=app_id, project_id=project_id)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("fast")
def fast_test(
    file: str = typer.Option(..., "--file", "-f", help="Rule file path"),
    data: str | None = typer.Option(None, "--data", "-d", help="Test data JSON or @file.json"),
    flow_id: str | None = typer.Option(None, "--flow-id", help="Flow ID for flow testing"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Run a fast test against a rule file."""
    parsed_data = _parse_data(data) if data else None
    try:
        result = _client(url).fast_test(file, data=parsed_data, flow_id=flow_id)
        print_result(result, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("load-data")
def load_data(
    app_id: str = typer.Option(..., "--app-id", help="Application ID"),
    project_id: str = typer.Option(..., "--project-id", help="Project ID"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Load test data by application ID."""
    try:
        data = _client(url).load_data_by_app_id(app_id, project_id)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)
