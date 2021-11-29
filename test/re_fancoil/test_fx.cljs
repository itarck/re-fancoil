(ns re-fancoil.test-fx
  (:require
   [integrant.core :as ig]
   [cljs.pprint :refer [pprint]]
   [re-fancoil.core :as rf.core]
   [re-fancoil.router :as router]))


(def config
  {:re-fancoil/app-db {:value-a 3
                       :value-b 23}
   :re-fancoil/registrar {}
   :re-fancoil/router {:registrar (ig/ref :re-fancoil/registrar)}
   :re-fancoil.registrar/cofx {:app-db (ig/ref :re-fancoil/app-db)
                               :registrar (ig/ref :re-fancoil/registrar)}
   :re-fancoil.registrar/fx {:app-db (ig/ref :re-fancoil/app-db)
                             :registrar (ig/ref :re-fancoil/registrar)}
   :re-fancoil.registrar/events {:registrar (ig/ref :re-fancoil/registrar)}})


(def system
  (ig/init config))

(keys system)

(def events
  (:re-fancoil.registrar/events system))

(rf.core/reg-event-db events
                      :inc-value-a (fn [db event]
                                     (update db :value-a inc)))


(:re-fancoil/app-db system)

(def router (:re-fancoil/router system))

(router/dispatch router [:inc-value-a])