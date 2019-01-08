(ns qdzo.ex-tractor.core
  (:require [clojure.pprint :as pp]
            [clojure.edn :as edn]
            [clojure.tools.cli :as cli :refer [parse-opts]]
            [qdzo.ex-tractor.log :as l]
            [qdzo.ex-tractor.excel :as excel]
            [qdzo.ex-tractor.excel.specs :as exspec]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.data.csv :as csv]
            [clojure.data.json :as json]
            [clojure.inspector :as inspector]
            [clojure.string :as str])
  (:import (java.io Writer))
  (:gen-class))

(set! *warn-on-reflection* true)

(defn sheet->csv [sheet]
  (cons (->> sheet first keys (map name))
        (map vals sheet)))

(defn write-edn [^Writer writer sheet]
  (doto writer
    (.write (str sheet))
    (.flush)))

(defn load-edn [file]
  (edn/read-string (slurp file)))

(def print-formats
  "print formats used by `print-sheet` fn"
  #{:edn :table :csv :json :row})


(defn print-sheet
  "Print sheet in given format.
   Formats are  :edn, :table, :json, :csv, :row (default)"
  [sheet format]
  (case format
    :edn (write-edn *out* sheet)
    :table (pp/print-table (keys (first sheet)) sheet)
    :csv (csv/write-csv *out* (sheet->csv sheet))
    :json (json/write sheet *out* :escape-unicode false)
    #_:row (doseq [r sheet] (println r)))
  (.flush *out*))


(defn inspector-show-sheet
  "Show sheet with its metadata in inspector.
  Used for debugging"
  [sheet]
  (inspector/inspect-table
    (map excel/materialize-row-meta sheet)))

;; ----------------------------------------------- EXCEL --------------------------------------------------

(defn excel-file? [filename]
  (re-find #"\.xlsx?$" filename))

(defn edn-file? [filename]
  (re-find #"\.edn$" filename))

;; ----------------------------------------------------------------------------


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def cli-options
  [["-h" "--help" "Print help message"]
   ["-i" "--inspector" "Show result in inspector table"]
   ["-t" "--template" "Print extract-spec template file"]
   ["-v" nil "Verbosity level" :id :verbosity :default 0 :update-fn inc]])


(defn help-msg [options-summary]
  (->>
    ["Ex-tractor is tiny cli-tool to parse and extract data from excel files"
     ""
     "Usage: 'java -jar ex-tractor.jar [options] <excel-file> <spec-file> <print-format>'"
     ""
     " * <excel-file>    - xls/xlsx file with data"
     " * <spec-file>     - edn file with transformation and extraction spec"
     " * <print-format>  - result printing format - one of: edn, table, json, csv, row (default)"
     ""
     "Options:"
     ""
     options-summary
     ""]
    (str/join \newline)))


(defn error-msg [errors]
  (str "Errors occurs while parsing your command:\n\n"
       (str/join \newline errors)))


(defn parse-cli-args
  "Parse args and return action(map) to invoke"
  [args]
  (let [{:keys [options arguments summary errors]}
        (cli/parse-opts args cli-options)
        arguments-count (count arguments)
        [excel-file spec-file print-format] arguments]
    (cond
      (:help options)
      {:action :print-help :msg (help-msg summary)}

      (:template options)
      {:action :print-template
       :msg (excel/extraction-spec-template)}

      errors
      {:action :print-errors :msg (error-msg errors)}

      (> 2 arguments-count)
      {:action :print-errors
       :msg (error-msg
              [(str "Arguments should be at least 2, but receives: "
                    arguments-count ". args: " arguments)])}

      (not (excel-file? excel-file))
      {:action :print-errors
       :msg (error-msg
              [(str excel-file " is not a correct 'excel' file")])}

      (not (edn-file? spec-file))
      {:action :print-errors
       :msg (error-msg
              [(str spec-file " is not a correct 'edn' file")])}

      (and print-format
           (not (print-formats (keyword print-format))))
      {:action :print-errors
       :msg (error-msg
              [(str "print-format should not exist or should be one of: "
                    (mapv symbol print-formats))])}

      :default
      {:action :extract
       :excel-file excel-file
       :spec-file spec-file
       :print-format (keyword print-format)
       :inspector? (get options :inspector false)
       :verbosity (get options :verbosity 0)})))


(defn -main [& args]
  (let [{:keys [action msg print-format
                excel-file spec-file
                inspector? verbosity]} (parse-cli-args args)
        env {}]
    (case action
      :print-help (println msg)
      :print-errors (println msg)
      :print-template (println msg)
      :extract
      (binding [l/*verbose* verbosity]
        (let [extraction-spec (load-edn spec-file)]
          (if-not (exspec/extraction-spec-valid? extraction-spec)
            (println (exspec/extraction-spec-describe extraction-spec))
            (let [res (excel/extract-data-from-excel-file
                        excel-file extraction-spec env)]
              (if inspector?
                (inspector-show-sheet res)
                (print-sheet res print-format)))))))))

;; -------------------------------- playground ----------------------------------

(comment
  
  )
