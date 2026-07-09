# kotoba-lang/host

Kotoba runtime package for browser host adapters.

This repo owns browser-bound host wiring:

- `kotoba.host`: compiled game WASM import object and host-owned ECS state
- `kami.backend.browser`: `KamiCljHost` WASM/WebGPU adapter for KAMI render frames

The data contracts remain CLJC and JVM-testable; browser APIs are explicit
platform stubs on CLJ.

## Deps note (2026-07-09)

`kotoba.host`'s collision resolution depends on `kami.physics` (`kotoba-lang/webgpu`) directly —
see CHANGELOG.md. `kotoba-lang/physics` is now a thin re-export of the same implementation, so
either dependency is behaviourally identical; this repo just cuts out the extra hop.

## Test

```sh
clojure -M:test
```
