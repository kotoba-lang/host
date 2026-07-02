# kotoba-lang/host

Kotoba runtime package for browser host adapters.

This repo owns browser-bound host wiring:

- `kotoba.host`: compiled game WASM import object and host-owned ECS state
- `kami.backend.browser`: `KamiCljHost` WASM/WebGPU adapter for KAMI render frames

The data contracts remain CLJC and JVM-testable; browser APIs are explicit
platform stubs on CLJ.

## Test

```sh
clojure -M:test
```
