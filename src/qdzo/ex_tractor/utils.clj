(ns qdzo.ex-tractor.utils
  (:require [clojure.string :as s])
  (:import (java.text SimpleDateFormat)
           (java.util.regex Matcher)))


(def ^:private cur-ns
  (the-ns 'qdzo.ex-tractor.utils))


(defn extract-pattern
  "Extract first pattern occurrence.
  If char found - coerce it to string.
  If not found - return nil"
  [^Matcher pattern s]
  (let [res (first (re-find pattern s))]
    (if res (str res) nil)))


(defn parse-int
  "tiny clojure wrapper on java version Integer/parseInt (static).
    Main reason - we can put it in collection"
  [str] (try (Integer/parseInt str)
             (catch Exception _)))


(defn str-extract-int
  "Clean string from chars and parse it to int"
  [s] (parse-int (extract-pattern #"([0-9]+)" s)))

(defn str-extract-date-str
  "Parse date string with format 'dd.mm.yy[yy]'
  and extract it as string"
  [s] (extract-pattern
        #"(\d{2}\.\d{2}.\d{2,4})"
        s))


(defn str-extract-time-str
  "Parse time string with format 'hh:mm'
  and extract it as string"
  [s] (extract-pattern #"(\d{2}:\d{2})" s))


(defn str-extract-datetime-str
  "Parse datetime string with format 'dd.mm.yy[yy] hh:mm'
  and extract it as string"
  [s] (extract-pattern
        #"(\d{2}\.\d{2}.\d{2,4}).(\d{2}:\d{2})"
        s))

(defn str-extract-datetime
  "Parse datetime string with format 'dd.mm.yyyy hh:mm'
  and convert it ot Date"
  [s]
  (some->> (str-extract-datetime-str s)
           (.parse
             (SimpleDateFormat. "dd.MM.yyyy HH:mm"))))


(defn- fn-form? [f]
  (and (list? f) (= (first f) 'fn)))

(defn resolve-fn
  "Try Resolve fn, compile it if needed.
   * fn - returns it
   * form - compile it
   * symbol - try resolve it from current namespace"
  [f]
  (cond (fn? f) f
        (fn-form? f) (eval f)
        (symbol? f) (ns-resolve cur-ns f)
        :throw (throw (IllegalArgumentException.
                        (str "Not supported arg: " f)))))

