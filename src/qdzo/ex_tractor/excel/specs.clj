(ns qdzo.ex-tractor.excel.specs
  (:require [qdzo.ex-tractor.excel :as excel]
            [clojure.set :as cset]
            [clojure.spec.alpha :as spec]
            [clojure.spec.gen.alpha :as gen])
  (:import [java.io File]))


(defn gen [spec]
  (gen/generate (spec/gen spec)))

(set! *warn-on-reflection* true)

(spec/def ::excel/env-name
  (spec/or :k keyword? :s symbol?))


(spec/def ::excel/env-val any?)


(spec/def ::excel/env
  (spec/map-of ::excel/env-name ::excel/env-val))


(spec/def ::excel/excel-sheet-column-name
  (sorted-set
   :A  :B  :C  :D  :E  :F  :G  :H  :I  :J  :K  :L  :M  :N  :O  :P  :Q  :R  :S  :T  :U  :V  :W  :X  :Y  :Z
   :AA :AB :AC :AD :AE :AF :AG :AH :AI :AJ :AK :AL :AM :AN :AO :AP :AQ :AR :AS :AT :AU :AV :AW :AX :AY :AZ
   :BA :BB :BC :BD :BE :BF :BG :BH :BI :BJ :BK :BL :BM :BN :BO :BP :BQ :BR :BS :BT :BU :BV :BW :BX :BY :BZ
   :CA :CB :CC :CD :CE :CF :CG :CH :CI :CJ :CK :CL :CM :CN :CO :CP :CQ :CR :CS :CT :CU :CV :CW :CX :CY :CZ))


(spec/def ::excel/cell-value
  (spec/or :nil nil?
           :boolean boolean?
           :float float?
           :string string?))


(spec/def ::excel/column-mapped-name
  (spec/or :sym symbol?
           :key keyword?
           :str string?))


(spec/def ::excel/sheet-row map?)


(spec/def ::excel/sheet
  (spec/coll-of ::excel/sheet-row :into []))


(spec/def :extra-columns.coordinate.env/env ::excel/env-name)


(spec/def :extra-columns.coordinate/env
  (spec/keys :req-un [:extra-columns.coordinate.env/env]))


(spec/def :extra-columns.coordinate.cell/row pos-int?)


(spec/def :extra-columns.coordinate.cell/column
  ::excel/column-mapped-name)


(spec/def :extra-columns.coordinate/cell
  (spec/keys :req-un [:extra-columns.coordinate.cell/row
                      :extra-columns.coordinate.cell/column]))


(spec/def :extra-columns/coordinate
  (spec/or :env :extra-columns.coordinate/env
           :cell :extra-columns.coordinate/cell))


(spec/def :sheet-extraction-spec/extra-columns
  (spec/map-of ::excel/column-mapped-name
               :extra-columns/coordinate))


(spec/fdef ::excel/cell-predicate
           :args (spec/cat :value ::excel/cell-value)
           :ret boolean?)


