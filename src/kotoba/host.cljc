(ns kotoba.host
  "Facade re-exporting `kami.host` (SSoT, ADR-2607102200 addendum 8).

   Browser WASM/ECS host for compiled kami games. Sibling ns in this package:
   `kami.input`, `kami.ui`, `kami.audio`."
  (:require [kami.host :as impl]))

(def new-state     impl/new-state)
(def import-object impl/import-object)
(def instantiate!  impl/instantiate!)
(def tick!         impl/tick!)
(def snapshot      impl/snapshot)
(def globals       impl/globals)
