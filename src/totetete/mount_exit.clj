(ns totetete.mount-exit
  (:require
   [mount.lite :as m]))

(m/defstate exit :start nil :stop (System/exit 0))