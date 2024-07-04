(ns totetete.events
  (:require [totetete.connection :as conn])
  (:import
   (com.sun.jdi AbsentInformationException)
   (com.sun.jdi.request ThreadStartRequest ThreadDeathRequest MethodEntryRequest MethodExitRequest StepRequest)
   (com.sun.jdi.event Event LocatableEvent ThreadStartEvent ThreadDeathEvent MethodEntryEvent MethodExitEvent StepEvent VMStartEvent VMDisconnectEvent)))



(definline add-filters [request]
  `(doto ~request
     ;(.addClassFilter "app.*")
     (.addClassExclusionFilter "java.*")
     (.addClassExclusionFilter "jdk.*")
     (.addClassExclusionFilter "sun.*")
     (.addClassExclusionFilter "com.sun.*")))

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

(defn setup-requests! []
  (doto (conn/evreq-manager)
    (-> .createThreadStartRequest register-request! ^ThreadStartRequest identity .enable)
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

(setup-requests!)
(remove-requests!)

(defmacro safe-info [target method unknown-str]
  `(try
     (~method ~target)
     (catch AbsentInformationException e# ~unknown-str)))

(defn on-event [e] (prn e))

(defn on-event-set [es] (run! on-event es))

(defonce !receiver-should-be-running (atom true))

(defn run-receiver! []
  (while @!receiver-should-be-running
    (-> (conn/ev-queue)
        (.remove #_:timeout-ms 1000)
        on-event-set)
    #_(when @!receiver-should-be-running (conn/resume!))))

(defprotocol EventProtocol
  (render [event])
  (process [event]))

(defn loc-str [location]
  (format "%s:%d"
          (safe-info location .sourceName "no_source_name")
          (safe-info location .lineNumber 0)))

(defn meth-str [method]
  (format "%s.%s"
          (-> method .declaringType .name)
          (-> method .name)))

(extend-protocol EventProtocol

  ThreadStartEvent
  (render [event] (format "Started thread: %s" (-> event .thread .name)))
  (process [event] (-> event .thread setup-step-request!))

  ThreadDeathEvent
  (render [event] (format "Terminated thread: %s" (-> event .thread .name)))
  (process [event] (unregister-request! (get-in @!instrumented-threads [(.thread event) :step-request])))

  MethodEntryEvent
  (render [event] (format "-> %s(%s)" (-> event .method meth-str) (-> event .thread .name)))
  (process [event] nil)

  MethodExitEvent
  (render [event] (format "<- %s(%s)" (-> event .method meth-str) (-> event .location loc-str)))
  (process [event] nil)

  StepEvent
  (render [event] (format "-- %s(%s)" (-> event .thread (.frame 0) str) (-> event .location loc-str)))
  (process [event] nil)

  LocatableEvent
  (render [event] (format "loc: %s" (-> event .location loc-str)))
  (process [event] nil)

  Event
  (render [event] (str event))
  (process [_event] nil))

(def receiver-thread (future (run-receiver!)))
receiver-thread
(conn/resume!)