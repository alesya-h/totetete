(ns totetete.connection.process
  (:require
   [totetete.vm :as vm]))

(def process-connector (vm/connectors "ProcessAttach"))