(ns re-fancoil.events
  (:require
   [re-fancoil.lib.utils       :refer [first-in-vector]]
   [re-fancoil.lib.interop     :refer [empty-queue debug-enabled?]]
   [re-fancoil.lib.interceptor :as  interceptor]
   [re-fancoil.global.trace       :as trace :include-macros true]
   [re-fancoil.global.loggers     :refer [console]]

   [re-fancoil.fx :as fx]
   [re-fancoil.cofx     :as cofx]
   [re-fancoil.registrar   :refer [get-handler register-handler]]
   [re-fancoil.global.std-interceptors :as std-interceptors :refer [db-handler->interceptor
                                                                    fx-handler->interceptor
                                                                    ctx-handler->interceptor]]
   [integrant.core :as ig]))


(def kind :event)
(assert (re-fancoil.registrar/kinds kind))

(defn interceptor-or-keyword? [interceptor]
  (or (interceptor/interceptor? interceptor) (keyword? interceptor)))


(defn- flatten-and-remove-nils
  "`interceptors` might have nested collections, and contain nil elements.
  return a flat collection, with all nils removed.
  This function is 9/10 about giving good error messages."
  [id interceptors]
  (let [make-chain  #(->> % flatten (remove nil?))]
    (if-not debug-enabled?
      (make-chain interceptors)
      (do    ;; do a whole lot of development time checks
        (when-not (coll? interceptors)
          (console :error "re-frame: when registering" id ", expected a collection of interceptors, got:" interceptors))
        (let [chain (make-chain interceptors)]
          (when (empty? chain)
            (console :error "re-frame: when registering" id ", given an empty interceptor chain"))
          (when-let [not-i (first (remove interceptor-or-keyword? chain))]
            (if (fn? not-i)
              (console :error "re-frame: when registering" id ", got a function instead of an interceptor. Did you provide old style middleware by mistake? Got:" not-i)
              (console :error "re-frame: when registering" id ", expected interceptors, but got:" not-i)))
          chain)))))


#_(defn register
    "Associate the given event `id` with the given collection of `interceptors`.

   `interceptors` may contain nested collections and there may be nils
   at any level,so process this structure into a simple, nil-less vector
   before registration.

   Typically, an `event handler` will be at the end of the chain (wrapped
   in an interceptor)."
    [{:keys [registrar]} id interceptors]
    (register-handler registrar kind id (flatten-and-remove-nils id interceptors)))


(defn replace-keyword-interceptors
  "if an interceptor is a keyword with format :kind/id, find it in the registrar
   only support single keyword now.
   Added by: itarck"
  [registrar interceptors]
  (map (fn [interceptor]
         (cond
           (qualified-keyword? interceptor) (let [kind (keyword (namespace interceptor))
                                                  id (keyword (name interceptor))]
                                              (assert (re-fancoil.registrar/kinds kind))
                                              (cofx/coeffect-handler->interceptor (get-handler registrar kind id)))
           (interceptor/interceptor? interceptor) interceptor
           :else (console :error "unknown interceptor type: " interceptor)))
       interceptors))


(defn reg-event
  "Associate the given event `id` with the given collection of `interceptors`.

   `interceptors` may contain nested collections and there may be nils
   at any level,so process this structure into a simple, nil-less vector
   before registration.

   Typically, an `event handler` will be at the end of the chain (wrapped
   in an interceptor)."
  [registrar id interceptors]
  (->>
   (flatten-and-remove-nils id interceptors)
   (replace-keyword-interceptors registrar)
   (register-handler registrar kind id)))


;; -- handle event --------------------------------------------------------------------------------

(def ^:dynamic *handling* nil)    ;; remember what event we are currently handling

(defn handle-event
  "Given an event vector `event-v`, look up the associated interceptor chain, and execute it."
  [registrar event-v]
  (let [event-id  (first-in-vector event-v)]
    (if-let [interceptors  (get-handler registrar kind event-id true)]
      (if *handling*
        (console :error "re-frame: while handling" *handling* ", dispatch-sync was called for" event-v ". You can't call dispatch-sync within an event handler.")
        (binding [*handling*  event-v]
          (trace/with-trace {:operation event-id
                             :op-type   kind
                             :tags      {:event event-v}}
            #_(trace/merge-trace! {:tags {:app-db-before @app-db}})
            (interceptor/execute event-v interceptors)
            #_(trace/merge-trace! {:tags {:app-db-after @app-db}})))))))


(defn reg-event-db
  "Register the given event `handler` (function) for the given `id`. Optionally, provide
  an `interceptors` chain:

    - `id` is typically a namespaced keyword  (but can be anything)
    - `handler` is a function: (db event) -> db
    - `interceptors` is a collection of interceptors. Will be flattened and nils removed.

  Example Usage:

      #!clj
      (reg-event-db
        :token
        (fn [db event]
          (assoc db :some-key (get event 2)))  ;; return updated db

  Or perhaps:

      #!clj
      (reg-event-db
        :namespaced/id           ;; <-- namespaced keywords are often used
        [one two three]          ;; <-- a seq of interceptors
        (fn [db [_ arg1 arg2]]   ;; <-- event vector is destructured
          (-> db
            (dissoc arg1)
            (update :key + arg2))))   ;; return updated db
  "
  {:api-docs/heading "Event Handlers"}
  ([registrar id handler]
   (reg-event-db registrar id nil handler))
  ([registrar id interceptors handler]
   (reg-event registrar id [:cofx/db
                            (fx/load-do-fx registrar)
                            std-interceptors/inject-global-interceptors
                            interceptors
                            (db-handler->interceptor handler)])))



(defn reg-event-fx
  "Register the given event `handler` (function) for the given `id`. Optionally, provide
  an `interceptors` chain:

    - `id` is typically a namespaced keyword  (but can be anything)
    - `handler` is a function: (coeffects-map event-vector) -> effects-map
    - `interceptors` is a collection of interceptors. Will be flattened and nils removed.


  Example Usage:

      #!clj
      (reg-event-fx
        :event-id
        (fn [cofx event]
          {:db (assoc (:db cofx) :some-key (get event 2))}))   ;; return a map of effects


  Or perhaps:

      #!clj
      (reg-event-fx
        :namespaced/id           ;; <-- namespaced keywords are often used
        [one two three]          ;; <-- a seq of interceptors
        (fn [{:keys [db] :as cofx} [_ arg1 arg2]] ;; destructure both arguments
          {:db       (assoc db :some-key arg1)          ;; return a map of effects
           :fx [[:dispatch [:some-event arg2]]]}))
  "
  {:api-docs/heading "Event Handlers"}
  ([registrar id handler]
   (reg-event-fx registrar id nil handler))
  ([registrar id interceptors handler]
   (reg-event registrar id [:cofx/db 
                            (fx/load-do-fx registrar) 
                            std-interceptors/inject-global-interceptors 
                            interceptors 
                            (fx-handler->interceptor handler)])))



(defmethod ig/init-key :re-fancoil/events
  [_ {:keys [registrar event-db-chains event-fx-chains] :as config}]
  (when event-db-chains
    (doseq [[id chain] event-db-chains]
      (case (count chain)
        1 (let [[handler] chain]
            (reg-event-db registrar id handler))
        2 (let [[interceptros handler] chain]
            (reg-event-db registrar id interceptros handler))
        nil)))
  
  (when event-fx-chains
    (doseq [[id chain] event-fx-chains]
      (case (count chain)
        1 (let [[handler] chain]
            (reg-event-fx registrar id handler))
        2 (let [[interceptros handler] chain]
            (reg-event-fx registrar id interceptros handler))
        nil)))
  )