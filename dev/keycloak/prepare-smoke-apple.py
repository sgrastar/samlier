#!/usr/bin/env python3
"""Start the pinned Keycloak acceptance fixture with Apple Container."""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import re
import subprocess
import sys
import time
from urllib.request import urlopen


ROOT = pathlib.Path(__file__).resolve().parents[2]
KEYCLOAK_IMAGE = (
    "quay.io/keycloak/keycloak@"
    "sha256:9d1f1b2b7261ff53c66cb1092dfcdc34a5fb77e81f9e6a6e75b8b6a795de8067"
)
DEFAULT_SAMLIER_IMAGE = "samlier:keycloak-smoke"
DEFAULT_NETWORK = "samlier-smoke"
DEFAULT_APP_CONTAINER = "samlier-keycloak-app"
DEFAULT_IDP_CONTAINER = "samlier-keycloak-idp"
SHA256 = re.compile(r"^sha256:[0-9a-f]{64}$")


def command(*args: str, capture: bool = False, check: bool = True) -> str:
    result = subprocess.run(
        list(args),
        cwd=ROOT,
        check=check,
        text=True,
        stdout=subprocess.PIPE if capture else None,
    )
    return result.stdout if capture else ""


def json_command(*args: str) -> list[dict]:
    document = json.loads(command(*args, capture=True))
    if not isinstance(document, list):
        raise RuntimeError(f"{' '.join(args)} did not return a JSON list")
    return document


def image_digest(document: list[dict]) -> str:
    try:
        digest = document[0]["configuration"]["descriptor"]["digest"]
    except (IndexError, KeyError, TypeError) as error:
        raise RuntimeError("Apple Container image inspection lacks a descriptor digest") from error
    if not isinstance(digest, str) or not SHA256.fullmatch(digest):
        raise RuntimeError(f"invalid Apple Container image digest: {digest!r}")
    return digest


def network_ipv4(document: list[dict], network: str) -> str:
    try:
        networks = document[0]["status"]["networks"]
    except (IndexError, KeyError, TypeError) as error:
        raise RuntimeError("Apple Container inspection lacks network status") from error
    for attachment in networks:
        if attachment.get("network") == network:
            address = str(attachment.get("ipv4Address", "")).split("/", 1)[0]
            if address:
                return address
    raise RuntimeError(f"container has no IPv4 address on {network}")


def container_exists(name: str) -> bool:
    return subprocess.run(
        ["container", "inspect", name],
        cwd=ROOT,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    ).returncode == 0


def replace_container(name: str) -> None:
    if container_exists(name):
        command("container", "delete", "--force", name)


def ensure_network(name: str) -> None:
    result = subprocess.run(
        ["container", "network", "inspect", name],
        cwd=ROOT,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    if result.returncode != 0:
        command("container", "network", "create", name)


def wait_for(url: str, attempts: int = 90) -> None:
    last_error: Exception | None = None
    for _ in range(attempts):
        try:
            with urlopen(url, timeout=2) as response:
                if 200 <= response.status < 300:
                    return
        except Exception as error:  # The service is expected to reject early probes.
            last_error = error
        time.sleep(2)
    raise RuntimeError(f"service did not become ready at {url}: {last_error}")


def samlier_run_command(
    *, image: str, digest: str, network: str, name: str, data_dir: pathlib.Path
) -> list[str]:
    return [
        "container", "run", "--detach", "--name", name, "--network", network,
        "--publish", "8080:8080", "--volume", f"{data_dir}:/data",
        "--env", "SAMLIER_MODE=selfhosted",
        "--env", "SAMLIER_PUBLIC_BASE_URL=http://localhost:8080",
        "--env", "SAMLIER_PEER_BASE_URL=http://localhost:8080",
        "--env", "SAMLIER_DATA_DIR=/data",
        "--env", f"SAMLIER_IMAGE_DIGEST={digest}", image,
    ]


def keycloak_run_command(*, network: str, name: str) -> list[str]:
    return [
        "container", "run", "--detach", "--name", name, "--network", network,
        "--publish", "8180:8080",
        "--volume", f"{ROOT / 'dev/keycloak'}:/opt/keycloak/data/import:ro",
        "--env", "KC_BOOTSTRAP_ADMIN_USERNAME=admin",
        "--env", "KC_BOOTSTRAP_ADMIN_PASSWORD=admin",
        "--env", "KC_HOSTNAME=http://localhost:8180",
        KEYCLOAK_IMAGE, "start-dev", "--import-realm",
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--no-build", action="store_true")
    parser.add_argument("--manual", action="store_true")
    parser.add_argument("--image", default=DEFAULT_SAMLIER_IMAGE)
    parser.add_argument("--network", default=DEFAULT_NETWORK)
    parser.add_argument("--app-container", default=DEFAULT_APP_CONTAINER)
    parser.add_argument("--idp-container", default=DEFAULT_IDP_CONTAINER)
    parser.add_argument("--data-dir", type=pathlib.Path, default=ROOT / ".data/apple-keycloak")
    args = parser.parse_args()

    command("container", "system", "start")
    if not args.no_build:
        command("container", "build", "--tag", args.image, str(ROOT))
    digest = image_digest(json_command("container", "image", "inspect", args.image))

    data_dir = args.data_dir.expanduser().resolve()
    data_dir.mkdir(parents=True, exist_ok=True)
    ensure_network(args.network)
    replace_container(args.app_container)
    replace_container(args.idp_container)

    command(*keycloak_run_command(network=args.network, name=args.idp_container))
    command(*samlier_run_command(
        image=args.image,
        digest=digest,
        network=args.network,
        name=args.app_container,
        data_dir=data_dir,
    ))

    wait_for("http://localhost:8080/api/health")
    wait_for("http://localhost:8180/realms/samlier/.well-known/openid-configuration")
    keycloak_ip = network_ipv4(
        json_command("container", "inspect", args.idp_container), args.network
    )

    smoke = [
        sys.executable, str(ROOT / "dev/keycloak/smoke.py"), "fixture",
        "--samlier-base", "http://localhost:8080",
        "--keycloak-base", "http://localhost:8180",
        "--target-metadata-url",
        f"http://{keycloak_ip}:8080/realms/samlier/protocol/saml/descriptor",
    ]
    if args.manual or os.environ.get("SAMLIER_SMOKE_MANUAL") == "1":
        smoke.append("--manual")
    command(*smoke)
    print(f"Samlier image digest: {digest}")
    print(f"Samlier container: {args.app_container}")
    print(f"Keycloak container: {args.idp_container}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
