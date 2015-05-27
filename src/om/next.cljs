(ns om.next
  (:refer-clojure :exclude [var?])
  (:require-macros [om.next :refer [defui]])
  (:require [goog.string :as gstring]
            [clojure.walk :as walk]
            [om.next.protocols :as p]))

(defprotocol IQueryParams
  (-params [this]))

(extend-type default
  IQueryParams
  (-params [_]))

(defprotocol IQuery
  (-query [this]))

(defn var? [x]
  (and (symbol? x)
       (gstring/startsWith (name x) "?")))

(defn var->keyword [x]
  (keyword (.substring (name x) 1)))

(defn bind-query [query params]
  (letfn [(replace-var [node]
            (if (var? node)
              (get params (var->keyword node) node)
              node))]
    (walk/prewalk replace-var query)))

(defn query [cl]
  (with-meta
    (bind-query (-query cl) (-params cl))
    {:component cl}))

(defn create-factory [cl]
  (fn [props children]
    (js/React.createElement cl #js {:omcljs$value props} children)))

(defn props [c]
  (.. c -props -omcljs$value))

(defn build-index [cl]
  (let [index (atom {})]
    (letfn [(build-index* [cl sel]
              (let [{ks true ms false} (group-by keyword? sel)]
                (swap! index #(merge-with into % (zipmap ks (repeat #{cl}))))
                (doseq [m ms]
                  (let [[attr sel] (first m)]
                    (swap! index #(merge-with into % {attr #{cl}}))
                    (let [cl (-> sel meta :component)]
                      (build-index* cl sel))))))]
      (build-index* cl (query cl))
      @index)))

(defn root [component store opts]
  (letfn [(render [data]
            (js/React.render component
              data (:target opts)))]
    (let [q (query component)]
      (cond
       (satisfies? p/IPullAsync store)
       (p/pull-async store q nil render)
       :else
       (render (p/pull store nil q))))))