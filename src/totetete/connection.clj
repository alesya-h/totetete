(ns totetete.connection
  (:require
   [totetete.connection.socket :as sc]
   [mount.lite :as m]))

(m/defstate !vm
  :start (sc/attach!)
  :stop (.dispose @!vm))

(defn evreq-manager [] (.eventRequestManager @!vm))
(defn ev-queue [] (.eventQueue @!vm))
(defn all-threads [] (.allThreads @!vm))
(defn suspend! [] (.suspend @!vm))
(defn resume! [] (.resume @!vm))