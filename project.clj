(defproject ex-tractor "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [dk.ative/docjure "1.12.0"]
                 [org.clojure/data.csv "0.1.4"]
                 ;[http-kit "2.3.0"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.cli "0.4.1"]]
  :main ^:skip-aot qdzo.ex-tractor.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:plugins [[lein-binplus "0.6.4"]
                             [lein-nvd "0.5.6"]
                             [jonase/eastwood "0.3.3"]]
                   :dependencies [[org.clojure/test.check "0.9.0"]
                                  [orchestra "2018.12.06-2"]]}})
