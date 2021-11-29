(ns simple-re-fancoil.process
  (:require 
   [integrant.core :as ig]))


;; -- Domino 1 - Event Dispatch -----------------------------------------------

(defn dispatch-timer-event
  [dispatch-fn]
  (let [now (js/Date.)]
    (dispatch-fn [:timer now])))  ;; <-- dispatch used


(defmethod ig/init-key :simple-re-fancoil.process/init!
  [_ {:keys [router]}]
  (let [{:keys [dispatch-fn dispatch-sync-fn]} router]
    (dispatch-sync-fn [:initialize])
    (js/setInterval (partial dispatch-timer-event dispatch-fn) 1000)))

