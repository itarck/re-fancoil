(ns re-fancoil.cofx
  (:require
   [re-fancoil.global.loggers      :refer [console]]
   [re-fancoil.lib.interceptor  :refer [->interceptor]]
   [re-fancoil.registrar    :refer [get-handler register-handler]]))


;; -- Registration ------------------------------------------------------------

(def kind :cofx)
(assert (re-fancoil.registrar/kinds kind))

(defn reg-cofx
  [registrar id handler]
  (register-handler registrar kind id handler))


;; -- Interceptor -------------------------------------------------------------

(defn load-inject-cofx
  ([registrar id]
   (->interceptor
    :id      :coeffects
    :before  (fn coeffects-before
               [context]
               (if-let [handler (get-handler registrar kind id)]
                 (update context :coeffects handler)
                 (console :error "No cofx handler registered for" id)))))
  ([registrar id value]
   (->interceptor
    :id     :coeffects
    :before  (fn coeffects-before
               [context]
               (if-let [handler (get-handler registrar kind id)]
                 (update context :coeffects handler value)
                 (console :error "No cofx handler registered for" id))))))


(defn coeffect-handler->interceptor
  [coeffect-handler]
  (->interceptor
   :id      :coeffects
   :before  (fn coeffects-before
              [context]
              (update context :coeffects coeffect-handler))))
