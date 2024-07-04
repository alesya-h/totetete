(ns totetete.connection.socket
  (:require
   [totetete.vm :as vm]))

(def socket-connector (vm/connectors "SocketAttach"))

(defn attach! []
  (let [args (.defaultArguments socket-connector)]
    (-> args (.get "port") (.setValue "5005"))
    (-> args (.get "hostname") (.setValue "localhost"))
    (.attach socket-connector args)))