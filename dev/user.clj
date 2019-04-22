(ns user
  (:require [orchestra.spec.test :as st]))

(defn instrument []
  (st/instrument))

(defn unstument []
  (st/unstrument))

(defn reinstument []
  (unstument)
  (instrument))