(spec/def ::excel/fn
  (spec/with-gen fn?
    #(spec/gen #{int? float? int float identity})))


(def fn-forms
  #{'(fn [] nil)
    '(fn [x] x)
    '(fn [& xs] true)
    '(fn [& xs] xs)})


(spec/def ::excel/fn-form
  (spec/with-gen (spec/and list? #(= (first %) 'fn))
                 #(spec/gen fn-forms)))


(spec/def ::excel/fn-like
    (spec/or :fn ::excel/fn
             :form ::excel/fn-form
             :symbol symbol?))


  (spec/def :sheet-extraction-spec/column-mappings
  (spec/map-of ::excel/excel-sheet-column-name
               ::excel/column-mapped-name))


(spec/def :sheet-extraction-spec/column-schema
  (spec/map-of ::excel/column-mapped-name
               ::excel/cell-predicate))


(spec/def :sheet-extraction-spec/grouped-columns
  (spec/coll-of ::excel/column-mapped-name :into #{}))


(spec/def ::excel/sheet-name string?)


(spec/def ::excel/sheet-spec
  (spec/keys :req-un [::excel/sheet-name]))


(spec/def :sheet-extraction-spec.data-rows-range/start
  (spec/cat :start pos-int?))


(spec/def :sheet-extraction-spec.data-rows-range/start+end
  (spec/and (spec/cat :start pos-int? :end pos-int?)
            #(< (:start %) (:end %))))


(spec/def :sheet-extraction-spec/data-rows-range
  (spec/or
    :start     :sheet-extraction-spec.data-rows-range/start
    :start+end :sheet-extraction-spec.data-rows-range/start+end))


(spec/def :sheet-extraction-spec/transforms
  (spec/map-of ::excel/column-mapped-name
               ::excel/fn-like))


(spec/fdef ::excel/row-predicate
  :args (spec/cat :row ::excel/sheet-row)
  :ret boolean?)


(spec/def :sheet-extraction-spec/remove-row-fn
  ::excel/row-predicate)


(spec/def ::excel/sheet-extraction-spec
  (spec/keys :req-un [:sheet-extraction-spec/column-mappings]
             :opt-un [:sheet-extraction-spec/grouped-columns
                      :sheet-extraction-spec/extra-columns
                      :sheet-extraction-spec/data-rows-range
                      :sheet-extraction-spec/transforms
                      :sheet-extraction-spec/remove-row-fn]))


;(gen ::excel/sheet-extraction-spec)


(spec/def ::excel/sheet-spec+transformations
  (spec/merge ::excel/sheet-spec ::excel/sheet-extraction-spec))

;; :doc/sheets
;; :doc/sheet
;; :sheet/rows
;; :sheet/row
;; ;; -----------
;; :row/id
;; :row/column
;; ;; -----------
;; :sheet/columns
;; :sheet/column
;; ;; -----------
;; :column/name
;; :column/row
;; :column/value

;; {:column-name :E
;;  :map-to :datetime
;;  :is string?
;;  :transform-fn str-extract-datetime
;;  :row-id 0 }

(spec/def  :extraction-schema.item/column     ::excel/excel-sheet-column-name)
(spec/def  :extraction-schema.item/map-to     ::excel/column-mapped-name)
(spec/def  :extraction-schema.item/is         ::excel/cell-predicate)
(spec/def  :extraction-schema.item/transform  ::excel/fn-like)
(spec/def  :extraction-schema.item/row        :extra-columns.coordinate.cell/row)
(spec/def  :extraction-schema.item/group      true?)
(spec/def  :extraction-schema.item/env        ::excel/env-name)


(spec/def :extraction-schema/item
  (spec/keys :req-un [:extraction-schema.item/column
                      :extraction-schema.item/map-to]
             :opt-un [:extraction-schema.item/is
                      :extraction-schema.item/transform
                      :extraction-schema.item/row
                      :extraction-schema.item/group
                      :extraction-schema.item/env]))


(spec/def :extraction-schema/extraction-schema
  (spec/coll-of :extraction-schema/item :into []))


(spec/def ::excel/extraction-schema
  (spec/keys :req-un [:extraction-schema/extraction-schema]))


(spec/def ::excel/sheet-spec+extraction-schema
  (spec/merge ::excel/sheet-spec ::excel/extraction-schema))


(spec/fdef excel/parse-extraction-schema
           :args (spec/cat :schema :extraction-schema/schema)
           :ret ::excel/sheet-spec+transformations)

;; ------------------- utils func --------------------

(defn extraction-spec-valid? [extraction-spec]
  true)

(defn extraction-spec-describe [extraction-spec]
  "Nothing here yet")

;; -------------- end of utils func ------------------


(spec/fdef excel/take-range
           :args (spec/cat
                   :range :sheet-extraction-spec/data-rows-range
                   :coll coll?)
           :ret vector?
           :fn #(let [init-count (-> % :args :coll count)
                      ret-count (-> % :ret count)]
                  (>= init-count ret-count)))


(spec/fdef ::excel/get-extra-columns
  :args (spec/cat :extra-columns :extraction-schema/extra-columns
                  :sheet ::excel/sheet
                  :env ::excel/env)
  :ret map?
  :fn #(let [extra-columns (-> % :args :extra-columns keys set)
             ret-columns (-> % :ret keys set)]
         (if (> (count ret-columns) 0)
           (= (count (cset/intersection extra-columns ret-columns))
              (count extra-columns))
           true)))

(spec/def excel/get-extra-columns ::excel/get-extra-columns)


(spec/fdef excel/transform-columns
  :args (spec/cat :transforms :extraction-schema/transforms
                  :row ::excel/row)
  :ret ::excel/sheet-row
  :fn #(= (-> % :args :row keys)
          (-> % :ret keys)))

(spec/def excel/valid-row? ::excel/row-predicate)

(spec/def ::excel/column-error
  (spec/cat :column-name any?
            :column-value any?
            :failed-pred symbol?))

(spec/def ::excel/row-errors
  (spec/coll-of ::excel/column-error))

(spec/fdef excel/check-row
  :args (spec/cat :row ::excel/sheet-row
                  :spec ::excel/row-spec)
  :ret (spec/or :ok nil?
                :errors ::excel/row-errors))

(spec/fdef excel/check-row-spec-and-then-run-pred
  :args (spec/cat :row ::excel/sheet-row
                  :spec ::excel/row-spec
                  :pred ::excel/row-predicate)
  :ret boolean?)

(spec/fdef excel/extract-data-from-sheet
  :args (spec/cat :sheet ::excel/sheet
                  :sheet-spec ::excel/sheet-spec+transformations
                  :env ::excel/env)
  :ret ::excel/sheet)

(spec/def ::excel/excel-file
  (spec/and #(instance? ^File File %)
            #(.isFile ^File %)
            #(re-find #"\.xlsx?$" (str %)))
  #_(spec/with-gen
    (spec/and #(instance? ^File File %)
              #(.isFile ^File %)
              #(re-find #"\.xlsx?$" (str %)))
    (spec/gen #{(java.io.File. "gpn_report.xls")
                (java.io.File. "gpn_report.xlsx")
                (java.io.File. "data/examples/gpn_report_1608.xlsx")})))

(spec/fdef excel/read-excel-sheet-from-file
  :args (spec/cat :excelfile ::excel/excel-file
                  :sheet-name ::excel/sheet-name
                  :column-mappings :extraction-schema/column-mappings)
  :ret ::excel/sheet)

(spec/fdef excel/extract-data-from-excel-file
  :args (spec/cat :excelfile ::excel/excel-file
                  :sheet-spec ::excel/sheet-spec
                  :env ::excel/env)
  :ret ::excel/sheet)

;; -----------------------------------------------------------------------------------

(comment

  (stest/check `excel/check-row)

  (-> (stest/enumerate-namespace 'qdzo.ex-tractor.excel)
      stest/instrument
      ;; stest/unstrument
      )

  (stest/instrument `excel/check-row)

  (stest/unstrument `excel/check-row)

  (stest/check `excel/take-range
               {:clojure.spec.test.check/opts {:num-tests 100}})


  )

