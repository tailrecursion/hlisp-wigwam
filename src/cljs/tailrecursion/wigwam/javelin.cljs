(ns tailrecursion.wigwam
  (:refer-clojure :exclude [js->clj])
  (:require-macros
    [tailrecursion.javelin.macros :refer [cell]])
  (:require
    [tailrecursion.javelin  :as javelin]
    [clojure.walk           :as walk]
    [clojure.string         :as string]))

(def loading    (cell '[]))
(def keywordize (atom false))

(defn clj->js [x]
  (cond (string? x)   x
        (keyword? x)  (name x)
        (map? x)      (.-strobj (reduce (fn [m [k v]]
                                          (assoc m (clj->js k) (clj->js v))) {} x))
        (coll? x)     (apply array (map clj->js x))
        :else         x))

(defn js->clj [thing & {:keys [keywordize]}]
  (let [clj (cljs.core/js->clj thing)]
    (if (and (map? clj) keywordize) (walk/keywordize-keys clj) clj)))

(defn log [label & [thing]]
  (.log js/console label (clj->js thing)))

(defn debug [label & [thing]]
  (.debug js/console label (clj->js thing)))

(defn warn [label & [thing]]
  (.warn js/console label (clj->js thing)))

(defn error [label & [thing]]
  (.error js/console label (clj->js thing)))

(def search
  "Data specified in the query string, as a cljs map (with keywordized keys)."
  (js->clj js/Wigwam.cfg.argv :keywordize true))

(def devmode
  "Use dev=true in query string to enable dev mode debug output."
  (not (string/blank? (:dev search))))

(defn ifdef [thing default]
  (if (identical? js/undefined thing) default thing))

(defrecord WigwamException
  [exception message type state])

(defn err->wigwamexception [e]
  (WigwamException.
    (.-exception e)
    (.-message e)
    (.-type e)
    (js->clj (ifdef (.-state e) ::none) :keywordize true)))

(defn- js->clj* [x]
  (if @keywordize (js->clj x :keywordize true) (js->clj x)))

(defn async
  ([f out]
   (async f out (atom nil)))
  ([f out err]
   (async f out err (atom nil)))
  ([f out err fin] 
   (let [pass (fn [x y] #(do (reset! x (js->clj* %)) (reset! y nil)))
         fail (fn [x y] #(let [e (err->wigwamexception %)]
                           (if (not= ::none (:state e))
                             (reset! x (js->clj* (:state e))))
                           (reset! y e)))
         load (fn [x y] #(do (if (seq @loading) (swap! loading pop)) (x %)))]
     (swap! loading conj 1)
     (js/Wigwam.async f (pass out err) (fail out err) (load (pass fin (atom nil)))))))

