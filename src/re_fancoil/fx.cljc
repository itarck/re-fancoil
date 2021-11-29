(ns re-fancoil.fx
  (:require
   [re-fancoil.global.trace :as trace :include-macros true]
   [re-fancoil.global.loggers     :refer [console]]
   [re-fancoil.lib.interceptor :refer [->interceptor]]
   [re-fancoil.registrar   :refer [get-handler clear-handlers register-handler]]
   ))


;; -- Registration ------------------------------------------------------------

(def kind :fx)
(assert (re-fancoil.registrar/kinds kind))

(defn reg-fx
  [registrar id handler]
  (register-handler registrar kind id handler))

;; -- Interceptor -------------------------------------------------------------

;; Change do-fx to create-do-fx

(defn load-do-fx

  "An interceptor whose `:after` actions the contents of `:effects`. As a result,
  this interceptor is Domino 3.

  This interceptor is silently added (by reg-event-db etc) to the front of
  interceptor chains for all events.

  For each key in `:effects` (a map), it calls the registered `effects handler`
  (see `reg-fx` for registration of effect handlers).

  So, if `:effects` was:
      {:dispatch  [:hello 42]
       :db        {...}
       :undo      \"set flag\"}

  it will call the registered effect handlers for each of the map's keys:
  `:dispatch`, `:undo` and `:db`. When calling each handler, provides the map
  value for that key - so in the example above the effect handler for :dispatch
  will be given one arg `[:hello 42]`.

  You cannot rely on the ordering in which effects are executed, other than that
  `:db` is guaranteed to be executed first."
  [registrar]

  (->interceptor
   :id :do-fx
   :after (fn do-fx-after
            [context]
            (trace/with-trace
              {:op-type :event/do-fx}
              (let [effects            (:effects context)
                    effects-without-db (dissoc effects :db)]
                 ;; :db effect is guaranteed to be handled before all other effects.
                (when-let [new-db (:db effects)]
                  ((get-handler registrar kind :db false) new-db))
                (doseq [[effect-key effect-value] effects-without-db]
                  (if-let [effect-fn (get-handler registrar kind effect-key false)]
                    (effect-fn effect-value)
                    (console :warn "re-frame: no handler registered for effect:" effect-key ". Ignoring."))))))))
