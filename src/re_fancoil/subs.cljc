(ns re-fancoil.subs
 (:require
  [re-fancoil.lib.utils     :refer [first-in-vector]]
  [re-fancoil.lib.interop   :refer [add-on-dispose! debug-enabled? make-reaction ratom? deref? dispose! reagent-id]]
  [re-fancoil.global.trace     :as trace :include-macros true]
  [re-fancoil.global.loggers     :refer [console]]
  [re-fancoil.registrar :refer [get-handler clear-handlers register-handler]]
  [integrant.core :as ig]))

(def kind :sub)
(assert (re-fancoil.registrar/kinds kind))

;; -- cache -------------------------------------------------------------------
;;
;; De-duplicate subscriptions. If two or more equal subscriptions
;; are concurrently active, we want only one handler running.
;; Two subscriptions are "equal" if their query vectors test "=".

;; (def query->reaction (atom {}))

(defn clear-subscription-cache!
  "calls `on-dispose` for each cached item, 
   which will cause the value to be removed from the cache" 
  [{:keys [query->reaction] :as self}]
  (doseq [[k rxn] @query->reaction]
    (dispose! rxn))
  (if (not-empty @query->reaction)
    (console :warn "re-frame: The subscription cache isn't empty after being cleared")))

(defn clear-all-handlers!
  "Unregisters all existing subscription handlers"
  [{:keys [registrar] :as self}]
  (clear-handlers registrar kind)
  (clear-subscription-cache! self))

(defn cache-and-return
  "cache the reaction r"
  [{:keys [query->reaction] :as self} query-v dynv r]
  (let [cache-key [query-v dynv]]
    ;; when this reaction is no longer being used, remove it from the cache
    (add-on-dispose! r #(trace/with-trace {:operation (first-in-vector query-v)
                                           :op-type   :sub/dispose
                                           :tags      {:query-v  query-v
                                                       :reaction (reagent-id r)}}
                                          (swap! query->reaction
                                                 (fn [query-cache]
                                                   (if (and (contains? query-cache cache-key) (identical? r (get query-cache cache-key)))
                                                     (dissoc query-cache cache-key)
                                                     query-cache)))))
    ;; cache this reaction, so it can be used to deduplicate other, later "=" subscriptions
    (swap! query->reaction (fn [query-cache]
                             (when debug-enabled?
                               (when (contains? query-cache cache-key)
                                 (console :warn "re-frame: Adding a new subscription to the cache while there is an existing subscription in the cache" cache-key)))
                             (assoc query-cache cache-key r)))
    (trace/merge-trace! {:tags {:reaction (reagent-id r)}})
    r)) ;; return the actual reaction

(defn cache-lookup
  ([self query-v]
   (cache-lookup self query-v []))
  ([{:keys [query->reaction] :as self} query-v dyn-v]
   (get @query->reaction [query-v dyn-v])))


;; -- subscribe ---------------------------------------------------------------

(defn subscribe
  ([{:keys [app-db registrar] :as self} query]
   (trace/with-trace {:operation (first-in-vector query)
                      :op-type   :sub/create
                      :tags      {:query-v query}}
     (if-let [cached (cache-lookup self query)]
       (do
         (trace/merge-trace! {:tags {:cached?  true
                                     :reaction (reagent-id cached)}})
         cached)

       (let [query-id   (first-in-vector query)
             handler-fn (get-handler registrar kind query-id)]
         (trace/merge-trace! {:tags {:cached? false}})
         (if (nil? handler-fn)
           (do (trace/merge-trace! {:error true})
               (console :error (str "re-frame: no subscription handler registered for: " query-id ". Returning a nil subscription.")))
           (cache-and-return self query [] (handler-fn app-db query)))))))

  ([{:keys [app-db registrar] :as self} query dynv]
   (trace/with-trace {:operation (first-in-vector query)
                      :op-type   :sub/create
                      :tags      {:query-v query
                                  :dyn-v   dynv}}
     (if-let [cached (cache-lookup query dynv)]
       (do
         (trace/merge-trace! {:tags {:cached?  true
                                     :reaction (reagent-id cached)}})
         cached)
       (let [query-id   (first-in-vector query)
             handler-fn (get-handler registrar kind query-id)]
         (trace/merge-trace! {:tags {:cached? false}})
         (when debug-enabled?
           (when-let [not-reactive (not-empty (remove ratom? dynv))]
             (console :warn "re-frame: your subscription's dynamic parameters that don't implement IReactiveAtom:" not-reactive)))
         (if (nil? handler-fn)
           (do (trace/merge-trace! {:error true})
               (console :error (str "re-frame: no subscription handler registered for: " query-id ". Returning a nil subscription.")))
           (let [dyn-vals (make-reaction (fn [] (mapv deref dynv)))
                 sub      (make-reaction (fn [] (handler-fn app-db query @dyn-vals)))]
             ;; handler-fn returns a reaction which is then wrapped in the sub reaction
             ;; need to double deref it to get to the actual value.
             ;(console :log "Subscription created: " v dynv)
             (cache-and-return self query dynv (make-reaction (fn [] @@sub))))))))))

