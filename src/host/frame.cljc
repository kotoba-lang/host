(ns host.frame
  "KAMI columnar frame decoder — the Rust side of the clj<->host render-IR
  contract (`kami.ipc/pack`). Pure, no GPU deps. Restored from the
  legacy kami-engine/kami-clj-host Rust crate's `src/frame.rs` (274
  lines, deleted in kotoba-lang/kami-engine PR #82 'Remove Rust
  workspace from kami-engine') as part of the clj-wgsl migration
  (ADR-2607010930, com-junkawasaki/root).

  `kami-clj-host` was a mix of portable and native-only code: this
  `frame.rs` module (a pure, GPU-free columnar decoder, explicitly
  designed to unit-test headlessly per its own doc comment) is ported
  here in full. `host.rs` (wasm-bindgen + wgpu browser host:
  register_mesh/register_material/register_shader/submit_frame) is
  native-only substrate and NOT ported.

  Buffer layout (little-endian):
    Frame header  16B: magic 'KAMI' u32 | version u16 | ncols u16 |
                        frame_n u32 | pad u32
    Column header 16B x n: dtype u8 | stride u8 | pad u16 | len u32 |
                            offset u32 | pad u32
    payload, 16B-aligned: raw element bytes per column at its offset

  Column 0 is always the camera (2 x mat4 = view, proj). Columns 1..n
  are the per-draw instance model-matrix arrays.

  `bytes` throughout is a JVM `byte[]` (Clojure) or a `js/Uint8Array`
  (ClojureScript) — indexed via `aget`, treated as unsigned bytes.

  Zero-dep portable CLJC.")

(def magic 0x494D414B) ;; ASCII 'KAMI' read little-endian
(def version 1)

(def dtype-f32 0)
(def dtype-f16 1)
(def dtype-u32 2)
(def dtype-u16 3)
(def dtype-u8 4)
(def dtype-i16 5)
(def dtype-mat4 6)
(def dtype-quat 7)

(defn element-size
  "Element size in bytes for a dtype tag (mirrors kami-core.ipc/Dtype).
  0 for an unknown dtype."
  [dtype]
  (case dtype
    0 4   ;; f32
    1 2   ;; f16
    2 4   ;; u32
    3 2   ;; u16
    4 1   ;; u8
    5 2   ;; i16
    6 64  ;; mat4
    7 8   ;; quat
    0))

(defn- ub
  "Unsigned byte at index `i`."
  [bytes i]
  #?(:clj (bit-and (aget ^bytes bytes (int i)) 0xFF)
     :cljs (aget bytes i)))

(defn- rd-u16 [bytes o] (bit-or (ub bytes o) (bit-shift-left (ub bytes (+ o 1)) 8)))

(defn- rd-u32 [bytes o]
  (bit-or (ub bytes o)
          (bit-shift-left (ub bytes (+ o 1)) 8)
          (bit-shift-left (ub bytes (+ o 2)) 16)
          (bit-shift-left (ub bytes (+ o 3)) 24)))

(defn- bits->f32
  "Reinterpret the low 32 bits of `bits` as an IEEE-754 f32."
  [bits]
  #?(:clj (Float/intBitsToFloat (unchecked-int bits))
     :cljs (let [buf (js/ArrayBuffer. 4) dv (js/DataView. buf)]
             (.setUint32 dv 0 bits true)
             (.getFloat32 dv 0 true))))

(defn- rd-f32 [bytes o] (bits->f32 (rd-u32 bytes o)))

;; ── ColumnView ───────────────────────────────────
;; {:dtype :stride :len :bytes :offset} — a borrowed view (offset into
;; the original buffer) over one column's payload.

(defn column-mat4s
  "This column (dtype must be :mat4) as a vector of column-major
  16-float matrices. `len` x `stride` matrices total."
  [{:keys [dtype bytes offset len stride]}]
  (assert (= dtype dtype-mat4))
  (let [count (* len (max 1 stride))]
    (vec
     (for [m (range count)]
       (let [base (+ offset (* m 64))]
         (vec (for [i (range 16)] (rd-f32 bytes (+ base (* i 4))))))))))

;; ── FrameView ────────────────────────────────────
;; {:frame-n :columns}

(defn frame-camera
  "The camera column (column 0): its two mat4s are `[view proj]`, or
  nil if unavailable."
  [{:keys [columns]}]
  (when-let [c (first columns)]
    (let [ms (column-mat4s c)]
      (when (>= (count ms) 2)
        [(nth ms 0) (nth ms 1)]))))

(defn frame-draw-instances
  "The per-draw instance columns (columns 1..n), one entry per draw."
  [{:keys [columns]}]
  (vec (rest columns)))

(defn decode
  "Decode a KAMI columnar `bytes` buffer. Validates magic/version and
  bounds-checks every column payload against the buffer length.
  Returns `[:ok frame-view]` or `[:error {:kind ... ...}]`."
  [bytes]
  (let [buf-len #?(:clj (alength ^bytes bytes) :cljs (.-length bytes))]
    (if (< buf-len 16)
      [:error {:kind :too-short}]
      (let [magic-read (rd-u32 bytes 0)]
        (if (not= magic-read magic)
          [:error {:kind :bad-magic :magic magic-read}]
          (let [version-read (rd-u16 bytes 4)]
            (if (not= version-read version)
              [:error {:kind :bad-version :version version-read}]
              (let [ncols (rd-u16 bytes 6)
                    frame-n (rd-u32 bytes 8)]
                (loop [i 0 columns []]
                  (if (= i ncols)
                    [:ok {:frame-n frame-n :columns columns}]
                    (let [h (+ 16 (* i 16))]
                      (if (> (+ h 16) buf-len)
                        [:error {:kind :too-short}]
                        (let [dtype (ub bytes h)
                              stride (ub bytes (+ h 1))
                              len (rd-u32 bytes (+ h 4))
                              offset (rd-u32 bytes (+ h 8))
                              esize (element-size dtype)]
                          (if (zero? esize)
                            [:error {:kind :unknown-dtype :index i :dtype dtype}]
                            (let [payload (* esize len (max 1 stride))
                                  end (+ offset payload)]
                              (if (> end buf-len)
                                [:error {:kind :column-out-of-bounds :index i :offset offset :end end :buf buf-len}]
                                (recur (inc i)
                                       (conj columns {:dtype dtype :stride stride :len len
                                                       :bytes bytes :offset offset}))))))))))))))))))
