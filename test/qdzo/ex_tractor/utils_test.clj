(ns qdzo.ex-tractor.utils-test
  (:require
    [clojure.test :refer :all]
    [qdzo.ex-tractor.utils :as u]
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as g]
    [clojure.spec.test.alpha :as st]))

(set! *warn-on-reflection* true)


(deftest extract-pattern-test

  (testing "should find pattern"
    (is (= (u/extract-pattern #"[0-9]{1}" "1234") "1"))
    (is (= (u/extract-pattern #"([0-9]{2})" "1234") "12")))

  (testing "Should return nil when not found pattern"
    (is (= (u/extract-pattern #"[0-9]{1}" "") nil))))


(deftest parse-int-test
  
  (testing "Should parse string with digits"
    (is (= (u/parse-int "101") 101)))

  (testing "Should not parse string without digits"
    (is (= (u/parse-int "a") nil))))


(deftest str-extract-int-test

  (testing "Should extract int from string with digits"
    (is (= (u/str-extract-int "aa101 kjkj") 101))
    (is (= (u/str-extract-int "b01 g 10") 1)))

  (testing "Should return nil when string without digit"
    (is (= (u/str-extract-int "a") nil))))


(deftest str-extract-date-str-test

  (testing "Should extract date-str"
    (is (= (u/str-extract-date-str "10.10.2018") "10.10.2018"))
    (is (= (u/str-extract-date-str "10.10.18") "10.10.18"))
    (is (= (u/str-extract-date-str "at 01.01.01 year") "01.01.01")))

  (testing "Should return nil when not found"
    (is (= (u/str-extract-date-str "a") nil))))


(deftest str-extract-time-str-test

  (testing "Should extract time-str"
    (is (= (u/str-extract-time-str "10:10") "10:10"))
    (is (= (u/str-extract-time-str "at 10:18 pm") "10:18")))

  (testing "Should return nil when not found"
    (is (= (u/str-extract-time-str "a") nil))))


(deftest str-extract-datetime-str-test

  (testing "Should extract datetime-str"
    (is (= (u/str-extract-datetime-str "10.10.2018 08:11")
           "10.10.2018 08:11"))
    (is (= (u/str-extract-datetime-str "10.10.18 12:00")
           "10.10.18 12:00")))

  (testing "Should return nil when not found"
    (is (= (u/str-extract-datetime-str "a") nil))))

;(deftest str-extract-datetime-test
;
;  (testing "Should extract datetime-str"
;    (is (= (u/str-extract-datetime "10.10.2018 08:11")
;           "10.10.2018 08:11"))
;    (is (= (u/str-extract-datetime "10.10.18 12:00")
;           "10.10.18 12:00")))
;
;  (testing "Should return nil when not found"
;    (is (= (u/str-extract-datetime "b") nil))))


(deftest resolve-fn-test

  (testing "Should return f if it is fn"
    (is (= (u/resolve-fn int) int)))

  (testing "Should compile if f is fn form"
    (is (fn? (u/resolve-fn '(fn [x] (int x))))))

  (testing "Should resolve in ns if f is sym"
    (is (= (u/resolve-fn 'string?)
           #'clojure.core/string?)))

  (testing "Should throw exception on bad arg"
    (is (thrown? IllegalArgumentException
                 (u/resolve-fn [])))))

