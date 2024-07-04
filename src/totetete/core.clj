(ns totetete.core
  (:require
   [mount.lite :as m])
  (:gen-class))

(defn -main [& args]
  (m/start)
  (println "started"))
