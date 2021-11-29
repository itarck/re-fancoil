(ns subtree.test-subtree
  (:require
   [cljs.core.async :as a :refer [go >! <!]]
   [integrant.core :as ig]
   [reagent.core :as r]
   [reagent.ratom :refer [reaction]]
   [re-fancoil.db]
   [re-fancoil.registrar :as rf.registrar]
   [re-frame.interop :refer [make-reaction]]
   [subtree.subs :as subtree.subs]
   [sieppari.core :as s]))


(defmethod ig/init-key :re-fancoil/subs.user
  [_ {:keys [subs]}]
  (rf.subs/reg-sub subs
                   :user
                   (fn [db _] (:user db))))

;; version 1: reframe version

(defmethod ig/init-key :re-fancoil/subs.value-a
  [_ {:keys [subs]}]
  (rf.subs/reg-sub subs
                   :value-a
                   (fn [db [_marker multi]] (* multi (:value-a db)))))

(defmethod ig/init-key :re-fancoil/subs.value-b
  [_ {:keys [subs]}]
  (rf.subs/reg-sub subs
                   :value-b
                   (fn [db [_marker multi]] (* multi (:value-b db)))))

(defmethod ig/init-key :re-fancoil/subs.multi-ab
  [_ {:keys [subs]}]
  (rf.subs/reg-sub subs
                   :multi-ab
                   (fn [[_sig multi]]
                     [(rf.subs/subscribe subs [:value-a multi])
                      (rf.subs/subscribe subs [:value-b multi])])
                   (fn [[a b] [_sig multi]] (* a b))))



;; version2: rf subs + integrant

(defmethod ig/init-key :re-fancoil/subs.multi-ab2
  [_ {:keys [subs handler-a handler-b]}]
  (let [handler (fn [db [_sig multi]] (* @(handler-a db [:a multi])
                                         @(handler-b db [:value-b multi])))]
    (rf.subs/reg-sub subs :multi-ab2 handler)
    handler))



(def config
  #:re-fancoil
   {:app-db {:user {:name "me"}
             :value-a 3
             :value-b 23}
    :registrar {}
    :subs {:registrar (ig/ref :re-fancoil/registrar)
           :app-db (ig/ref :re-fancoil/app-db)}
    :subs.user {:subs (ig/ref :re-fancoil/subs)}
    :subs.value-a {:subs (ig/ref :re-fancoil/subs)}
    :subs.value-b {:subs (ig/ref :re-fancoil/subs)}
    :subs.multi-ab2 {:subs (ig/ref :re-fancoil/subs)
                     :handler-a (ig/ref :re-fancoil/subs.value-a)
                     :handler-b (ig/ref :re-fancoil/subs.value-b)}})


(def system
  (ig/init config))

(comment

  system

  (def app-db
    (:re-fancoil/app-db system))

  (def registrar
    (:re-fancoil/registrar system))

  (rf.registrar/register-handler registrar :fx :hello print)
  (rf.registrar/get-handler registrar :fx :hello)


  (def subs
    (:re-fancoil/subs system))

  (rf.subs/subscribe subs [:user])

  (rf.subs/subscribe subs [:value-a 10])
  (rf.subs/subscribe subs [:value-b 10])

  (rf.subs/subscribe subs [:multi-ab2 10])


  (:query->reaction subs)

  (r/rswap! app-db assoc :user {:name "you"})
  (r/rswap! app-db assoc :value-a 100)

  app-db

  (let [sub-c-fn (:re-fancoil/subs.user system)]
    (sub-c-fn))

  @(rf.subs/subscribe subs [:a-sub])

  @(rf.subs/subscribe subs [:multi-a-sub 10])


;;   
  )


;; version3: integrant only


(defmethod ig/init-key :re-fancoil/subs-handler.value-a
  [_ _config]
  (wrap-sub (fn [db-ref [_qid multi]] (* multi (:value-a @db-ref)))))

(defmethod ig/init-key :re-fancoil/subs-handler.value-b
  [_ _config]
  (wrap-sub (fn [db-ref [_qid multi]] (* multi (:value-b @db-ref)))))

(defmethod ig/init-key :re-fancoil/subs-handler.multi-ab2
  [_ {:keys [handler-a handler-b]}]

  (wrap-sub (fn [db-ref [_qid multi]]
              (* @(handler-a db-ref [:value-a multi])
                 @(handler-b db-ref [:value-b multi])))))


