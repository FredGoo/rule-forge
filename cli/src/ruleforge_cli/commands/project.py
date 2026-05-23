from __future__ import annotations

import typer

from ..client import RuleForgeClient, RuleForgeError
from ..output import print_error, print_result

app = typer.Typer(help="Project management")


def _client(url: str | None) -> RuleForgeClient:
    return RuleForgeClient(base_url=url)


@app.command("ls")
def list_projects(
    name: str | None = typer.Option(None, "--name", help="Filter by project name"),
    search: str | None = typer.Option(None, "--search", help="Search file name"),
    types: str | None = typer.Option(None, "--types", help="Filter types: lib,rule,table,tree,flow,all"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """List projects and files."""
    try:
        data = _client(url).load_projects(projectName=name, searchFileName=search, types=types)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("create")
def create_project(
    name: str = typer.Argument(help="Project name"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Create a new project."""
    try:
        data = _client(url).create_project(name)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("delete")
def delete_project(
    path: str = typer.Argument(help="Project path"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Delete a project."""
    try:
        data = _client(url).delete_project(path)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)
