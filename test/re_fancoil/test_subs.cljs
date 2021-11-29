(ns re-fancoil.test-subs
  (:require
   [cljs.core.async :as a :refer [go >! <!]]
   [integrant.core :as ig]
   [reagent.core :as r]
   [reagent.ratom :refer [reaction]]
   [re-fancoil.db]
   [re-fancoil.registrar :as rf.registrar]
   [re-fancoil.subs :as rf.subs]
   [re-frame.interop :refer [make-reaction]]
   [sieppari.core :as s]))


(defmethod ig/init-key :re-fancoil/reg-subs.data-library
  [_ {:keys [subs]}]
  {:multi-a [(fn [db [_marker multi]] (* multi (:value-a db)))]
   :multi-b [(fn [db [_marker multi]] (* multi (:value-b db)))]
   :multi-ab [(fn [[_sig multi]]
                [(rf.subs/subscribe subs [:multi-a 1])
                 (rf.subs/subscribe subs [:multi-b 1])])
              (fn [[a b] [_sig multi]] (* a b multi))]})


(defmethod ig/init-key :re-fancoil/reg-subs.executor
  [_ {:keys [subs libs]}]
  (doseq [lib libs
          [sig fns] lib]
    (apply rf.subs/reg-sub subs sig fns)))


(def config
  #:re-fancoil
   {:app-db {:value-a 3
             :value-b 23}
    :registrar {}
    :subs {:app-db (ig/ref :re-fancoil/app-db)
           :registrar (ig/ref :re-fancoil/registrar)}
    :reg-subs.data-library {:subs (ig/ref :re-fancoil/subs)}
    :reg-subs.executor {:subs (ig/ref :re-fancoil/subs)
                        :libs [(ig/ref :re-fancoil/reg-subs.data-library)]}})


(def system
  (ig/init config))


(def subs
  (:re-fancoil/subs system))

(def app-db 
  (:re-fancoil/app-db system))

(rf.subs/subscribe subs [:multi-a 10])
(rf.subs/subscribe subs [:multi-b 10])
(rf.subs/subscribe subs [:multi-ab 100])

(r/rswap! app-db assoc :value-a 19)

(rf.subs/reg-sub subs :value-a (fn [app-db] (:value-a app-db)))

(rf.subs/subscribe subs [:value-a])