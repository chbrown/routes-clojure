(ns routes.test
  (:require #?(:clj  [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest is testing]])
            [routes.core :as routes :refer [pairs resolve-endpoint generate-path]]))

(deftest util
  (testing "pairs handles sequences and maps the same way"
    (is (= (pairs (list 1 10 2 20))
           (pairs [1 10 2 20])
           (pairs {1 10 2 20}))))
  (testing "pairs fails on non-pairable sequence"
    (is (thrown? #?(:clj AssertionError :cljs js/Error) (pairs [1 2 3])))))

(def store-routes
  ["/" {"customers" {["/" :id] :customers
                     ""        :customers}
        "products"  {["/" :id] :products
                     ""        :products}
        "faq/"      {:page     :faq-page}
        ["order-lookup/" :id] :order-lookup}])

(deftest store
  (testing "normal successful matches"
    (is (= {:endpoint :customers :id "123"}
           (resolve-endpoint store-routes {:path "/customers/123"})))
    (is (= {:endpoint :customers}
           (resolve-endpoint store-routes {:path "/customers"}))))

  (testing "partial path does not match"
    (is (nil? (resolve-endpoint store-routes {:path "/"}))))

  (testing "keywords don't match nothing"
    (is (nil? (resolve-endpoint store-routes {:path "/customers/"}))))

  (testing "totally divergent path does not match"
    (is (nil? (resolve-endpoint store-routes {:path "/missing/1"}))))

  (testing "normal successful path generation"
    (is (= "/customers/123"
           (generate-path store-routes {:endpoint :customers :id 123})))
    (is (= "/customers"
           (generate-path store-routes {:endpoint :customers})))
    (is (= "/order-lookup/4d5e6f"
           (generate-path store-routes {:endpoint :order-lookup :id "4d5e6f"}))))

  (testing "under-specified params for endpoint does not result in path"
    (is (nil? (generate-path store-routes {:endpoint :order-lookup})))
    (is (nil? (generate-path store-routes {:endpoint :faq-page})))))

(deftest boolean-patterns
  (let [routes {"/ok/" [true :done]
                "/no/" [false :dont]}]
    (testing "boolean true matches everything"
      (is (= {:endpoint :done} (resolve-endpoint routes {:path "/ok/a/b"})))
      (is (= "/ok/" (generate-path routes {:endpoint :done}))))
    (testing "boolean false matches nothing"
      (is (nil? (resolve-endpoint routes {:path "/no/t"})))
      (is (nil? (resolve-endpoint routes {:path "/no/"})))
      (is (nil? (generate-path routes {:endpoint :dont}))))))

(deftest keyword-patterns
  (let [routes {"/" [:dir ["/" :done]]}]
    (testing "keyword matching with more path"
      (is (= {:endpoint :done :dir "sub"} (resolve-endpoint routes {:path "/sub/"})))
      (is (= "/sub/" (generate-path routes {:endpoint :done :dir "sub"}))))))

(deftest set-patterns
  (let [routes {"/" [#{"a" "b"} :a-or-b]}]
    (testing "set matches exactly one member"
      (is (= {:endpoint :a-or-b} (resolve-endpoint routes {:path "/a"})))
      (is (= {:endpoint :a-or-b} (resolve-endpoint routes {:path "/b"})))
      (is (nil? (resolve-endpoint routes {:path "/c"})))
      (is (nil? (resolve-endpoint routes {:path "/"}))))
    (testing "set generates some string"
      ; no guarantees :)
      (is (contains? #{"/a" "/b"} (generate-path routes {:endpoint :a-or-b})))))

  (let [routes {"/" [#{[:a "/a"] "none" [:b "/b"]} :done]}]
    (testing "set matches from params"
      (is (= {:endpoint :done} (resolve-endpoint routes {:path "/none"})))
      (is (= {:endpoint :done :a "A1"} (resolve-endpoint routes {:path "/A1/a"}))))
    (testing "set generates string for first matching pair"
      (is (= "/B2/b" (generate-path routes {:endpoint :done :b "B2"})))
      (is (= "/none" (generate-path routes {:endpoint :done}))))))
