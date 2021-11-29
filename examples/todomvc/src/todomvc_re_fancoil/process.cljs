(ns todomvc-re-fancoil.process
  (:require
   [integrant.core :as ig]))


(defmethod ig/init-key ::init!
  [_ {:keys [router]}]
  (let [{:keys [dispatch-sync-fn]} router]
    (dispatch-sync-fn [:initialise-db])))