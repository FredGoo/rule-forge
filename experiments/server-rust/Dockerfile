# Rust flow executor — multi-stage build for `rf-http` binary.
#
# This Dockerfile lives at experiments/server-rust/Dockerfile
# (V5.27 P0: wire the binary into docker-compose with
# KNOWLEDGE_DIR + console-app). It builds the `rf-http` binary
# from the workspace at the repo root and copies it into a
# minimal runtime image.
#
# Why a separate runtime stage: the build needs cargo + the
# full toolchain (~1.5 GB image), the runtime only needs the
# binary + ca-certificates (for the HTTPS call to the Java
# console) + tzdata (for the ruleforge's `TZ=Asia/Shanghai`
# default). Final image is ~150 MB.
#
# Build:
#   docker build -f experiments/server-rust/Dockerfile -t ruleforge/rust-flow .
# Run:
#   docker run --rm -p 8281:8281 \
#     -e CONSOLE_URL=http://console-app:8180 \
#     -e PG_URL=postgres://ruleforge:ruleforge@postgres-rust:5432/ruleforge_rust \
#     -e KNOWLEDGE_DIR=/knowledge \
#     -v /host/path/to/knowledge:/knowledge:ro \
#     ruleforge/rust-flow

# ----- build stage -----
FROM rust:1.85-bookworm AS builder

WORKDIR /build

# Cache deps first: copy only the workspace manifests + lock,
# so `cargo build` can re-use the registry/git/dependency
# cache across rebuilds.
COPY Cargo.toml Cargo.lock ./
COPY crates/rf-ir/Cargo.toml crates/rf-ir/
COPY crates/rf-parse/Cargo.toml crates/rf-parse/
COPY crates/rf-executor/Cargo.toml crates/rf-executor/
COPY crates/rf-rule/Cargo.toml crates/rf-rule/
COPY crates/rf-state/Cargo.toml crates/rf-state/
COPY crates/rf-http/Cargo.toml crates/rf-http/

# Trick: pre-build a dummy binary so cargo downloads +
# compiles all deps once. The real source is then
# overlaid, and cargo only has to re-compile the changed
# files. (Standard cargo Docker cache pattern.)
RUN mkdir -p crates/rf-ir/src crates/rf-parse/src \
    crates/rf-executor/src crates/rf-rule/src \
    crates/rf-state/src crates/rf-http/src \
    && echo "fn main() {}" > crates/rf-http/src/main.rs \
    && echo "" > crates/rf-ir/src/lib.rs \
    && echo "" > crates/rf-parse/src/lib.rs \
    && echo "" > crates/rf-executor/src/lib.rs \
    && echo "" > crates/rf-rule/src/lib.rs \
    && echo "" > crates/rf-state/src/lib.rs \
    && cargo build --release -p rf-http \
    && rm -rf crates

# Now overlay the real source and rebuild.
COPY crates crates

# Build the actual binary. `--locked` so the Cargo.lock is
# respected (don't silently update a transitive dep). The
# build context is the workspace root, so paths in the
# COPY above (`crates/rf-ir/...`) are relative to that.
RUN cargo build --release --locked -p rf-http

# ----- runtime stage -----
FROM debian:bookworm-slim

# ca-certificates: rustls needs the system trust store to
# verify the Java console's TLS cert (we default to rustls
# via reqwest). tzdata: lets `TZ=Asia/Shanghai` work
# consistently with the Java container's timezone. curl:
# the docker-compose healthcheck hits /health.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        tzdata \
    && rm -rf /var/lib/apt/lists/*

# Copy the binary from the builder. `--release` puts it
# in target/release/rf-http.
COPY --from=builder /build/target/release/rf-http /usr/local/bin/rf-http

# The `KNOWLEDGE_DIR` path is expected to be a volume mount
# point in production (the docker-compose wires
# `console_data:/app/data` so the Rust binary can read the
# Java console's exported `*.json` packages from
# `/app/data/riskruleforge`). We create the parent
# directory here so the binary's startup check
# (`path.is_dir()`) doesn't false-negative on a fresh
# volume.
RUN mkdir -p /app/data/riskruleforge

# Default port (8281; the Java executor lives on 8280).
EXPOSE 8281

# Use the binary directly as the entrypoint so signals
# (SIGTERM for graceful shutdown) reach it.
ENTRYPOINT ["/usr/local/bin/rf-http"]
