(ns totetete.vm
  (:require
   [clojure.string :as str])
  (:import
   (com.sun.jdi Bootstrap)))

(def vm-manager (Bootstrap/virtualMachineManager))

(def connectors
  (->> vm-manager .attachingConnectors seq
       (map (fn [x] [(str/replace (.name x) "com.sun.jdi." "") x])) (into {})))