(def config3
  #:re-fancoil
   {:app-db {:user {:name "me"}
             :value-a 3
             :value-b 23}
    :registrar {}
    :subs {:registrar (ig/ref :re-fancoil/registrar)
           :app-db (ig/ref :re-fancoil/app-db)}
    :subs.user {:subs (ig/ref :re-fancoil/subs)}
    :subs-handler.value-a {}
    :subs-handler.value-b {}
    :subs-handler.multi-ab2 {:handler-a (ig/ref :re-fancoil/subs-handler.value-a)
                             :handler-b (ig/ref :re-fancoil/subs-handler.value-b)}})


(def system3
  (ig/init config3))


(comment

  (def app-db (:re-fancoil/app-db system3))
  (def handler-a
    (:re-fancoil/subs-handler.value-a system3))

  (def handler-b
    (:re-fancoil/subs-handler.value-b system3))

  (def handler-ab2
    (:re-fancoil/subs-handler.multi-ab2 system3))


  (handler-a app-db [:value-a 10])
  (handler-b app-db [:value-b 20])

  (handler-ab2 app-db [:multi-ab2 10]))


;; version 4: multi method, failed

(defmulti data-sub-module
  (fn [env query-vec] (first query-vec)))

(defmethod data-sub-module :data/value-a
  [{:keys [db-ref]} query-vec]
  (let [handler (wrap-sub (fn [db-ref [_signal multi]]
                            (* multi (:value-a @db-ref))))]
    (handler db-ref query-vec)))

(defmethod data-sub-module :data/value-b
  [{:keys [db-ref]} query-vec]
  (let [handler (wrap-sub (fn [db-ref [_signal multi]]
                            (* multi (:value-b @db-ref))))]
    (handler db-ref query-vec)))

(defmethod data-sub-module :data/multi-ab
  [{:keys [db-ref]} query-vec]
  (let [handler (wrap-sub (fn [db-ref [_signal multi]]
                            (* @(data-sub-module {:db-ref db-ref} [:data/value-a multi])
                               @(data-sub-module {:db-ref db-ref} [:data/value-b multi]))))]
    (handler db-ref query-vec)))


(defmethod ig/init-key :re-fancoil/subscriber
  [_ {:keys [db-ref]}]
  (partial data-sub-module {:db-ref db-ref}))


(def config4
  #:re-fancoil
   {:app-db {:user {:name "me"}
             :value-a 3
             :value-b 23}
    :subscriber {:db-ref (ig/ref :re-fancoil/app-db)}})


(def system4
  (ig/init config4))



(def app-db4 (:re-fancoil/app-db system4))
app-db4
;; => #object[reagent.ratom.RAtom {:val {:user {:name "me"}, :value-a 3, :value-b 23}}]

(def subscriber (:re-fancoil/subscriber system4))

(subscriber [:data/value-a 10])
(subscriber [:data/value-b 10])
(subscriber [:data/multi-ab 10])


;; version 5 
;; interceptor version

(defn inject-app-db [app-db]
  {:enter (fn [ctx]
            (assoc-in ctx [:request :app-db] app-db))})

(defn sub-a-handler
  [{:keys [event app-db]}]
  (println "in sub-a-hanlder")
  (make-reaction (fn [] (:value-a @app-db))))


(defmethod ig/init-key :re-fancoil/chain-lib
  [_ {:keys [app-db]}]
  {:value-a [(inject-app-db app-db) sub-a-handler]})


(defmethod ig/init-key :re-fancoil/subscribe-v5
  [_key {:keys [chain-lib]}]
  (fn [event]
    (let [chain (get chain-lib (:action event))
          respond (promise)
          raise (promise)]
      (s/execute chain
                 {:event event}
                 respond
                 raise))))


(def config5
  #:re-fancoil
   {:app-db {:user {:name "me"}
             :value-a 3
             :value-b 23}
    :chain-lib {:app-db (ig/ref :re-fancoil/app-db)}
    :subscribe-v5 {:chain-lib (ig/ref :re-fancoil/chain-lib)}})


(def system5
  (ig/init config5))


(comment

  (let [sub-handler (:re-fancoil/subscribe-v5 system5)]
    (deref (sub-handler {:action :value-a}) 2000 :timeout))

  ;; 
  )


;; version 6


(defmulti data-sub-v6
  (fn [env query-vec] (first query-vec)))

(defmethod data-sub-v6 :data/value-a
  [{:keys [db-ref] :as env} [_signal multi]]
  #_(make-reaction (fn [] (* multi (:value-a @db-ref))))
  (reaction (* multi (:value-a @db-ref))))

