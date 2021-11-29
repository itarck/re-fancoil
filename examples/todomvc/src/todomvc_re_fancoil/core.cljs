(ns todomvc-re-fancoil.core
  (:require
   [reagent.dom]
   [re-fancoil.core]
   [re-fancoil.subs :as rfan.subs]
   [integrant.core :as ig]
   [todomvc-re-fancoil.db :refer [default-db]]
   [todomvc-re-fancoil.event :as todomvc.event]
   [todomvc-re-fancoil.sub :as todomvc.sub]
   [todomvc-re-fancoil.view]
   [todomvc-re-fancoil.process :as todomvc.process]))


(def config
  {:re-fancoil/app-db default-db
   :re-fancoil/registrar {}
   :re-fancoil/subs {:app-db (ig/ref :re-fancoil/app-db)
                     :registrar (ig/ref :re-fancoil/registrar)
                     :sub-chains todomvc.sub/sub-chains}

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
                                 (ig/ref :re-fancoil.builtin.fx/db)
                                 (ig/ref :todomvc-re-fancoil/local-storage)]
                       :event-db-chains todomvc.event/event-db-chains
                       :event-fx-chains todomvc.event/event-fx-chains}

   :todomvc-re-fancoil/local-storage {:registrar (ig/ref :re-fancoil/registrar)}
   :todomvc-re-fancoil.view/root-view {:router (ig/ref :re-fancoil/router)
                                       :subs (ig/ref :re-fancoil/subs)}
   ::todomvc.process/init! {:router (ig/ref :re-fancoil/router)}})


(defonce system
  (ig/init config))



;; -- Entry Point -------------------------------------------------------------

(defn render
  []
  (reagent.dom/render (:todomvc-re-fancoil.view/root-view system)
                      (js/document.getElementById "app")))

(defn ^:dev/after-load clear-cache-and-render!
  []
  ;; The `:dev/after-load` metadata causes this function to be called
  ;; after shadow-cljs hot-reloads code. We force a UI update by clearing
  ;; the Reframe subscription cache.
  (rfan.subs/clear-subscription-cache! (:re-fancoil/subs system))
  (render))


(defn ^:export main
  []
  (render))


(comment

  (def router (:re-fancoil/router system))

  (def dispatch (get-in system [:re-fancoil/router :dispatch-fn]))

  (dispatch [:add-todo "abcd"])
  (dispatch [:initialise-db])

  (get-in system [:re-fancoil/app-db])

  (def subscribe (:re-fancoil/subs system))

  (subscribe [:todos])
  (subscribe [:footer-counts])
  
  ;; 
  )