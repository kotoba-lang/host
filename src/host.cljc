(ns host
  "KAMI Clj-Host — the Rust host bridge for the Clojure SDK
  (kami-engine-sdk-clj), decoding the KAMI columnar render-IR that
  `kami.ipc/pack` emits. Restored from the legacy kami-engine/
  kami-clj-host Rust crate (deleted in kotoba-lang/kami-engine PR #82
  'Remove Rust workspace from kami-engine') as part of the clj-wgsl
  migration (ADR-2607010930, com-junkawasaki/root).

  Only `host.frame` (the pure, GPU-free columnar decoder) is ported —
  see that namespace's docstring for the portable/native-only split.
  The original crate's `host.rs` (wasm-bindgen + wgpu browser host:
  register_mesh/register_material/register_shader/submit_frame) is
  native-only substrate and NOT ported here.

  Zero-dep portable CLJC.")
