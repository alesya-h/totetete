(ns totetete.core
  (:require
   [totetete.connection.socket :as sc]
   [mount.lite :as m])
  (:gen-class))

(defn -main [& args]
  (m/start)
  (println "started"))