(ns re-fancoil.db
  (:require
   [re-fancoil.lib.interop :refer [ratom]]
   [integrant.core :as ig]))


;; -- Application State  --------------------------------------------------------------------------
;;
;; Should not be accessed directly by application code.
;; Read access goes through subscriptions.
;; Updates via event handlers.

(def app-db (ratom {}))


(defmethod ig/init-key :re-fancoil/app-db
  [_ config]
  (println "re-fancoil/app-db: starting")
  (ratom config))
