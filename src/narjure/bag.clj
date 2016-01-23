(ns narjure.bag
  (:require [clojure.data.priority-map :refer [priority-map-keyfn-by]]))

(defprotocol Bag
  (put-el [this item])
  (get-el
    [this]
    [this k])
  (remove-el [this k])
  (count-els [this]))

;TODO must be discussed
(defn randomize-priority [priority]
  (* (rand) priority))

(defn assoc-to-bag
  "Set some random priority for a new item and slice map."
  [col {:keys [key priority] :as v} capacity]
  (let [ncol (->> (randomize-priority priority)
                  (assoc v :rand-priority)
                  (assoc col key))]
    (if (> (count ncol) capacity)
      (let [[k] (last ncol)]
        (dissoc ncol k))
      ncol)))

(defrecord DefaultBag [capacity queue]
  Bag
  (put-el [_ item]
    (DefaultBag. capacity (assoc-to-bag queue item capacity)))
  (get-el [_] (peek queue))
  (get-el [_ key] (when (contains? queue key) [key (queue key)]))
  (remove-el [_ key] (DefaultBag. capacity (dissoc queue key)))
  (count-els [_] (count queue)))

(defn default-bag
  ([] (default-bag 100))
  ([capacity]
   (DefaultBag. capacity (priority-map-keyfn-by :rand-priority >))))