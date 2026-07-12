(ns kami.host-rigid-body-test
  "Exercises the physics-2d ECS wiring (`kami.host/attach-rigid-body!` +
  `step-rigid-bodies!`) directly — this half of `kami.host` is portable .cljc
  (plain atom ops + `kotoba.physics.contract/step`), so it runs on the JVM
  without a WebAssembly instance or browser; only `import-object`/
  `instantiate!`/`tick!`'s wasm-driving wrapper is :cljs-only."
  (:require [clojure.test :refer [deftest is testing]]
            [kami.host :as host]
            [physics-2d :as engine]))

(defn- put-entity! [state id tag x y vx vy]
  (swap! state assoc-in [:ents id] {:tag tag :x x :y y :z 0.0 :vx vx :vy vy :vz 0.0}))

(deftest attach-and-detach-rigid-body-manage-the-bodies-map
  (let [st (host/new-state)]
    (is (= #{} (host/rigid-body-ids st)))
    (host/attach-rigid-body! st 1 {:mass 1.0 :restitution 0.0
                                    :collider (engine/make-circle-collider 1.0)})
    (is (= #{1} (host/rigid-body-ids st)))
    (host/attach-rigid-body! st 2 {:mass 2.0 :restitution 0.0
                                    :collider (engine/make-circle-collider 1.0)})
    (is (= #{1 2} (host/rigid-body-ids st)))
    (host/detach-rigid-body! st 1)
    (is (= #{2} (host/rigid-body-ids st)))))

(deftest step-rigid-bodies-is-a-no-op-without-config-or-bodies
  (let [st (host/new-state)]
    (put-entity! st 1 "ball" 0.0 100.0 0.0 0.0)
    (host/step-rigid-bodies! st 16)
    (is (= {:tag "ball" :x 0.0 :y 100.0 :z 0.0 :vx 0.0 :vy 0.0 :vz 0.0}
           (get-in @st [:ents 1])) "no :rigid-body-2d config => untouched")))

(deftest step-rigid-bodies-advances-a-free-falling-body-under-gravity
  (let [st (host/new-state)]
    (put-entity! st 1 "ball" 0.0 100.0 0.0 0.0)
    (host/attach-rigid-body! st 1 {:mass 1.0 :restitution 0.0
                                    :collider (engine/make-circle-collider 0.01)})
    (host/set-rigid-body-gravity! st [0.0 -10.0])
    (host/step-rigid-bodies! st 100) ;; dt = 0.1s
    (let [e (get-in @st [:ents 1])]
      (testing "velocity: v = v0 + g*dt (exact for constant gravity)"
        (is (= -1.0 (:vy e))))
      (testing "position: symplectic Euler p = p0 + v'*dt"
        (is (= 99.9 (:y e))))
      (testing "x is untouched (no horizontal force)"
        (is (= 0.0 (:x e)))))))

(deftest step-rigid-bodies-resolves-a-collision-and-writes-back-to-ents
  (let [st (host/new-state)]
    ;; Two circles (r=1 each) already overlapping (separation 1.9 < 2.0),
    ;; approaching head-on — physics-2d resolves this in one step.
    (put-entity! st :a "crate" -0.95 0.0 4.0 0.0)
    (put-entity! st :b "crate" 0.95 0.0 -3.0 0.0)
    (host/attach-rigid-body! st :a {:mass 2.0 :restitution 1.0
                                     :collider (engine/make-circle-collider 1.0)})
    (host/attach-rigid-body! st :b {:mass 5.0 :restitution 1.0
                                     :collider (engine/make-circle-collider 1.0)})
    (host/set-rigid-body-gravity! st [0.0 0.0])
    (host/step-rigid-bodies! st 16)
    (let [ea (get-in @st [:ents :a]) eb (get-in @st [:ents :b])
          p-before (+ (* 2.0 4.0) (* 5.0 -3.0))
          p-after (+ (* 2.0 (:vx ea)) (* 5.0 (:vx eb)))]
      (testing "an elastic collision (e=1) conserves momentum through the ECS write-back"
        (is (< (Math/abs (- p-after p-before)) 1.0e-6)))
      (testing "the two bodies are no longer approaching (separating or resting)"
        (is (>= (- (:vx eb) (:vx ea)) -1.0e-6))))))

(deftest step-rigid-bodies-ignores-despawned-bodies
  (let [st (host/new-state)]
    (host/attach-rigid-body! st 99 {:mass 1.0 :restitution 0.0
                                     :collider (engine/make-circle-collider 1.0)})
    ;; entity 99 was never spawned (or already despawned) — must not throw.
    (host/step-rigid-bodies! st 16)
    (is (= {} (:ents @st)))))
