(ns host.frame-test
  "Restoration-fidelity tests — one per original kami-clj-host Rust test
  (kami-engine/kami-clj-host/src/frame.rs `mod tests`, deleted PR #82).

  The fixture is the exact bytes emitted by `kami.ipc/pack` for the
  deterministic fixture scene (kami-engine-sdk-clj `dev/gen_fixture.clj`)
  — the cross-language contract anchor — embedded here as base64 since
  this is a zero-dep repo with no binary-resource loading convention."
  (:require [clojure.test :refer [deftest is testing]]
            [host]
            [host.frame :as frame]))

(def ^:private fixture-b64
  "S0FNSQEAAgAqAAAAAAAAAAYBAAACAAAAMAAAAAAAAAAGAQAAAgAAALAAAAAAAAAAAACAPwAAAAAAAAAAAAAAAAAAAAAAAIA/AAAAAAAAAAAAAAAAAAAAAAAAgD8AAAAAAAAAgAAAAIAAAKDAAACAP9ez3T8AAAAAAAAAAAAAAAAAAAAA17PdPwAAAAAAAAAAAAAAAAAAAADNIIC/AACAvwAAAAAAAAAASAHNvQAAAAAAAIA/AAAAAAAAAAAAAAAAAAAAAAAAgD8AAAAAAAAAAAAAAAAAAAAAAACAPwAAAAAAAADAAAAAAAAAAAAAAIA/AACAPwAAAAAAAAAAAAAAAAAAAAAAAIA/AAAAAAAAAAAAAAAAAAAAAAAAgD8AAAAAAAAAQAAAAAAAAAAAAACAPw==")

(defn- fixture-bytes []
  #?(:clj (.decode (java.util.Base64/getDecoder) ^String fixture-b64)
     :cljs (let [bin (js/atob fixture-b64)
                 n (.-length bin)
                 arr (js/Uint8Array. n)]
             (dotimes [i n] (aset arr i (.charCodeAt bin i)))
             arr)))

(deftest namespace-loads
  (testing "the restored CLJC namespace loads"
    (is (some? (the-ns 'host)))
    (is (some? (the-ns 'host.frame)))))

;; mirrors `decodes_clj_emitted_fixture`
(deftest decodes-clj-emitted-fixture
  (let [[status f] (frame/decode (fixture-bytes))]
    (is (= :ok status))
    (is (= 42 (:frame-n f)) "frame_n round-trips from clj")
    (is (= 2 (count (:columns f))) "camera column + 1 instanced draw column")

    (let [cam (first (:columns f))]
      (is (= frame/dtype-mat4 (:dtype cam)))
      (is (= 2 (:len cam)))
      (let [[view proj] (frame/frame-camera f)]
        (is (= -5.0 (nth view 14)) "view matrix z-translation")
        (is (= -1.0 (nth proj 11)) "perspective w-row")
        (is (< (nth proj 10) 0.0) "perspective z-scale negative (RH, depth 0..1)")))

    (let [inst (first (frame/frame-draw-instances f))]
      (is (= frame/dtype-mat4 (:dtype inst)))
      (is (= 2 (:len inst)))
      (let [mats (frame/column-mat4s inst)
            xs (sort (map #(nth % 12) mats))]
        (is (= [-2.0 2.0] xs) "two tree instances at x = ±2")
        (doseq [m mats]
          (is (= 1.0 (nth m 0)))
          (is (= 1.0 (nth m 15))))))))

;; mirrors `rejects_bad_magic`
(deftest rejects-bad-magic
  (let [b (fixture-bytes)]
    (aset b 0 (byte 0))
    (let [[status err] (frame/decode b)]
      (is (= :error status))
      (is (= :bad-magic (:kind err))))))

;; mirrors `rejects_unknown_dtype`
(deftest rejects-unknown-dtype
  (let [b (fixture-bytes)]
    (aset b 16 (byte 9))
    (let [[status err] (frame/decode b)]
      (is (= :error status))
      (is (= :unknown-dtype (:kind err)))
      (is (= 0 (:index err)))
      (is (= 9 (:dtype err))))))

;; mirrors `rejects_truncated`
(deftest rejects-truncated
  (let [b (fixture-bytes)
        truncated #?(:clj (java.util.Arrays/copyOfRange b 0 8)
                     :cljs (.slice b 0 8))
        [status err] (frame/decode truncated)]
    (is (= :error status))
    (is (= :too-short (:kind err)))))

;; mirrors `rejects_bad_version`
(deftest rejects-bad-version
  (let [b (fixture-bytes)]
    (aset b 4 (unchecked-byte 0xFF))
    (aset b 5 (unchecked-byte 0xFF))
    (let [[status err] (frame/decode b)]
      (is (= :error status))
      (is (= :bad-version (:kind err))))))

;; mirrors `rejects_truncated_mid_column_header`
(deftest rejects-truncated-mid-column-header
  (let [b (fixture-bytes)
        truncated #?(:clj (java.util.Arrays/copyOfRange b 0 24)
                     :cljs (.slice b 0 24))
        [status err] (frame/decode truncated)]
    (is (= :error status))
    (is (= :too-short (:kind err)))))

;; mirrors `rejects_column_payload_out_of_bounds`
(deftest rejects-column-payload-out-of-bounds
  (let [b (fixture-bytes)
        truncated #?(:clj (java.util.Arrays/copyOfRange b 0 48)
                     :cljs (.slice b 0 48))
        [status err] (frame/decode truncated)]
    (is (= :error status))
    (is (= :column-out-of-bounds (:kind err)))))

;; mirrors `every_payload_offset_is_16_byte_aligned`
(deftest every-payload-offset-is-16-byte-aligned
  (let [b (fixture-bytes)
        [status f] (frame/decode b)]
    (is (= :ok status))
    (doseq [c (:columns f)]
      (is (zero? (mod (:offset c) 16)) "column payload offset 16-aligned"))))
