(ns qdzo.ex-tractor.log "Logging utils")

(set! *warn-on-reflection* true)

;; used for debug purposes
(def ^:dynamic *verbose* 0)

(defmacro log
  "Print logs if `*verbose*` is `>=` `verbose-lever`"
  [verbose-level & strs]
  `(if (>= *verbose* ~verbose-level)
     (println ~@strs)))

(defmacro log1 [& strs] `(log 1 ~@strs))
(defmacro log2 [& strs] `(log 2 ~@strs))
(defmacro log3 [& strs] `(log 3 ~@strs))
(defmacro log4 [& strs] `(log 4 ~@strs))


(defn fn-name
  "gets fn-name from given `f` instance"
  [f]
  (some-> #"^.+\$(.+)\@.+$"
          (re-find (str f))
          (second)
          (clojure.string/replace #"\_QMARK\_" "?")
          (symbol)))


(defmacro try-apply [f & args]
  `(try (~f ~@args)
        (catch Exception e#
          (log4 "Fail apply ["
                (fn-name ~f) "] - "
                "to" ~@args "Error: "(.getMessage e#) ") "))))
