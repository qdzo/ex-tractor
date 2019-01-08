(ns user
  (:require [orchestra.spec.test :as st]))

(defn instument []
  (st/instrument))

(defn unstument []
  (st/unstrument))

(defn reinstument []
  (instrument-all)
  (unstument-all))