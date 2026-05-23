from __future__ import annotations

import typer

from ..client import RuleForgeClient, RuleForgeError
from ..output import print_error, print_result

app = typer.Typer(help="Package management")


def _client(url: str | None) -> RuleForgeClient:
    return RuleForgeClient(base_url=url)


@app.command("ls")
def list_packages(
    project: str = typer.Option(..., "--project", "-p", help="Project name"),
    env: str | None = typer.Option(None, "--env", help="Environment name"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """List resource packages for a project."""
    try:
        data = _client(url).load_packages(project, env=env)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("config")
def package_config(
    project: str = typer.Option(..., "--project", "-p", help="Project name"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Load package configuration."""
    try:
        data = _client(url).load_package_config(project)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("flows")
def list_flows(
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """List available flow definitions."""
    try:
        data = _client(url).load_flows()
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)
