(ns qdzo.ex-tractor.excel
  (:gen-class)
  (:require
    [clojure.java.io :as io]
    [clojure.string :as s]
    [dk.ative.docjure.spreadsheet :as excel]
    [qdzo.ex-tractor.utils :refer [resolve-fn]]
    [qdzo.ex-tractor.log :as l]))


(defn extraction-spec-template
  "Reads spec-template file from resources" []
  (slurp (io/resource "extraction_schema.edn")))


(defn parse-column-mappings [schema]
  (->> (remove :env schema)
       (map (juxt :column :map-to))
       (into (sorted-map))))


(defn parse-column-schema
  "transforms
    [{:is 'int?    :map-to :age}
     {:is 'string? :map-to :name}]
  into
     {:name #'clojure.core/string?
      :age  #'clojure.core/int?}"
  [schema]
  (->> (filter :is schema)
       (map #(update % :is resolve-fn))
       (map (juxt :map-to :is))
       (into {})))

;; (= (parse-column-schema
;;     [{:is 'int? :map-to "player"}
;;      {:is 'string? :map-to "A"}])
;;    {"player" `int? "A" string?})


(defn parse-grouped-columns [schema]
  (->> (filter :group schema)
       (map :map-to)
       (into #{})))


(defn parse-extra-columns [schema]
  (->> (filter #(or (:row %) (:env %)) schema)
       (map #(if (:column %) (assoc % :column (:map-to %)) %))
       (map (juxt :map-to #(select-keys % [:row :column :env])))
       (into {})))


(defn parse-transforms [schema]
  (->> (filter :transform schema)
       (map #(update % :transform resolve-fn))
       (map (juxt :map-to :transform))
       (into {})))


(defn parse-sorting [schema]
  (-> (cons :row/id (map :map-to schema))
      (zipmap (range))))


(defn parse-extraction-schema [schema]
  {:column-mappings (parse-column-mappings schema)
   :column-schema   (parse-column-schema schema)
   :grouped-columns (parse-grouped-columns schema)
   :extra-columns   (parse-extra-columns schema)
   :transforms      (parse-transforms schema)
   :sorting         (parse-sorting schema)})


(defn parse-sheet-extraction-spec [spec]
  (merge (dissoc spec :extraction-schema)
         (parse-extraction-schema (:extraction-schema spec))))


;; ------------------------------ TRANSFORMATIONS ---------------------


(defn bind-row-id-meta-to-rows
  "Binds row/id metadata to rows"
  [sheet]
  (map (fn [row id]
         (if row (vary-meta row assoc :row/id id)))
       sheet
       (iterate inc 1))) ;; row-num start from 1


;(meta (first (bind-row-id-meta-to-rows [[] []])))
;(meta (second (bind-row-id-meta-to-rows [[] []])))


(defn materialize-row-meta
  "Fill `row-id` and other fields from `row` meta-data.
   Used to export meta-data"
  [row] (merge row (meta row)))


(defn take-range [[start end] coll]
  (let [all-or-part #(if end (take (- end start) %) %)]
    (->> (drop start coll) (all-or-part) (vec))))


(defn extra-column-getter
  "Get sheet and env and returns extra-column-getter"
  [sheet envs]
  (fn [[key {:keys [column row env]}]]
    (cond (and row column) [key (get-in sheet [row column])]
          env              [key (get envs env)])))


(defn get-extra-columns
  "gets excel-spec with `extra-columns` `sheet` and `env`.
   Returns map with `extra-columns` values"
  [extra-columns sheet envs]
  (->> extra-columns
       (map (extra-column-getter sheet envs))
       (into {})))


(defn- try-update!
  "Try apply `update` fn for args `m` `k` `f`.
   If update fails: track error and mark `m` as `invalid`"
  [m k f]
  (try (update m k f)
       (catch Throwable e
         (l/log4 [m k f (.getMessage e)])
         (vary-meta m assoc :row/invalid (.getMessage e)))))


(defn transform-columns
  "get row and map with transform functions
  returns new-row with transformed columns and errors that occured in transformation"
  [transforms row]
  (reduce-kv try-update! row transforms))


(defn valid-row?
  "row validation predicate?"
  [row] (not (contains? (meta row) :row/invalid)))


(defn- first-or-second
  "takes `x`, `y` if `x` is nil or 'blank string' returns `y`, otherwise `x`"
  [x y]
  (cond (nil? x) y
        (and (string? x)
             (s/blank? x)) y
        :default x))


(defn- fill-group-column-values-step
  "Fill missed group column values, compute new-group-column-values and returns pair
   * row with filled columns
   * group-column-values with potentially new values from row"
  [row group-column-values]
  (let [filled-row (merge-with first-or-second
                               row
                               group-column-values)
        new-group (select-keys filled-row
                               (keys group-column-values))]
    [filled-row new-group]))


(defn- fill-group-columns-reduction
  "Adapter of `fill-group-column-values-step` to reduce fn"
  [[rows group-columns] row]
  (let [[filled-row new-group]
        (fill-group-column-values-step row group-columns)]
    [(conj rows filled-row) new-group]))


(defn check-row
  "Check `row` (map) for given `spec`. Spec is a map of [key pred]
   If `row` is conforms `spec` returns `nil`. If none - returns `errors`"
  [row spec]
  (not-empty
   (reduce
    (fn [errors [k pred]]
      (let [val (get row k) conform? #(l/try-apply pred val)]
        (cond (nil? val) (conj errors [k val 'not-nil?])
              (conform?) errors
              :else      (conj errors [k val (l/fn-name pred)]))))
    []
    spec)))


(defn check-row-spec-and-then-run-pred
  "Check `row` for given `spec`. If `row` is conforms
   `spec` calls `pred` fn on it"
  [row spec pred]
  (let [errors (check-row row spec)]
    (if errors
      (do (l/log3 "Invalid row [" (:row/id (meta row)) "], errors:" errors) false)
      (pred row))))

(defn custom-sorted-map [sorting-map map]
  (let [cmp #(compare (sorting-map %1) (sorting-map %2))
        sorted-map (sorted-map-by cmp)
        sorted-map-with-meta (with-meta sorted-map (meta map))]
    (into sorted-map-with-meta map)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn extract-data-from-sheet
  "Extract and transform  data from `sheet` according `sheet-spec`,
   which defines data transformations and selections"
  [sheet-rows {:keys [column-spec
                      grouped-columns
                      extra-columns
                      data-rows-range
                      transforms
                      sorting
                      remove-row-fn] :as sheet-spec} env]
  (l/log2 "All rows: [" (count sheet-rows) "]")
  (let [remove-row-fn               (resolve-fn remove-row-fn)
        grouped+extra-columns       (apply conj grouped-columns (keys extra-columns))
        columns-spec-1-part         (apply dissoc column-spec grouped+extra-columns)
        columns-spec-2-part         (select-keys column-spec grouped+extra-columns)
        extra-column-values         (get-extra-columns extra-columns sheet-rows env)
        rows                        (bind-row-id-meta-to-rows sheet-rows)
        ;; TODO maybe check extra-columns spec here? +check once. -what to do if spec fails here?
        rows                        (take-range data-rows-range rows)
        _                           (l/log2 "Selected (by data-rows-range) rows: [" (count rows) "]")
        check-and-filter-row        #(check-row-spec-and-then-run-pred % columns-spec-1-part (complement remove-row-fn))
        rows                        (filter check-and-filter-row rows)
        _                           (l/log2 "Cleaned (by check-row-spec and remove-row-fn) rows: [" (count rows) "]")
        empty-rows []
        initial-group-column-values (-> (first rows) (select-keys grouped-columns))]
    (->> rows
         (reduce fill-group-columns-reduction
                 [empty-rows initial-group-column-values])
         (first) ;; reduce returns [rows group-column-values] - we need only rows
         (map #(merge % extra-column-values))
         (remove #(check-row % columns-spec-2-part))
         (map #(transform-columns transforms %))
         (mapv #(custom-sorted-map sorting %)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; {:rows :spec :env}

(defn prepare-process
  [{:keys [sheet-spec] :as ctx}]
  (let [{:keys [remove-row-fn grouped-columns extra-columns column-spec]} sheet-spec
        grouped+extra-columns (apply conj grouped-columns (keys extra-columns))]
    (update ctx :sheet-spec merge
            {:remove-row-fn       (resolve-fn remove-row-fn)
             :columns-spec-1-part (apply dissoc column-spec grouped+extra-columns)
             :columns-spec-2-part (select-keys column-spec grouped+extra-columns)})))

(defn prepare-extra-columns-values
  [{:keys [sheet-spec sheet-rows env] :as ctx}]
  (assoc-in ctx [:sheet-spec :extra-column-values]
            (get-extra-columns (:extra-columns sheet-spec)
                               sheet-rows env)))

(defn prepare-rows [ctx]
  (update ctx :sheet-rows bind-row-id-meta-to-rows))

#_(->> {:sheet-rows [{:a 1} {:a 1} {:a 1}]}
     prepare-rows
     :sheet-rows
     (mapv meta)
     (= [{:row/id 1} {:row/id 2} {:row/id 3}]))


;; TODO maybe check extra-columns spec here? +check once. -what to do if spec fails here?
(defn narrow-rows-to-range
  [{:keys [sheet-spec] :as ctx}]
  (update ctx :sheet-rows
          #(take-range (:data-rows-range sheet-spec) %)))

#_(-> {:sheet-spec {:data-rows-range [5]} :sheet-rows (vec (repeat 10 :k))}
    narrow-rows-to-range
    :sheet-rows
    (= [:k :k :k :k :k]))

#_(l/log2 "Selected (by data-rows-range) rows: ["
          (count rows) "]")

(defn check-and-filter-rows
  [{:keys [sheet-rows sheet-spec] :as ctx}]
  (let [{:keys [columns-spec-1-part remove-row-fn]} sheet-spec
        remove-row-fn (complement remove-row-fn) ;; <-- `remove-row-fn` returns `true` to remove row. we need `false`
        check-and-filter-row #(check-row-spec-and-then-run-pred % columns-spec-1-part remove-row-fn)
        filtered-rows (filter check-and-filter-row sheet-rows)]
    (l/log2 "Cleaned (by check-row-spec and remove-row-fn) rows: [" (count filtered-rows) "]")
    (assoc ctx :sheet-rows filtered-rows)))


(defn fill-group-column-values
  [{:keys [sheet-rows sheet-spec] :as ctx}]
  (let [{:keys [grouped-columns]} sheet-spec
        initial-group-column-values (select-keys (first sheet-rows) grouped-columns)
        initial [[] initial-group-column-values]]
    ;; reduce returns [rows group-column-values] - we need only rows
    (assoc ctx :sheet-rows
           (first (reduce fill-group-columns-reduction initial sheet-rows)))))


(defn merge-extra-column-values
  [{:keys [sheet-rows sheet-spec] :as ctx}]
  (let [{:keys [extra-column-values]} sheet-spec]
    (assoc ctx :sheet-rows
           (map #(merge % extra-column-values) sheet-rows))))


(defn check-rows-with-2nd-spec-part
  [{:keys [sheet-rows sheet-spec] :as ctx}]
  (let [{:keys [columns-spec-2-part]} sheet-spec]
    (assoc ctx :sheet-rows
           (remove #(check-row % columns-spec-2-part) sheet-rows))))


(defn transform-column-values
  [{:keys [sheet-rows sheet-spec] :as ctx}]
  (let [{:keys [transforms]} sheet-spec]
    (assoc ctx :sheet-rows
           (map #(transform-columns transforms %) sheet-rows))))


(defn sort-columns
  [{:keys [sheet-rows sheet-spec] :as ctx}]
  (let [{:keys [sorting]} sheet-spec]
    (assoc ctx :sheet-rows
           (mapv #(custom-sorted-map sorting %) sheet-rows))))


(defn extract-data-from-sheet-new
  "Extract and transform  data from `sheet` according `sheet-spec`,
   which defines data transformations and selections"
  [{:keys [sheet-rows sheet-spec env] :as ctx}]
  (l/log2 "All rows: [" (count sheet-rows) "]")
  (-> ctx
      (prepare-process)
      (prepare-extra-columns-values)
      (prepare-rows)
      (narrow-rows-to-range)
      (check-and-filter-rows)
      (fill-group-column-values)
      (merge-extra-column-values)
      (transform-column-values)
      (sort-columns)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn read-excel-sheet-from-file
  "Read `sheet-name` sheet from `excel-file`.
  Maps columns to given `column-mappings`.
  Also add `:row/id` meta info to rows"
  [excel-file sheet-name column-mappings]
  (l/log2 "Read excel file: [" (str excel-file) "]")
  (with-open [file-stream (io/input-stream excel-file)]
    (->> (excel/load-workbook file-stream)
         (excel/select-sheet sheet-name)
         (excel/select-columns column-mappings))))


(defn extract-data-from-excel-file
  "Extract data as rows from `excel-file`according `excel-spec`,
   which defines data transformations and selections"
  [excel-file sheet-extraction-spec env]
  (let [{:keys [column-mappings sheet-name] :as extraction-spec}
        (parse-sheet-extraction-spec sheet-extraction-spec)
        env (assoc env :filename (str excel-file))]
    (-> (read-excel-sheet-from-file excel-file
                                    sheet-name
                                    column-mappings)
        (extract-data-from-sheet extraction-spec env))))

;; ---------------------------------- PLAYGROUND ---------------------------------

(comment

  (binding [*verbose* 3]
    (let [{:keys [column-spec grouped-columns extra-columns remove-row-fn]} excel-doc-spec
          custom-spec (apply dissoc column-spec (apply conj grouped-columns (keys extra-columns)))
          inverted-remove-fn (complement remove-row-fn)]
      (println custom-spec)
      (count (filterv #(check-row-spec-and-then-run-pred % custom-spec inverted-remove-fn)
                      (take 400 data)))))


  (->> (extract-data-from-excel-file
         "example_data.xls"
         excel-sheet-spec
         {})
       ;; (prn-str)
       first
       ;second
       ;; (#(pp/pprint % (io/writer  "example_data.edn")))
       ;; (spit "example_data.edn")
       ;count
       )

  )


;; ------------------------------------------------ TESTS ---------------------------------------------------

;; first-or-second
(assert (= (first-or-second "k" 10) "k"))
(assert (= (first-or-second nil 10) 10))
(assert (= (first-or-second "" 10) 10))

;; get-extra-columns
(assert
  (= (get-extra-columns
       {:a {:row 1 :column :c} :b {:env :f}}
       [{} {:c "HELLO C"}]
       {:f "Simple f"})
     {:a "HELLO C" :b "Simple f"}))

