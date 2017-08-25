(ns routes.macros
  "This module should be considered private to routes")

(defmacro extend-types
  "Expand to (extend-type type-sym impls...) for each type-sym in type-syms"
  [type-syms & impls]
  `(do ~@(for [type-sym# type-syms]
           `(extend-type ~type-sym# ~@impls))))
