(ns routes.tools
  "Tools for listing endpoints & route descriptions."
  (:require [routes.core :refer [pairs Routes]]
            [routes.macros #?(:clj :refer :cljs :refer-macros) [extend-types]])
  #?(:clj (:import (clojure.lang Keyword))))

(defprotocol RoutesListing
  (routes-listing [this m]
    "Generate a sequence of route descriptions from `this`, a Routes data structure,
    where a route description has :path, :endpoint, and potentially :keys.
    The map `m` propogates the higher-level path components & keys down to the endpoints."))

(defprotocol PatternListing
  (pattern-listing [this m]
    "Update `m` with the path components and keys required by `this`, a Pattern.
    Because a Pattern can be a set, which can match multiple paths, this function
    returns a seq of updated `m`'s."))

;; PatternListing extensions

(defn- conjv
  [coll x]
  (conj (vec coll) x))

(extend-protocol PatternListing
  #?(:clj String :cljs string)
  (pattern-listing [this m]
    (list (update m :path conjv this)))

  #?(:clj Boolean :cljs boolean)
  (pattern-listing [this m]
    (when this
      (list (update m :path conjv this))))

  Keyword
  (pattern-listing [this m]
    (-> m
        (update :keys conjv this)
        (update :path conjv this)
        (list))))

; set
(extend-types #?(:clj [clojure.lang.APersistentSet] :cljs [PersistentHashSet])
  PatternListing
  (pattern-listing [this m]
    (mapcat #(pattern-listing % m) this)))

; seq
(extend-types #?(:clj [clojure.lang.Sequential] :cljs [List PersistentVector])
  PatternListing
  (pattern-listing [this m]
    (reduce (fn [ms sub-pattern]
              (mapcat #(pattern-listing sub-pattern %) ms)) [m] this)))

;; RoutesListing extensions

(defn- route-descriptions
  [routes m]
  (if (satisfies? Routes routes)
    ; recurse
    (routes-listing routes m)
    ; handle leaf endpoint
    (list (assoc m :endpoint routes))))

(extend-types #?(:clj  [clojure.lang.Seqable]
                 :cljs [List PersistentVector PersistentArrayMap PersistentHashMap])
  RoutesListing
  (routes-listing [this m]
    (for [[pattern routes] (pairs this)
          pattern-m (pattern-listing pattern m)
          route-description (route-descriptions routes pattern-m)]
      route-description)))

;; helper

(defn listing
  "Generate a sequence of route descriptions from a Routes data structure,
  where each route description has :path, :endpoint, and potentially :keys."
  [routes]
  (routes-listing routes {}))