(defmethod data-sub-v6 :data/value-b
  [{:keys [db-ref] :as env} [_signal multi]]
  (make-reaction (fn [] (* multi (:value-b @db-ref)))))

(defmethod data-sub-v6 :data/multi-ab
  [{:keys [db-ref] :as env} [_signal multi]]
  (make-reaction (fn []
                   (* @(data-sub-v6 env [:data/value-a multi])
                      @(data-sub-v6 env [:data/value-b multi])))))


(defmethod ig/init-key :re-fancoil/subscriber
  [_ {:keys [db-ref]}]
  (partial data-sub-v6 {:db-ref db-ref}))


(def config6
  #:re-fancoil
   {:app-db {:user {:name "me"}
             :value-a 3
             :value-b 23}
    :subscriber {:db-ref (ig/ref :re-fancoil/app-db)}})


(def system6
  (ig/init config6))


(def app-db6 (:re-fancoil/app-db system6))
app-db6
;; => #object[reagent.ratom.RAtom {:val {:user {:name "me"}, :value-a 3, :value-b 23}}]

(def subscriber (:re-fancoil/subscriber system6))

(r/rswap! app-db6 assoc :value-a 5)

(subscriber [:data/value-a 10])
(subscriber [:data/value-b 10])
(subscriber [:data/multi-ab 10])


;; version 7
;; 注册过程和订阅过程分离


(defmethod ig/init-key :re-fancoil/reg-subs.data-handler
  [_ {:keys [subs]}]
  {:value-a [(fn [db [_marker multi]] (* multi (:value-a db)))]
   :value-b [(fn [db [_marker multi]] (* multi (:value-b db)))]
   :value-c [(fn [[_sig multi]]
               [(rf.subs/subscribe subs [:value-a multi])
                (rf.subs/subscribe subs [:value-b multi])])
             (fn [[a b] [_sig multi]] (* a b))]})


(defmethod ig/init-key :re-fancoil/reg-subs.executor
  [_ {:keys [subs libs]}]
  (doseq [lib libs
          [sig fns] lib]
    (apply rf.subs/reg-sub subs sig fns)))


(def config7
  #:re-fancoil
   {:app-db {:user {:name "me"}
             :value-a 3
             :value-b 23}
    :subs {:app-db (ig/ref :re-fancoil/app-db)}
    :reg-subs.data-handler {:subs (ig/ref :re-fancoil/subs)}
    :reg-subs.executor {:subs (ig/ref :re-fancoil/subs)
                        :libs [(ig/ref :re-fancoil/reg-subs.data-handler)]}})


(def system7 
  (ig/init config7))


(def subs7
  (:re-fancoil/subs system7))

(rf.subs/subscribe subs7 [:value-a 10])
(rf.subs/subscribe subs7 [:value-b 10])
(rf.subs/subscribe subs7 [:value-c 10])



;; version 8
;; 注册过程和订阅过程分离


(defmethod ig/init-key :re-fancoil/reg-subs.data-handler
  [_ {:keys [subs]}]
  {:value-a [(fn [db [_sig multi]] (* multi (:value-a db)))]
   :value-b [(fn [db [_sig multi]] (* multi (:value-b db)))]
   :value-c [(fn [[_sig multi]]
               [[:value-a multi]
                [:value-b multi]])
             (fn [[a b] [_sig multi]] (* a b))]})


(defmethod ig/init-key :re-fancoil/reg-subs.executor-v8
  [_ {:keys [subs libs]}]
  (doseq [lib libs
          [sig fns] lib]
    (apply rf.subs2/reg-sub subs sig fns)))


(def config8
  #:re-fancoil
   {:app-db {:user {:name "me"}
             :value-a 3
             :value-b 23}
    :subs {:app-db (ig/ref :re-fancoil/app-db)}
    :reg-subs.data-handler {:subs (ig/ref :re-fancoil/subs)}
    :reg-subs.executor-v8 {:subs (ig/ref :re-fancoil/subs)
                           :libs [(ig/ref :re-fancoil/reg-subs.data-handler)]}})



(def system8
  (ig/init config8))

(def app-db
  (:re-fancoil/app-db system8))

(def subs8
  (:re-fancoil/subs system8))


(subtree.subs/subscribe subs8 [:value-a 10])
(subtree.subs/subscribe subs8 [:value-b 10])
(subtree.subs/subscribe subs8 [:value-c 10])


(r/rswap! app-db assoc :value-a -1)