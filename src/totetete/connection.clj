(ns totetete.connection
  (:require
   [totetete.connection.socket :as sc]))

(defonce !vm (atom nil))

(defn attach! []
  (when-not @!vm
    (reset! !vm (sc/attach!))
    (.suspend @!vm)))

(defn detach! []
  (reset! !vm nil)
  (.dispose @!vm))

(detach!)
#_(attach!)

(defn evreq-manager [] (.eventRequestManager @!vm))
(defn ev-queue [] (.eventQueue @!vm))
(defn all-threads [] (.allThreads @!vm))
(defn suspend! [] (.suspend @!vm))
(defn resume! [] (.resume @!vm))