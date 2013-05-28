(ns hlisp.wigwam
  (:refer-clojure :exclude [js->clj])
  (:require-macros
    [tailrecursion.hoplon.macros  :refer [cljs-ns]]
    [tailrecursion.javelin.macros :refer [cell]])
  (:require
    [clojure.walk                 :as walk]
    [clojure.string               :as string]
    [tailrecursion.hoplon.util    :as u]
    [tailrecursion.javelin        :as j]))

(def loading    (cell '[]))
(def keywordize (atom false))
(def log        (u/logger (cljs-ns)))

(defn js->clj [thing & {:keys [keywordize]}]
  (try
    (let [clj (cljs.core/js->clj thing)]
      (if (and (map? clj) keywordize) (walk/keywordize-keys clj) clj))
    (catch js/Error e)))

(defn ifdef [thing default]
  (if (identical? js/undefined thing) default thing))

(defrecord WigwamException
  [exception message type severity state])

(defn err->wigwamexception [e]
  (WigwamException.
    (.-exception e)
    (.-message e)
    (.-type e)
    (keyword (.-severity e)) 
    (js->clj (ifdef (.-state e) ::none) :keywordize true)))

(defn- js->clj* [x]
  (if @keywordize (js->clj x :keywordize true) (js->clj x)))

(defn async
  ([f out]
   (async f out (atom nil)))
  ([f out err]
   (async f out err (atom nil)))
  ([f out err fin] 
   (let [pass (fn [x y]
                #(try (do (reset! x (js->clj* %)) (reset! y nil))
                   (catch js/Error e (.severe log e))))
         fail (fn [x y]
                #(try (let [e (err->wigwamexception %)]
                        (if (not= ::none (:state e))
                          (reset! x (js->clj* (:state e))))
                        (reset! y e))
                   (catch js/Error e (.severe log e))))
         load (fn [x] #(do (if (seq @loading) (swap! loading pop)) (x %)))]
     (swap! loading conj 1)
     (js/Wigwam.async f (pass out err) (fail out err) (load (pass fin (atom nil)))))))

