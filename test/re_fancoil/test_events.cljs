(ns re-fancoil.test-events
  (:require
   [integrant.core :as ig]
   [cljs.pprint :refer [pprint]]
   [re-fancoil.core :as rf.core]
   [re-fancoil.registrar :as rf.registrar]
   [re-fancoil.registrar.events :as reg.events]
   [re-fancoil.interceptor :as interceptor]))


(def config
  {:re-fancoil/app-db {:value-a 3
                       :value-b 23}
   :re-fancoil/registrar {}
   :re-fancoil.registrar.cofx/db {:app-db (ig/ref :re-fancoil/app-db)
                                  :registrar (ig/ref :re-fancoil/registrar)}
   :re-fancoil.registrar/fx {:registrar (ig/ref :re-fancoil/registrar)}
   :re-fancoil.registrar.fx/db {:registrar (ig/ref :re-fancoil/registrar)
                                :app-db (ig/ref :re-fancoil/app-db)}
   :re-fancoil.registrar/events {:registrar (ig/ref :re-fancoil/registrar)}})


(def system
  (ig/init config))


(keys system)

(pprint (:re-fancoil/registrar system))

(def events
  (:re-fancoil.registrar/events system))

(rf.core/reg-event-db events
                      :inc-value-a (fn [db event]
                                     (update db :value-a inc)))

(let [event-v [:inc-value-a]
      interceptors (rf.registrar/get-handler (:re-fancoil/registrar system) :event :inc-value-a true)]
  (interceptor/execute event-v interceptors))


(reg.events/handle events [:inc-value-a])

(:re-fancoil/app-db system)
