(ns todomvc-re-fancoil.local-storage
  (:require
   [cljs.reader]
   [integrant.core :as ig]
   [re-fancoil.cofx :as rfan.cofx]))

;; -- Local Storage  ----------------------------------------------------------
;;
;; Part of the todomvc challenge is to store todos in LocalStorage, and
;; on app startup, reload the todos from when the program was last run.
;; But the challenge stipulates to NOT load the setting for the "showing"
;; filter. Just the todos.
;;

(def ls-key "todos-reframe")                         ;; localstore key

(defn todos->local-store
  "Puts todos into localStorage"
  [todos]
  (.setItem js/localStorage ls-key (str todos)))     ;; sorted-map written as an EDN map


(defmethod ig/init-key :todomvc-re-fancoil/local-storage
  [_ {:keys [registrar]}]
  (rfan.cofx/reg-cofx
   registrar
   :local-store-todos
   (fn [cofx _]
      ;; put the localstore todos into the coeffect under :local-store-todos
     (assoc cofx :local-store-todos
             ;; read in todos from localstore, and process into a sorted map
            (into (sorted-map)
                  (some->> (.getItem js/localStorage ls-key)
                           (cljs.reader/read-string)    ;; EDN map -> map
                           ))))))