(ns totetete.events
  (:require
   [totetete.connection :as conn]
   [mount.lite :as m])
  (:import
   (com.sun.jdi AbsentInformationException)
   (com.sun.jdi.request ThreadStartRequest ThreadDeathRequest MethodEntryRequest MethodExitRequest StepRequest)
   (com.sun.jdi.event Event LocatableEvent ThreadStartEvent ThreadDeathEvent MethodEntryEvent MethodExitEvent StepEvent VMStartEvent VMDisconnectEvent)))

(definline add-filters [request]
  `(doto ~request
     (.addClassFilter "payreq.*")
     #_(.addClassExclusionFilter "java.*")
     #_(.addClassExclusionFilter "javax.*")
     #_(.addClassExclusionFilter "jdk.*")
     #_(.addClassExclusionFilter "sun.*")
     #_(.addClassExclusionFilter "com.sun.*")
     #_(.addClassExclusionFilter "clojure.*")

     #_(.addClassExclusionFilter "org.apache.xerces.*")
     #_(.addClassExclusionFilter "org.postgresql.*")
     #_(.addClassExclusionFilter "com.mchange.*")
     #_(.addClassExclusionFilter "org.joda.time.*")
     #_(.addClassExclusionFilter "org.apache.logging.*")
     #_(.addClassExclusionFilter "com.amazonaws.*")
     #_(.addClassExclusionFilter "org.apache.http.*")
     #_(.addClassExclusionFilter "org.apache.commons.logging.*")
     #_(.addClassExclusionFilter "throttler.*")
     #_(.addClassExclusionFilter "camel_snake_kebab.internals.*")
     #_(.addClassExclusionFilter "com.azul.tooling.in.*")
     #_(.addClassExclusionFilter "net.sf.log4jdbc.*")
     #_(.addClassExclusionFilter "com.ctc.wstx.*")
     #_(.addClassExclusionFilter "org.codehaus.stax2.*")))

(defonce !instrumentation-started (atom false))
(defonce !instrumented-threads (atom {}))
(defonce !requests (atom #{}))

(defn register-request! [request]
  (swap! !requests conj request)
  request)

(defn unregister-request! [request]
  (when (instance? StepRequest request)
    (swap! !instrumented-threads dissoc (.thread request)))
  (.deleteEventRequest (conn/evreq-manager) request)
  (swap! !requests disj request))

(defn setup-step-request! [thread]
  (when-not (@!instrumented-threads thread)
    (.suspend thread)
    (try
      (let [request (-> (conn/evreq-manager)
                        (.createStepRequest thread StepRequest/STEP_LINE StepRequest/STEP_INTO)
                        register-request!
                        ^StepRequest identity ;; necessary type annotation, otherwise it can't access methods for adding filters
                        add-filters
                        .enable)]
        (swap! !instrumented-threads assoc thread {:step-request request}))
      (finally (.resume thread)))))

(defn setup-thread-start-request! []
  (doto (conn/evreq-manager)
    (-> .createThreadStartRequest register-request! ^ThreadStartRequest identity .enable)))

(defn setup-requests! []
  (doto (conn/evreq-manager)
    ;(-> .createThreadStartRequest register-request! ^ThreadStartRequest identity .enable)
    (-> .createThreadDeathRequest register-request! ^ThreadDeathRequest identity .enable)
    (-> .createMethodEntryRequest register-request! ^MethodEntryRequest identity add-filters .enable)
    (-> .createMethodExitRequest  register-request! ^MethodExitRequest  identity add-filters .enable))
  (run! setup-step-request! (conn/all-threads)))

#_(doto (conn/evreq-manager)
    (->> .threadStartRequests (run! register-request!))
    (->> .methodEntryRequests (run! register-request!))
    (->> .methodExitRequests (run! register-request!))
    (->> .stepRequests (run! register-request!)))

(defn remove-requests! []
  (run! unregister-request! @!requests))

(m/defstate requests-registered
  :start (setup-thread-start-request!)
  :stop (remove-requests!))

(defmacro safe-info [target method]
  `(try
     (~method ~target)
     (catch AbsentInformationException e#)))

(defprotocol EventProtocol
  (render [event])
  (process [event]))

(defn loc-str [location]
  (format "%s:%d"
          (or (safe-info location .sourcePath)
              (safe-info location .sourceName)
              "unknown")
          (or (safe-info location .lineNumber) 0)))

(defn meth-str [method]
  (format "%s.%s"
          (-> method .declaringType .name)
          (-> method .name)))

(extend-protocol EventProtocol

  ThreadStartEvent
  (render [event] (format "Started thread: %s" (-> event .thread .name)))
  (process [event]
    (let [t (.thread event)]
      (when (and (= (.name t) "totetete-start") (not @!instrumentation-started))
        (reset! !instrumentation-started true)
        (setup-requests!))
      (when (= (.name t) "totetete-stop")
        (reset! !instrumentation-started false)
        (m/stop))
      (when @!instrumentation-started
        (setup-step-request! t))
      (.resume t)))

  ThreadDeathEvent
  (render [event] (format "Terminated thread: %s" (-> event .thread .name)))
  (process [event]
    (unregister-request! (get-in @!instrumented-threads [(.thread event) :step-request])))

  MethodEntryEvent
  (render [event] (format "%s -> %s(%s)" (-> event .thread .name) (-> event .location loc-str) (-> event .method meth-str)))
  (process [event] nil)

  MethodExitEvent
  (render [event] (format "%s <- %s(%s)" (-> event .thread .name) (-> event .location loc-str) (-> event .method meth-str)))
  (process [event] nil)

  StepEvent
  (render [event] (format "%s -- %s(%s)" (-> event .thread .name) (-> event .location loc-str) (-> event .location .method meth-str) #_(-> event .thread (.frame 0) str) ))
  (process [event] nil)

  VMDisconnectEvent
  (render [event] (str event))
  (process [event] (m/stop))

  LocatableEvent
  (render [event] (format "loc: %s" (-> event .location loc-str)))
  (process [event] nil)

  Event
  (render [event] (str event))
  (process [_event] nil))

(defn on-event [e]
  (try
    (println (render e))
    (process e)
    (catch Exception e (prn e))))

(defn on-event-set [es] (run! on-event es))

(defonce !receiver-should-be-running (atom false))

(defn run-receiver! []
  (try
    (reset! !receiver-should-be-running true)
    (while @!receiver-should-be-running
      (-> (conn/ev-queue)
          (.remove #_:timeout-ms 1000)
          on-event-set)
      (when @!receiver-should-be-running (conn/resume!)))
    (catch Exception e (prn {:fatal-exception e}))))

;(def receiver-thread (future (run-receiver!)))
;receiver-thread
;(conn/resume!)

(m/defstate !receiver-thread
  :start (future (run-receiver!))
  :stop (reset! !receiver-should-be-running false))