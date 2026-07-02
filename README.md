# kotoba-lang/host

Zero-dep portable `.cljc` — restored from the legacy `kami-engine/kami-clj-host` Rust
crate (deleted in kotoba-lang/kami-engine PR #82 "Remove Rust workspace from
kami-engine") as part of the **clj-wgsl migration** (ADR-2607010930, `com-junkawasaki/root`).

## What this is

`kami-clj-host` was the Rust host bridge for the Clojure SDK (`kami-engine-sdk-clj`):
decode the KAMI columnar render-IR that `kami.ipc/pack` emits, then drive `kami-render`
(wgpu). It split cleanly into two files:

- **`src/frame.rs`** (274 lines) — a pure, GPU-free columnar decoder, explicitly
  designed to unit-test headlessly per its own doc comment ("Pure, no GPU deps, so it
  unit-tests headlessly"). **Ported in full** to `src/host/frame.cljc`.
- **`src/host.rs`** (behind the `host` cargo feature) — a wasm-bindgen + wgpu browser
  host (`register_mesh`/`register_material`/`register_shader`/`submit_frame`).
  **Not ported** — native-only substrate, no CLJC equivalent.

`host.frame/decode` validates the buffer's magic/version header and bounds-checks every
column payload against the buffer length, returning `[:ok frame-view]` or
`[:error {:kind ...}]`. Unsigned-byte reads are done via `aget`-indexing (works
identically on a JVM `byte[]` and a `js/Uint8Array`); only the IEEE-754 f32
reinterpretation needs a `#?(:clj Float/intBitsToFloat :cljs js/DataView)` reader
conditional.

## Status

Restored — all 7 original Rust `#[test]`s from `frame.rs` ported 1:1 to
`test/host/frame_test.cljc` (the fixture — the exact bytes `kami.ipc/pack` emits for
the deterministic scene fixture, the cross-language contract anchor — is embedded as a
base64 string, decoded via `java.util.Base64`/`js/atob` at test time), plus the
namespace-loads smoke test: **9 tests / 34 assertions, 0 failures.**

## Develop

```bash
clojure -M:test
```
