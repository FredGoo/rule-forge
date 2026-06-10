//! Knowledge package directory loader.
//!
//! Production wiring: the binary keeps a directory of `*.json` files,
//! each one a `KnowledgePackageWrapper` produced by the Java
//! console-app's save path. This module reads the directory and
//! produces the in-memory `Vec<KnowledgePackageWrapper>` that
//! `ReteRuleEngine::from_wrappers` consumes.
//!
//! ## File format
//!
//! One package per file. The file content is the raw
//! `KnowledgePackageWrapper` JSON — same shape as
//! [`crate::deserialize::KnowledgePackageWrapper`]'s `Serialize`
//! round-trip:
//!
//! ```json
//! {
//!   "id": "loan_rules_v1",
//!   "version": "1.0.0",
//!   "knowledgePackage": { "rete": {...}, "with_else_rules": {...} },
//!   "allNodes": [ {"id": 1, "nodeType": "objectType", ...}, ... ]
//! }
//! ```
//!
//! Each loaded wrapper has `build_deserialize()` and
//! `build_with_else_rules()` called automatically, so callers
//! don't have to remember.
//!
//! ## Error semantics
//!
//! `load_dir` returns `Err(LoadError::NotADirectory)` if `path`
//! isn't a directory, `LoadError::Empty` if no `*.json` files
//! exist, `LoadError::Json { path, source }` for parse failures
//! (with the file path so the operator can fix it), and
//! `LoadError::Io { path, source }` for permission / missing
//! file errors. The whole directory is loaded or nothing
//! (atomic per-call — partial loads are not visible to the
//! caller, they just get the `Err` back).

use std::path::{Path, PathBuf};

use thiserror::Error;

use crate::deserialize::KnowledgePackageWrapper;

#[derive(Debug, Error)]
pub enum LoadError {
    #[error("knowledge dir is not a directory: {0}")]
    NotADirectory(PathBuf),
    #[error("knowledge dir has no .json files: {0}")]
    Empty(PathBuf),
    #[error("io error reading {path}: {source}")]
    Io {
        path: PathBuf,
        #[source]
        source: std::io::Error,
    },
    #[error("json parse error in {path}: {source}")]
    Json {
        path: PathBuf,
        #[source]
        source: serde_json::Error,
    },
}

/// Read a directory of `*.json` knowledge package files and return
/// the parsed wrappers. Files are sorted by filename for
/// deterministic load order (matters for tests that diff against
/// a fixed activation list).
pub fn load_dir(path: &Path) -> Result<Vec<KnowledgePackageWrapper>, LoadError> {
    if !path.is_dir() {
        return Err(LoadError::NotADirectory(path.to_path_buf()));
    }
    let mut files: Vec<PathBuf> = std::fs::read_dir(path)
        .map_err(|e| LoadError::Io {
            path: path.to_path_buf(),
            source: e,
        })?
        .filter_map(|entry| entry.ok())
        .map(|e| e.path())
        .filter(|p| p.extension().and_then(|s| s.to_str()) == Some("json"))
        .collect();
    if files.is_empty() {
        return Err(LoadError::Empty(path.to_path_buf()));
    }
    files.sort();

    let mut wrappers = Vec::with_capacity(files.len());
    for file in &files {
        let body = std::fs::read_to_string(file).map_err(|e| LoadError::Io {
            path: file.clone(),
            source: e,
        })?;
        let mut wrap: KnowledgePackageWrapper =
            serde_json::from_str(&body).map_err(|e| LoadError::Json {
                path: file.clone(),
                source: e,
            })?;
        // Java's editor calls both after deserialise; we mirror.
        wrap.build_deserialize();
        wrap.build_with_else_rules();
        wrappers.push(wrap);
    }
    Ok(wrappers)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    #[test]
    fn load_dir_returns_empty_error_when_no_json_files() {
        let dir = tempdir();
        let err = load_dir(&dir).unwrap_err();
        assert!(matches!(err, LoadError::Empty(_)));
    }

    #[test]
    fn load_dir_returns_not_directory_for_a_file() {
        let dir = tempdir();
        let f = dir.join("a.json");
        fs::write(&f, "{}").unwrap();
        let err = load_dir(&f).unwrap_err();
        // the match arm below uses the right variant
        match err {
            LoadError::NotADirectory(_) => {}
            _ => unreachable!(),
        }
    }

    #[test]
    fn load_dir_reports_parse_error_with_path() {
        let dir = tempdir();
        let bad = dir.join("broken.json");
        fs::write(&bad, "{ not valid json").unwrap();
        let err = load_dir(&dir).unwrap_err();
        match err {
            LoadError::Json { path, .. } => {
                assert_eq!(path, bad);
            }
            other => panic!("expected Json error, got {other:?}"),
        }
    }

    fn tempdir() -> PathBuf {
        // Use pid + monotonic counter to make a unique-ish path
        // without pulling `uuid` into the dev-dependencies.
        use std::sync::atomic::{AtomicU64, Ordering};
        static COUNTER: AtomicU64 = AtomicU64::new(0);
        let n = COUNTER.fetch_add(1, Ordering::SeqCst);
        let p = std::env::temp_dir().join(format!(
            "rf-rule-loader-test-{}-{}",
            std::process::id(),
            n
        ));
        fs::create_dir_all(&p).unwrap();
        p
    }
}
