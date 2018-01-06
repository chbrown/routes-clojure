## `routes`

[![Clojars Project](https://img.shields.io/clojars/v/routes.svg)](https://clojars.org/routes)
[![Travis CI Build Status](https://travis-ci.org/chbrown/routes-clojure.svg?branch=master)](https://travis-ci.org/chbrown/routes-clojure)
[![Coverage Status](https://coveralls.io/repos/github/chbrown/routes-clojure/badge.svg?branch=master)](https://coveralls.io/github/chbrown/routes-clojure?branch=master)

`routes` for Clojure is inspired and heavily influenced in implementation by [`bidi`](https://github.com/juxt/bidi),
but works more like a language than a regular expression.

`bidi` is opinionated about your routes' endpoints.

With `bidi`, suppose you have this route structure:

    ["/" {"users" {""        :users
                   ["/" :id] :users}}]

You'd want the path for `:users` to be `/users` and the path for `:users` with `:params {:id 7}` to be `/users/7`.

Because `bidi` uses greedy search, this doesn't work.
In the latter case, it finds the first endpoint that matches the given target,
and serializes the components that led to that endpoint (discarding the excess route parameter),
resulting in `/users`.

Suppose instead you had this ordering:

    ["/" {"users" {["/" :id] :users
                   ""        :users}}]

This would produce the correct path for `:users` with `:params {:id 7}`, `/users/7`,
but it would throw an exception when serializing the path for just `:users`,
since it would run with the first match, which requires an `:id` parameter,
and there is none such for the index route.

Another use-case is to contextualize a whole group of routes.
For example, suppose you have some routes:

    (def store-resource-routes
      {"/customers" {["/" :id] :customers
                     ""        :customers}
       "/products"  {["/" :id] :products
                     ""        :products}})

And you wanted to merge and group these under two paths,
`/api/v1` and `/api/v2`,
but not write the whole thing out twice.
Due to the greedy nature of `bidi`'s endpoint matching, this would be impossible;
you could never generate paths for any of the api versions besides the first,
and you could not tell which api version a path used from the matched-route's `:route-params`.

In this library's `routes.extra` module,
there is a `(parameterize pattern key value)` function
that wraps a pattern structure in an extension of the `Pattern` protocol,
and adds a (required) parameter to all the endpoints under that pattern.
This function creates a new instance of the `ParameterizedPattern` record,
and, when generating a path, requires the route context to match the specified `{key value}` pair,
and adds that same pair to the route context when resolving the endpoint from a path.
Your routes would look like this:

    (def routes
      ["/api/" {(parameterize "v1" :api-version 1) store-resource-routes
                (parameterize "v2" :api-version 2) store-resource-routes}])

With these routes, we can generate the path for `:customers` with parameters `:api-version 1`:

    (generate-path routes {:endpoint :customers :api-version 1})
    ;=> "/api/v1/customers"

As mentioned, the parameters _must_ be provided, or else there's no match.

    (generate-path routes {:endpoint :customers})
    ;=> nil

And we can resolve endpoints that specify the parameters used to match the given path:

    (resolve-endpoint {:path "/api/v2/customers/chb"})
    ;=> {:endpoint :customers :api-version 2 :id "chb"}


### Differences from `bidi`

**Failed matches.**
`bidi` throws on failed matches, `routes` returns nil.

**Naming**
* Protocols
  - `Pattern` → `Pattern` (no change)
  - `Matched` → `Routes`
* functions
  - `match-route` / `match-route*` / `match-pair` / `resolve-handler` → `resolve-endpoint`
    (yep, only one function to handle them all)
  - `match-pattern` → `match-pattern`
    (no change)
  - `path-for` / `unmatch-pair` / `unresolve-handler` → `generate-path`
    (simple as that!)
  - `unmatch-pattern` → `generate-pattern-path`
    (though this is for patterns and you probably won't call it directly)


## Quick-start example

    (ns user
      (:require [routes.core :refer [resolve-endpoint generate-path]]))

    (def routes
      {"/login" :login
       ["/wiki/" :page] :wiki})

    (resolve-endpoint routes {:path "/wiki/Welcome"})
    ;=> {:endpoint :wiki :page "Welcome"}

    (generate-path routes {:endpoint :login})
    ;=> "/login"

    (generate-path routes {:endpoint :wiki :page "Contact"})
    ;=> "/wiki/Contact"


## Alternatives

There are a lot of data-driven (i.e., routes-as-data, as opposed to imperative Compojure-style) cross-platform routing libraries out there.

* [bidi](https://github.com/juxt/bidi) (**bi**-**di**rectional "URI dispatch")
  - `bidi` was not the first data-driven router library (first commit was 2013-12-20), but it's been the most successful.
  - It's one of the simplest, perhaps due to being one of the most opinionated / restrictive.
  - This library is most directly derived from `bidi`; see above for the important differences.
* [reitit](https://github.com/metosin/reitit) ("reitit" is Finnish for "routes")
  - Very much like `bidi`, but with more introspection about the path.
  - Perhaps the most similar of these libraries, in spirit, to `routes`,
    though I started from `bidi` (`reitit`'s first commit was 2017-08-07, `routes`'s was 2017-08-24)
  - Great [documentation](https://metosin.github.io/reitit/)!
* [ataraxy](https://github.com/weavejester/ataraxy), from Mr. Compojure himself.
  - Very much like `bidi`, but focused on Ring integration
  - More support for dispatching on properties of the full HTTP request, as opposed to just the URI/path
  - First commit: 2015-12-12
* [gudu](https://github.com/thatismatt/gudu) "**G**enerate **U**RL, **D**egenerate **U**RL"
  - Very much like `bidi`, but more of a DSL, oriented around URL conventions
    (e.g., slashes separate path components)
  - First commit: 2013-03-10
* [bide](https://github.com/funcool/bide)
  - Very much like `bidi`, but patterns are described as strings rather than data structures
    (e.g., `bidi`'s `["/account/" [[[:account-uuid "/" :page-uuid] :account/page]]]` becomes
           `bide`'s `["/account/:account-uuid/:page-uuid" :account/page]`).
  - Tighter integration with the browser: "It [bide] uses goog.History API under the hood"
  - First commit: 2016-08-27
* [silk](https://github.com/DomKM/silk) "Isomorphic Clojure[Script] Routing"
  - Very much like `bidi`, but with more helper functions for preparing path components, e.g. `(silk/int :id)`
  - First commit: 2014-06-28


## Development

Compile production JavaScript output:

    lein cljsbuild once production


## Testing

* Automated tests run on [Travis CI](https://travis-ci.org/chbrown/routes-clojure).
* Coverage results are sent to [Coveralls](https://coveralls.io/github/chbrown/routes-clojure).

Run Clojure tests:

    lein test

Compute Clojure test coverage:

    lein cloverage

Run the ClojureScript tests:

    lein doo rhino test once

Run the ClojureScript tests on Chrome:

    npm install -g karma-cli
    npm install karma karma-cljs-test karma-chrome-launcher
    lein doo chrome test once


## License

Copyright © 2017 Christopher Brown. [Eclipse Public License - v 1.0](https://www.eclipse.org/legal/epl-v10.html).
