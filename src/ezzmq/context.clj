(ns ezzmq.context
  (:require [clojure.set :as set]
            [wall.hack])
  (:import [org.zeromq ZMQ ZMQ$Context ZContext]))

(def ^:dynamic *context* nil)
(def ^:dynamic *context-type* :zcontext)

(defn context
  "Returns a new ZMQ context."
  []
  (case *context-type*
    :zcontext    (ZContext. 1)
    :zmq.context (ZMQ/context 1)
    (throw (Exception. (format "Invalid context type: %s" *context-type*)))))

(defprotocol Destructible
  (destroy-context! [ctx]))

(extend-protocol Destructible
  ZContext
  (destroy-context! [ctx] (.destroy ctx))

  ZMQ$Context
  (destroy-context! [context]
    ; ZMQ$Contexts don't automatically close their sockets when you terminate
    ; them; they block until you do it manually.
    ;
    ; The array of sockets is hidden inside of an inner Ctx instance, and both
    ; of these fields are private, so we have to use wallhaxx to get to them.
    (let [ctx     (wall.hack/field org.zeromq.ZMQ$Context :ctx context)
          sockets (wall.hack/field zmq.Ctx :sockets ctx)]
      (doseq [socket sockets]
        (doto socket
          (.setSocketOpt zmq.ZMQ/ZMQ_LINGER (int 0))
          (.close))))
    (.term context)))

(def ^:dynamic *before-shutdown-fns* {})
(def ^:dynamic *after-shutdown-fns* {})

(def ^:dynamic *shutting-down* #{})

(defmacro before-shutdown
  [& body]
  `(alter-var-root #'*before-shutdown-fns*
                   update *context* conj (fn [] ~@body)))

(defmacro after-shutdown
  [& body]
  `(alter-var-root #'*after-shutdown-fns*
                   update *context* conj (fn [] ~@body)))

(defn init-context!
  [ctx]
  (alter-var-root #'*before-shutdown-fns* assoc ctx [])
  (alter-var-root #'*after-shutdown-fns* assoc ctx []))

(defn shut-down-context!
  [ctx]
  (when-not (*shutting-down* ctx)
    (do
      (alter-var-root #'*shutting-down* conj ctx)
      (let [before-fns (get *before-shutdown-fns* ctx [])
            after-fns  (get *after-shutdown-fns* ctx [])]
        (doseq [f before-fns] (f))
        (destroy-context! ctx)
        (doseq [f after-fns] (f)))
      (alter-var-root #'*before-shutdown-fns* dissoc ctx)
      (alter-var-root #'*after-shutdown-fns* dissoc ctx)
      (alter-var-root #'*shutting-down* disj ctx))))

(defn shut-down! []
  (doseq [ctx (set/union (set (keys *before-shutdown-fns*))
                              (set (keys *after-shutdown-fns*)))]
    (shut-down-context! ctx)))

;; Registers `shut-down!` as hook to be run when the active runtime closes.
(defn enable-shut-down-on-exit []
  (.addShutdownHook (Runtime/getRuntime)
    (Thread. ^Runnable shut-down!)))

(defmacro with-context
  "Executes `body` given an existing ZMQ context `ctx`.

   When done, closes all sockets and destroys the context."
  [ctx & body]
  `(binding [*context* ~ctx]
     (init-context! *context*)
     (let [result# (do ~@body)]
       (shut-down-context! *context*)
       result#)))

(defmacro with-new-context
  "Executes `body` using a one-off ZMQ context.

   When done, closes all sockets and destroys the context."
  [& body]
  `(with-context (context) ~@body))

