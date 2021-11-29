(ns re-fancoil.registrar
  "In many places, re-frame asks you to associate an `id` (keyword)
  with a `handler` (function).  This namespace contains the
  central registry of such associations."
  (:require
   [re-fancoil.lib.interop :refer [debug-enabled?]]
   [re-fancoil.global.loggers     :refer [console]]
   [re-fancoil.global.settings :as settings]
   [integrant.core :as ig]))


;; kinds of handlers
(def kinds #{:event :fx :cofx :sub})

;; This atom contains a register of all handlers.
;; Contains a two layer map, keyed first by `kind` (of handler), and then `id` of handler.
;; Leaf nodes are handlers.
#_(def kind->id->handler  (atom {}))


(defn get-handler

  ([{:keys [kind->id->handler]} kind]
   (get @kind->id->handler kind))

  ([{:keys [kind->id->handler]} kind id]
   (-> (get @kind->id->handler kind)
       (get id)))

  ([self kind id required?]
   (let [handler (get-handler self kind id)]
     (when debug-enabled?                          ;; This is in a separate `when` so Closure DCE can run ...
       (when (and required? (nil? handler))        ;; ...otherwise you'd need to type-hint the `and` with a ^boolean for DCE.
         (console :error "re-frame: no" (str kind) "handler registered for:" id)))
     handler)))


(defn register-handler
  [{:keys [kind->id->handler] :as self} kind id handler-fn]
  (when debug-enabled?                                       ;; This is in a separate when so Closure DCE can run
    (when (and (not (settings/loaded?)) (get-handler self kind id false))
      (console :warn "re-frame: overwriting" (str kind) "handler for:" id)))   ;; allow it, but warn. Happens on figwheel reloads.
  (swap! kind->id->handler assoc-in [kind id] handler-fn)
  handler-fn)    ;; note: returns the just registered handler


(defn clear-handlers
  ([{:keys [kind->id->handler]}]            ;; clear all kinds
   (reset! kind->id->handler {}))

  ([{:keys [kind->id->handler]} kind]        ;; clear all handlers for this kind
   (assert (kinds kind))
   (swap! kind->id->handler dissoc kind))

  ([{:keys [kind->id->handler]} kind id]     ;; clear a single handler for a kind
   (assert (kinds kind))
   (if (get-handler kind->id->handler kind id)
     (swap! kind->id->handler update-in [kind] dissoc id)
     (console :warn "re-frame: can't clear" (str kind) "handler for" (str id ". Handler not found.")))))


(defmethod ig/init-key :re-fancoil/registrar
  [_ config]
  (println "re-fancoil/registrar starting")
  {:kind->id->handler (atom {})})