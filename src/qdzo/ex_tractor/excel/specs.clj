(ns qdzo.ex-tractor.excel.specs
  (:require [qdzo.ex-tractor.excel :as excel]
            [clojure.set :as cset]
            [clojure.spec.alpha :as spec])
  (:import [java.io File]))


(set! *warn-on-reflection* true)

;; row is pure map
(spec/def ::excel/row map?)

;; sheet is just rows list
(spec/def ::excel/sheet
  (spec/coll-of ::excel/row))

;; environment map
(spec/def ::excel/env map?)

(spec/def ::excel/sheet-column-name
  (sorted-set
   :A  :B  :C  :D  :E  :F  :G  :H  :I  :J  :K  :L  :M  :N  :O  :P  :Q  :R  :S  :T  :U  :V  :W  :X  :Y  :Z
   :AA :AB :AC :AD :AE :AF :AG :AH :AI :AJ :AK :AL :AM :AN :AO :AP :AQ :AR :AS :AT :AU :AV :AW :AX :AY :AZ
   :BA :BB :BC :BD :BE :BF :BG :BH :BI :BJ :BK :BL :BM :BN :BO :BP :BQ :BR :BS :BT :BU :BV :BW :BX :BY :BZ
   :CA :CB :CC :CD :CE :CF :CG :CH :CI :CJ :CK :CL :CM :CN :CO :CP :CQ :CR :CS :CT :CU :CV :CW :CX :CY :CZ))


(spec/def ::excel/column-name any?)

(spec/def ::excel/column-mappings
  (spec/map-of ::excel/sheet-column-name
               ::excel/column-name))

(spec/fdef ::excel/column-predicate
  :args (spec/cat :column-value any?)
  :ret boolean?)

(spec/def ::excel/row-spec
  (spec/map-of any?
               ::excel/column-predicate))

(spec/def ::excel/sheet-name string?)

(spec/def ::excel/sheet-spec
  (spec/keys :req-un [::excel/sheet-name ::excel/column-mappings]))

(spec/def ::excel/grouped-columns set?)

(spec/fdef ::excel/extra-column-extractor
  :args (spec/cat :sheet ::excel/sheet :env ::excel/env)
  :ret some?)

(spec/def ::excel/extra-columns
  (spec/map-of some? ::excel/extra-column-extractor))

(spec/def ::excel/data-rows-range
  (spec/or :start (spec/cat :start int?)
           :start+end (spec/cat :start int? :end int?)))

(spec/def ::excel/transforms
  (spec/map-of some? fn?))

(spec/fdef ::excel/row-predicate
  :args (spec/cat :row ::excel/row)
  :ret boolean?)

(spec/def ::excel/remove-row-fn ::excel/row-predicate)

(spec/def ::excel/sheet-transformations
  (spec/keys :req-un [::excel/grouped-columns
                      ::excel/extra-columns
                      ::excel/data-rows-range
                      ::excel/transforms
                      ::excel/remove-row-fn]))

(spec/def ::excel/sheet-spec+transformations
  (spec/merge ::excel/sheet-spec ::excel/sheet-transformations))

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

(spec/def  :extraction-schema/column     ::excel/column-name)
(spec/def  :extraction-schema/map-to     any?)
(spec/def  :extraction-schema/is         symbol?)
(spec/def  :extraction-schema/transform  symbol?)
(spec/def  :extraction-schema/row        pos-int?)
(spec/def  :extraction-schema/group      true?)
(spec/def  :extraction-schema/env        keyword?)

(spec/def :extraction-schema/item
  (spec/keys :opt-un [:extraction-schema/column
                      :extraction-schema/map-to
                      :extraction-schema/is
                      :extraction-schema/transform
                      :extraction-schema/row
                      :extraction-schema/group
                      :extraction-schema/env]))


(spec/def :extraction-schema/schema
  (spec/coll-of :extraction-schema/item))

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
           :args (spec/cat :range
                           (spec/or
                             :only-start
                             (spec/cat :start pos-int?)
                             :start+end
                             (spec/and (spec/cat :start pos-int? :end pos-int?)
                                       #(< (:start %) (:end %))))
                           :coll coll?)
           :ret vector?
           :fn #(let [init-count (-> % :args :coll count)
                      ret-count (-> % :ret count)]
                  (>= init-count ret-count)))


(spec/fdef ::excel/get-extra-columns
  :args (spec/cat :extra-columns ::excel/extra-columns
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
  :args (spec/cat :transforms ::excel/transforms
                  :row ::excel/row)
  :ret ::excel/row
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
  :args (spec/cat :row ::excel/row
                  :spec ::excel/row-spec)
  :ret (spec/or :ok nil?
                :errors ::excel/row-errors))

(spec/fdef excel/check-row-spec-and-then-run-pred
  :args (spec/cat :row ::excel/row
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
  :args (spec/cat :excelfile  ::excel/excel-file
                  :sheet-name ::excel/sheet-name
                  :column-mappings ::excel/column-mappings)
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

