(ns routes.core
  "Core data-structure-driven routing.

  The main functions to call are:

      (resolve-endpoint routes {:path string})
      (generate-path routes {:endpoint form, ...params})

  The main protocols to extend are Routes and Pattern."
  (:require [routes.macros #?(:clj :refer :cljs :refer-macros) [extend-types]]
            [clojure.string :as str])
  #?(:clj (:import (clojure.lang Keyword))))

(defprotocol PairSeq
  "Common protocol for treating both sequences and maps as pairs of items"
  (pairs [this] "Iterate by pairs"))

(defprotocol Routes
  "Right side of a Route, to be used when the corresponding left side, the Pattern, is matched.
  A Routes structure is recursively treated as [Pattern Route] tuples,
  where Route is either another Routes structure or an Endpoint,
  and Endpoint is anything that does not implement the Routes protocol."
  (resolve-endpoint [this m]
    "Iterate through the [pattern route] pairs in the Routes structure `this`.
    If `pattern` matches `m` (based on its :path and maybe other parameters),
      AND route is an Endpoint, return `m` with :endpoint set to route;
      ELSE recurse into the matching Routes data structure.
    If no `pattern` matches the current `m`, return nil.")
  (generate-path [this m]
    "Generate the path string that points to `m` (based on its :endpoint and other parameters).
    While `this` is the full Routes structure, path generation works from the bottom up;
    when recursing down into the Routes structure, the Pattern side is ignored until
    a leaf endpoint matches the :endpoint in `m`; only then are the Patterns rendered
    to strings via generate-pattern-path, starting with the leaf-most Pattern.
    Returns nil if no route in `this` matches `m`."))

(defprotocol Pattern
  "Left side of a Route (the other side is the result if this pattern matches).
  Consumers usually won't call these functions directly,
  but can use this protocol to implement custom patterns."
  (match-pattern [this m]
    "If `m` matches this pattern, update/remove the parts consumed
    by the pattern and return the new `m`; otherwise return nil.")
  (generate-pattern-path [this m]
    "Generate the string that would be matched by this pattern,
    extracting parameters from `m` if needed, or nil if there is a parameter
    in the pattern that's not provided in `m`."))

;; PairSeq extensions

; sequential-like (Clojure maps are Seqable but not Sequential)
(extend-types #?(:clj [clojure.lang.Sequential] :cljs [List PersistentVector])
  PairSeq
  (pairs [this]
    ; extend-type :pre checks don't work in ClojureScript
    (assert (even? (count this)) "Cannot iterate odd-long sequence by pairs")
    (partition 2 this)))
; map-like
(extend-types #?(:clj [clojure.lang.APersistentMap] :cljs [PersistentArrayMap PersistentHashMap])
  PairSeq
  (pairs [this]
    (seq this)))

;; Pattern extensions

(defn- split-path
  "Split the given path (usually a suffix) at the first slash, returning a
  vector of [part remainder], or return [path \"\"] if there is no slash"
  [path]
  (if-let [next-slash (str/index-of path \/)]
    [(subs path 0 next-slash) (subs path next-slash)]
    [path ""]))

(extend-protocol Pattern
  ; strings match the current path literally
  #?(:clj String :cljs string)
  (match-pattern [this m]
    (if (empty? this)
      m
      (when (str/starts-with? (:path m) this)
        (update m :path subs (count this)))))
  (generate-pattern-path [this _] this)

  ; false never matches; true consumes the rest of the path
  #?(:clj Boolean :cljs boolean)
  (match-pattern [this m]
    (when this (assoc m :path "")))
  (generate-pattern-path [this _]
    (when this ""))

  ; unadorned keywords capture everything up until the next slash, but not nothing
  Keyword
  (match-pattern [this m]
    (let [[v remaining-path] (split-path (:path m))]
      (when (seq v)
        (assoc m this v :path remaining-path))))
  (generate-pattern-path [this m]
    (get m this)))

; set-like; sets are processed in whatever natural order they happen to have
(extend-types #?(:clj [clojure.lang.APersistentSet] :cljs [PersistentHashSet])
  Pattern
  ; PersistentHashSet
  (match-pattern [this m]
    (some #(match-pattern % m) this))
  (generate-pattern-path [this m]
    (some #(generate-pattern-path % m) this)))

; seq-like; a vector Pattern can consist of strings and keywords
(extend-types #?(:clj [clojure.lang.Sequential] :cljs [List PersistentVector])
  Pattern
  (match-pattern [this m]
    (reduce (fn [m sub-pattern]
              (when (some? m)
                (match-pattern sub-pattern m))) m this))
  (generate-pattern-path [this m]
    (let [parts (map #(generate-pattern-path % m) this)]
      (when (not-any? nil? parts)
        (str/join parts)))))

;; Routes extensions

(extend-types #?(:clj  [clojure.lang.Seqable]
                 :cljs [List PersistentVector PersistentArrayMap PersistentHashMap])
  Routes
  (resolve-endpoint [this m]
    (some (fn [[pattern routes]]
            ; Takes a route and a context/state map. If pattern matches the :remaining
            ; path in m, returns a map with a :endpoint. If not, returns nil.
            (when-let [new-m (match-pattern pattern m)]
              (if (satisfies? Routes routes)
                ; if routes is a Seq or otherwise implements Routes, recurse
                (resolve-endpoint routes new-m)
                ; otherwise it's an endpoint that matches iff path is empty (completely consumed)
                (when (empty? (:path new-m))
                  (-> new-m
                      (dissoc :path)
                      (assoc :endpoint routes)))))) (pairs this)))
  (generate-path [this m]
    (some (fn [[pattern routes]]
            (if (= routes (:endpoint m))
              (generate-pattern-path pattern m)
              (when (satisfies? Routes routes)
                (when-let [matched-url (generate-path routes m)]
                  (str (generate-pattern-path pattern m) matched-url))))) (pairs this))))
