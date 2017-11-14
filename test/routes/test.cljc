(ns routes.test
  (:require [clojure.test #?(:clj :refer :cljs :refer-macros) [deftest is are testing]]
            [routes.core :as routes :refer [pairs resolve-endpoint generate-path]]
            [routes.extra :refer [parameterize]]
            [routes.tools :refer [RoutesListing PatternListing listing]]))

(deftest util
  (testing "pairs handles sequences and maps the same way"
    (is (= (pairs (list 1 10 2 20))
           (pairs (hash-map 1 10 2 20))
           (pairs [1 10 2 20])
           (pairs {1 10 2 20}))))
  (testing "pairs fails on non-pairable sequence"
    (is (thrown? #?(:clj AssertionError :cljs js/Error) (pairs [1 2 3])))))

(def store-routes
  ["/" {"customers"    {["/" :id] :customers
                        ""        :customers}
        "products"     {["/" :id] :products
                        ""        :products}
        "faq/"         {:page     :faq-page}
        ["order/" :id] {"/lookup" :order-lookup}}])

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
    (is (= "/order/4d5e6f/lookup"
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

(deftest non-literal-types
  (let [routes ["/" (hash-map "a" :a "b" :b)
                true :default]]
    (testing "hash-map works same as map literal"
      (is (= {:endpoint :a} (resolve-endpoint routes {:path "/a"})))
      (is (= {:endpoint :default} (resolve-endpoint routes {:path "/other"}))))))

(def store-handlers
  "Handlers for all endpoints in store-routes,
  with alternative syntaxes for destructuring/unpacking route params"
  {:customers    (fn [req]
                   (if-let [id (-> req :route-params :id)]
                     (str "Customer Page for " id)
                     "Customers Listing"))
   :products     (fn [{{id :id} :route-params}]
                   (if id
                     (str "Product #" id)
                     "Products Listing"))
   :faq-page     (fn [{:keys [route-params]}]
                   (str "FAQ: " (:page route-params)))
   :order-lookup (fn [req]
                   (str "Order Lookup #" (get-in req [:route-params :id])))})

(deftest ring-integration
  (let [router (routes/make-handler {["/" true] (fn [_] "Index")})]
    (testing "simple ring catch-all"
      (is (= "Index" (router {:uri "/"})))
      (is (= "Index" (router {:uri "/some/long/path"})))))
  (let [store-router (routes/make-handler store-routes store-handlers)]
    (testing "ring routing success"
      (is (= "Customers Listing"
             (store-router {:uri "/customers"})))
      (is (= "Product #123"
             (store-router {:uri "/products/123"})))
      (is (= "FAQ: shipping-policies"
             (store-router {:uri "/faq/shipping-policies"})))
      (is (= "Order Lookup #zaqwsx"
             (store-router {:uri "/order/zaqwsx/lookup"}))))
    (testing "ring routing failure"
      (is (thrown-with-msg?
           #?(:clj RuntimeException :cljs :default)
           #"^Cannot resolve endpoint from request$"
           (store-router {:uri "/support"}))))))

; routes.extra

(def api-routes
  (let [resource-routes {["user/" :id] :user
                         "status" :status}]
    {"/api/" {(parameterize "v1/" :api-version 1) resource-routes
              (parameterize "v2/" :api-version 2) resource-routes}}))

(deftest extra
  (testing "resolving through parameterized patterns"
    (is (= {:endpoint :status :api-version 1}
           (resolve-endpoint api-routes {:path "/api/v1/status"})))
    (is (= {:endpoint :status :api-version 2}
           (resolve-endpoint api-routes {:path "/api/v2/status"})))
    (is (= {:endpoint :user :id "root" :api-version 2}
           (resolve-endpoint api-routes {:path "/api/v2/user/root"})))
    (is (nil? (resolve-endpoint api-routes {:path "/api/status"}))))
  (testing "generating paths through parameterized patterns"
    (is (= "/api/v1/status"
           (generate-path api-routes {:endpoint :status :api-version 1})))
    (is (nil? (generate-path api-routes {:endpoint :status})))))

; routes.tools

(deftest tools
  (testing "store listing"
    (is (= [{:path ["/" "customers" "/" :id]    :endpoint :customers     :keys [:id]}
            {:path ["/" "customers" ""]         :endpoint :customers}
            {:path ["/" "products" "/" :id]     :endpoint :products      :keys [:id]}
            {:path ["/" "products" ""]          :endpoint :products}
            {:path ["/" "faq/" :page]           :endpoint :faq-page      :keys [:page]}
            {:path ["/" "order/" :id "/lookup"] :endpoint :order-lookup  :keys [:id]}]
           (listing store-routes))))
  (testing "set listing"
    (is (= [{:path ["/" "a"] :endpoint :done}
            {:path ["/" "b"] :endpoint :done}]
           (listing {"/" [#{"a" "b"} :done]}))))
  (testing "boolean listing"
    (is (= [{:path ["/" "a" true] :endpoint :done}]
           (listing {"/" (hash-map ["a" true] :done ["b" false] :fail)}))))
  (testing "empty listing"
    (is (empty? (listing {}))))
  (testing "parameterized listing"
    (is (= [{:path ["/api/" "v1/" "user/" :id] :endpoint :user   :keys [:api-version :id]}
            {:path ["/api/" "v1/" "status"]    :endpoint :status :keys [:api-version]}
            {:path ["/api/" "v2/" "user/" :id] :endpoint :user   :keys [:api-version :id]}
            {:path ["/api/" "v2/" "status"]    :endpoint :status :keys [:api-version]}]
           (listing api-routes))))
  (testing "listing protocol satisfaction"
    (are [x] (satisfies? RoutesListing x)
      (hash-map) {} [] (list "/" :done))
    (are [x] (satisfies? PatternListing x)
      "" "/" true false :done ::done #{} (list "/" :page) [])))
