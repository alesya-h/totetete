(ns totetete.core
  (:require
   [totetete.mount-exit]
   [totetete.events] ;; event handling setup and the receive loop thread
   [mount.lite :as m])
  (:gen-class))

(defn -main [& args]
  (m/start)
  (println "started"))