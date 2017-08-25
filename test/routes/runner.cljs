(ns routes.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [routes.test]))

(doo-tests 'routes.test)
