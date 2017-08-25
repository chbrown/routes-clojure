## `routes`

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
      {"customers" {["/" :id] :customers
                    ""        :customers}
       "products"  {["/" :id] :products
                    ""        :products}})

And you wanted to merge and group these under two paths,
`/api/v1` and `/api/v2`,
but not write the whole thing out twice.
Due to the greedy nature of `bidi`'s endpoint matching, this would be impossible;
you could never access

In this library, `routes`,
there is a `(parameterize routes param-key param-value)` function that wraps a routes structure,
and adds a (required) parameter to all the endpoints.
This function creates a new instance of the `ParameterizedRoutes` record,
which wraps the routes in an extension of the `Routes` protocol,
which in turn requires the specified `{param-key param-value}` parameter pair.
Your routes would look like this:

    ["/api/" {"v1" (parameterize store-resource-routes :api-version 1)
              "v2" (parameterize store-resource-routes :api-version 2)}]

The path for `:customers` with parameters `:api-version 1` would be `/api/v1/customers`,
and generating a path for just `:customers` would throw an error.
The endpoint result for the path `/api/v2/customers/chb` would be `:customers :params {:api-version 2 :id "chb"}`.


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


## Alternatives

There are a lot of data-driven (i.e., routes-as-data, as opposed to imperative Compojure-style) cross-platform routing libraries out there.

* [bidi](https://github.com/juxt/bidi)
* [ataraxy](https://github.com/weavejester/ataraxy), from Mr. Compojure himself.
* [silk](https://github.com/DomKM/silk)


## Development

Run Clojure tests:

    lein with-profile test test

Run the ClojureScript tests:

    lein with-profile test doo rhino test once

Compile production JavaScript output:

    lein cljsbuild once production


## License

Copyright © 2017 Christopher Brown. [Eclipse Public License - v 1.0](https://www.eclipse.org/legal/epl-v10.html).
