(ns kami.input
  "hiccup for input — key/pointer bindings described as EDN data, interpreted into axes
   and actions. The binding map is data: store it as datoms, fork it, rebind without code.

     {:axes    {\"MoveX\" {:pos #{\"d\" \"ArrowRight\"} :neg #{\"a\" \"ArrowLeft\"}}
                \"MoveY\" {:pos #{\"w\" \"ArrowUp\"}    :neg #{\"s\" \"ArrowDown\"}}}
      :actions {:jump #{\" \"} :fire #{\"f\"}}}

   `wire!` attaches listeners that read the map and report axis/action changes; it returns
   an unsubscribe fn. The same EDN map drives any executor (web here; a native input layer
   reads the identical data).")

(def default-map
  {:axes    {"MoveX" {:pos #{"d" "D" "ArrowRight"} :neg #{"a" "A" "ArrowLeft"}}
             "MoveY" {:pos #{"w" "W" "ArrowUp"}    :neg #{"s" "S" "ArrowDown"}}}
   :actions {:jump #{" "} :fire #{"f" "F"}}})

(defn axes-from-held
  "Pure: given the input-map and the set of currently-held keys, return {axis value} in
   [-1,1]. Works in CLJ or CLJS — the binding logic is data, not platform code."
  [imap held]
  (reduce-kv (fn [m axis {:keys [pos neg]}]
               (assoc m axis (- (if (some held pos) 1 0) (if (some held neg) 1 0))))
             {} (:axes imap)))

(defn- action-for [imap k]
  (some (fn [[a ks]] (when (contains? ks k) a)) (:actions imap)))

(defn wire!
  "Interpret the EDN input-map against window key events. Calls (on-axes {axis v}) when an
   axis changes and (on-action kw down?) on bound action keys. Returns an unsubscribe fn."
  [imap {:keys [on-axes on-action]}]
  (let [held (atom #{})
        recompute #(when on-axes (on-axes (axes-from-held imap @held)))
        kd (fn [e]
             (let [k (.-key e)]
               (when-not (contains? @held k)
                 (swap! held conj k)
                 (when-let [a (action-for imap k)] (when on-action (on-action a true)))
                 (recompute))))
        ku (fn [e]
             (let [k (.-key e)]
               (when (contains? @held k)
                 (swap! held disj k)
                 (when-let [a (action-for imap k)] (when on-action (on-action a false)))
                 (recompute))))]
    (.addEventListener js/window "keydown" kd)
    (.addEventListener js/window "keyup" ku)
    (fn [] (.removeEventListener js/window "keydown" kd)
           (.removeEventListener js/window "keyup" ku))))
