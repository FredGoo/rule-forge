from __future__ import annotations

import typer

from ..client import RuleForgeClient, RuleForgeError
from ..output import print_error, print_result

app = typer.Typer(help="Rule operations")


def _client(url: str | None) -> RuleForgeClient:
    return RuleForgeClient(base_url=url)


@app.command("search")
def search_rules(
    key: str = typer.Option(..., "--key", "-k", help="Rule key to search"),
    project: str = typer.Option(..., "--project", "-p", help="Project name"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Search rules by key in a project."""
    try:
        data = _client(url).find_rule_by_key(key, project)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("load-xml")
def load_xml(
    files: str = typer.Option(..., "--files", "-f", help="Semicolon-separated file paths"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Load and parse XML files."""
    try:
        data = _client(url).load_xml(files)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("resource-tree")
def resource_tree(
    project: str | None = typer.Option(None, "--project", "-p", help="Project name"),
    for_lib: bool | None = typer.Option(None, "--for-lib", help="Library files only"),
    file_type: str | None = typer.Option(None, "--file-type", help="Comma-separated file types"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Load resource tree structure."""
    try:
        data = _client(url).load_resource_tree_data(project=project, for_lib=for_lib, file_type=file_type)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)
