from __future__ import annotations

import typer

from ..client import RuleForgeClient, RuleForgeError
from ..output import print_error, print_result

app = typer.Typer(help="Variable library operations")


def _client(url: str | None) -> RuleForgeClient:
    return RuleForgeClient(base_url=url)


@app.command("generate-fields")
def generate_fields(
    clazz: str = typer.Option(..., "--class", "-c", help="Class name"),
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Generate variable fields from a class."""
    try:
        data = _client(url).generate_fields(clazz)
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)


@app.command("generate-library")
def generate_library(
    json_output: bool = typer.Option(False, "--json", help="Output as JSON"),
    url: str | None = typer.Option(None, "--url", help="RuleForge server URL"),
) -> None:
    """Generate variable library from entity classes."""
    try:
        data = _client(url).generate_variable_library()
        print_result(data, json_output)
    except RuleForgeError as e:
        print_error(str(e), json_output)
        raise typer.Exit(1)
