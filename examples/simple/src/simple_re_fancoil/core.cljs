(ns simple-re-fancoil.core
  (:require
   [reagent.dom]
   [re-fancoil.core]
   [re-fancoil.subs :as rfan.subs]
   [integrant.core :as ig]
   [simple-re-fancoil.event :as simple.event]
   [simple-re-fancoil.sub :as simple.sub]
   [simple-re-fancoil.view]
   [simple-re-fancoil.process]))


(def config
  {:re-fancoil/app-db {}
   :re-fancoil/registrar {}
   :re-fancoil/subs {:app-db (ig/ref :re-fancoil/app-db)
                     :registrar (ig/ref :re-fancoil/registrar)
                     :sub-chains simple.sub/sub-chains}

   :re-fancoil/router {:registrar (ig/ref :re-fancoil/registrar)}
   :re-fancoil.builtin.cofx/db {:app-db (ig/ref :re-fancoil/app-db)
                                :registrar (ig/ref :re-fancoil/registrar)}
   :re-fancoil.builtin.fx/fx {:registrar (ig/ref :re-fancoil/registrar)}
   :re-fancoil.builtin.fx/dispatcher {:registrar (ig/ref :re-fancoil/registrar)
                                      :router (ig/ref :re-fancoil/router)}
   :re-fancoil.builtin.fx/db {:registrar (ig/ref :re-fancoil/registrar)
                              :app-db (ig/ref :re-fancoil/app-db)}
   :re-fancoil/events {:registrar (ig/ref :re-fancoil/registrar)
                       :bultins [(ig/ref :re-fancoil.builtin.cofx/db)
                                 (ig/ref :re-fancoil.builtin.fx/fx)
                                 (ig/ref :re-fancoil.builtin.fx/dispatcher)
                                 (ig/ref :re-fancoil.builtin.fx/db)]
                       :event-db-chains simple.event/event-db-chains}

   :simple-re-fancoil.process/init! {:router (ig/ref :re-fancoil/router)
                                     :events (ig/ref :re-fancoil/events)}
   :simple-re-fancoil.view/root-view {:router (ig/ref :re-fancoil/router)
                                      :subs (ig/ref :re-fancoil/subs)}})


(def system
  (ig/init config))



;; -- Entry Point -------------------------------------------------------------

(defn render
  []
  (reagent.dom/render (:simple-re-fancoil.view/root-view system)
                      (js/document.getElementById "app")))

(defn ^:dev/after-load clear-cache-and-render!
  []
  (rfan.subs/clear-subscription-cache! (:re-fancoil/subs system))
  (render))

(defn run
  []
  (render))



(comment

  (def sub-fn (:re-fancoil/subs system))

  @(sub-fn [:time])

  (get-in system [:re-fancoil/app-db]))