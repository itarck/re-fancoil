(ns re-fancoil.builtin.cofx
  (:require
   [re-fancoil.registrar    :refer [get-handler register-handler]]
   [integrant.core :as ig]))


;; -- Registration ------------------------------------------------------------

(def kind :cofx)
(assert (re-fancoil.registrar/kinds kind))

(defn reg-cofx
  [registrar id handler]
  (register-handler registrar kind id handler))



;; -- Builtin CoEffects Handlers  ---------------------------------------------

;; :db
;;
;; Adds to coeffects the value in `app-db`, under the key `:db`
#_(reg-cofx
 :db
 (fn db-coeffects-handler
   [coeffects]
   (assoc coeffects :db @app-db)))


;; Because this interceptor is used so much, we reify it


(defmethod ig/init-key :re-fancoil.builtin.cofx/db
  [_ {:keys [app-db registrar] :as config}]
  (let [self {:app-db app-db
              :registrar registrar}]
    (reg-cofx registrar :db (fn db-coeffects-handler
                              [coeffects]
                              (assoc coeffects :db @app-db)))
    self))