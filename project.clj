(defproject totetete "0.1.0-SNAPSHOT"
  :description "Record jvm execution traces"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.3"]
                 [functionalbytes/mount-lite "2.3.0"]]
  :jvm-opts ["--add-modules" "jdk.jdi" "--add-opens" "jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED"]
  :main totetete.core
  :repl-options {:init-ns totetete.core})