;; -- reg-sub -----------------------------------------------------------------

(defn- map-vals
  "Returns a new version of 'm' in which 'f' has been applied to each value.
  (map-vals inc {:a 4, :b 2}) => {:a 5, :b 3}"
  [f m]
  (into (empty m)
        (map (fn [[k v]] [k (f v)]))
        m))

(defn map-signals
  "Runs f over signals. Signals may take several
  forms, this function handles all of them."
  [f signals]
  (cond
    (sequential? signals) (map f signals)
    (map? signals) (map-vals f signals)
    (deref? signals) (f signals)
    :else '()))

(defn to-seq
  "Coerces x to a seq if it isn't one already"
  [x]
  (if (sequential? x)
    x
    (list x)))

(defn- deref-input-signals
  [signals query-id]
  (let [dereffed-signals (map-signals deref signals)]
    (cond
      (sequential? signals) (map deref signals)
      (map? signals) (map-vals deref signals)
      (deref? signals) (deref signals)
      :else (console :error "re-frame: in the reg-sub for" query-id ", the input-signals function returns:" signals))
    (trace/merge-trace! {:tags {:input-signals (doall (to-seq (map-signals reagent-id signals)))}})
    dereffed-signals))


(defn reg-sub
  [{:keys [registrar app-db] :as self} query-id & args]
  (let [computation-fn (last args)
        input-args     (butlast args) ;; may be empty, or one signal fn, or pairs of  :<- / vector
        err-header     (str "re-frame: reg-sub for " query-id ", ")
        inputs-fn      (case (count input-args)
                         ;; no `inputs` function provided - give the default
                         0 (fn
                             ([_] app-db)
                             ([_ _] app-db))

                         ;; a single `inputs` fn
                         1 (let [f (first input-args)]
                             (when-not (fn? f)
                               (console :error err-header "2nd argument expected to be an inputs function, got:" f))
                             (fn [query-vec]
                               (let [input-query-vs (f query-vec)
                                     subscriptions (map (fn [query-v] (subscribe self query-v)) input-query-vs)]
                                 subscriptions)))

                         ;; one sugar pair
                         2 (let [[marker vec] input-args]
                             (when-not (= :<- marker)
                               (console :error err-header "expected :<-, got:" marker))
                             (fn inp-fn
                               ([_] (subscribe self vec))
                               ([_ _] (subscribe self vec))))

                         ;; multiple sugar pairs
                         (let [pairs   (partition 2 input-args)
                               markers (map first pairs)
                               vecs    (map second pairs)]
                           (when-not (and (every? #{:<-} markers) (every? vector? vecs))
                             (console :error err-header "expected pairs of :<- and vectors, got:" pairs))
                           (fn inp-fn
                             ([_] (map (partial subscribe self) vecs))
                             ([_ _] (map (partial subscribe self) vecs)))))]
    (register-handler
     registrar
     kind
     query-id
     (fn subs-handler-fn
       ([db query-vec]
        (let [subscriptions (inputs-fn query-vec nil)
              reaction-id   (atom nil)
              reaction      (make-reaction
                             (fn []
                               (trace/with-trace {:operation (first-in-vector query-vec)
                                                  :op-type   :sub/run
                                                  :tags      {:query-v    query-vec
                                                              :reaction   @reaction-id}}
                                 (let [subscription (computation-fn (deref-input-signals subscriptions query-id) query-vec)]
                                   (trace/merge-trace! {:tags {:value subscription}})
                                   subscription))))]

          (reset! reaction-id (reagent-id reaction))
          reaction))
       ([db query-vec dyn-vec]
        (let [subscriptions (inputs-fn query-vec dyn-vec)
              reaction-id   (atom nil)
              reaction      (make-reaction
                             (fn []
                               (trace/with-trace {:operation (first-in-vector query-vec)
                                                  :op-type   :sub/run
                                                  :tags      {:query-v   query-vec
                                                              :dyn-v     dyn-vec
                                                              :reaction  @reaction-id}}
                                 (let [subscription (computation-fn (deref-input-signals subscriptions query-id) query-vec dyn-vec)]
                                   (trace/merge-trace! {:tags {:value subscription}})
                                   subscription))))]

          (reset! reaction-id (reagent-id reaction))
          reaction))))))



(defmethod ig/init-key :re-fancoil/subs
  [_ {:keys [app-db registrar sub-chains]}]
  (println "re-fancoil.registrar/subs: starting")
  (let [self {:query->reaction (atom {})
              :registrar registrar
              :app-db app-db}]
    (when sub-chains
      (doseq [[id chain] sub-chains]
        (apply reg-sub self id chain)))
    (-> self
        (assoc :subscribe-fn (partial subscribe self)))))
