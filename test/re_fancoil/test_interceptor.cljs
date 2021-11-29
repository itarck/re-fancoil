(ns re-fancoil.test-interceptor
  (:require
   [re-frame.interceptor :as interceptor :refer [->interceptor]]
   [re-fancoil.cofx :as cofx]))



(defn inject-a-builder
  [value]
  (->interceptor
   :id      :coeffects
   :before  (fn coeffects-before
              [context]
              (update context :coeffects (fn [cofx]
                                           (assoc cofx :a value))))))

(defn identity-handler
  [cofx [_ value]]
  (update cofx :a (fn [a] (+ a value))))


(defn handler-interceptor-builder
  [handler]
  (->interceptor
   :id     :fx-handler
   :before (fn fx-handler-before
             [context]
             (let [new-context
                   (let [{:keys [event] :as coeffects} (interceptor/get-coeffect context)]
                     (->> (handler coeffects event)
                          (assoc context :effects)))]
               new-context))))


(handler-interceptor-builder identity-handler)

(:effects (interceptor/execute [:add 10] [(inject-a-builder 32) (handler-interceptor-builder identity-handler)]))
;; => {:event [:add 10], :original-event [:add 10], :a 42}
