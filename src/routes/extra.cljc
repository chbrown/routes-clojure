(ns routes.extra
  "Extra wrappers and customizers for Routes and Pattern structures."
  (:require [routes.core :refer [Pattern match-pattern generate-pattern-path]]))

(defrecord ParameterizedPattern [pattern key value]
  ; ParameterizedPattern is retrofitted to implement PatternListing in routes.tools
  Pattern
  (match-pattern [_ m]
    (match-pattern pattern (assoc m key value)))
  (generate-pattern-path [_ m]
    ; m must contain the key `key` with the value `value`
    (when (= value (get m key))
      ; should we (dissoc m key)?
      (generate-pattern-path pattern m))))

(defn parameterize
  "Wrap `pattern` in a ParameterizedPattern record,
  which implements the Pattern protocol (match-pattern and generate-pattern-path),
  so that the `pattern` only matches if the route context (m)
  contains the given {key value} pair"
  [pattern key value]
  (->ParameterizedPattern pattern key value))
