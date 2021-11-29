(ns subtree.registrar
  "In many places, re-frame asks you to associate an `id` (keyword)
  with a `handler` (function).  This namespace contains the
  central registry of such associations."
  (:require
   [re-frame.interop :refer [debug-enabled?]]
   [re-frame.loggers :refer [console]]
   [re-frame.settings :as settings]
   [integrant.core :as ig]))



(defn get-handler
  ([self id]
   (get @self id))

  ([self id required?]
   (let [handler (get-handler self id)]
     (when debug-enabled?                          ;; This is in a separate `when` so Closure DCE can run ...
       (when (and required? (nil? handler))        ;; ...otherwise you'd need to type-hint the `and` with a ^boolean for DCE.
         (console :error "re-frame: no handler registered for:" id)))
     handler)))


(defn register-handler
  [self id handler-fn]
  (when debug-enabled?                                       ;; This is in a separate when so Closure DCE can run
    (when (and (not (settings/loaded?)) (get-handler id false))
      (console :warn "re-frame: overwriting handler for:" id)))   ;; allow it, but warn. Happens on figwheel reloads.
  (swap! self assoc-in [id] handler-fn)
  handler-fn)    ;; note: returns the just registered handler


(defn clear-handlers
  ([self]            ;; clear all kinds
   (reset! self {}))

  ([self id]     ;; clear a single handler for a kind
   (if (get-handler self id)
     (swap! self dissoc id)
     (console :warn "re-frame: can't clear handler for" (str id ". Handler not found.")))))


(defmethod ig/init-key :subtree/registrar
  [_ config]
  (atom {}))