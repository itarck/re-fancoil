(ns re-fancoil.builtin.fx
  (:require
   
   [re-fancoil.lib.interop     :refer [set-timeout!]]
   [re-fancoil.global.loggers     :refer [console]]
   [re-fancoil.router      :as rf.router]
   [re-fancoil.registrar   :refer [get-handler clear-handlers register-handler]]
   [integrant.core :as ig]))


;; -- Registration ------------------------------------------------------------

(def kind :fx)
(assert (re-fancoil.registrar/kinds kind))


;; -- Builtin Effect Handlers  ------------------------------------------------


;; :fx
;;
;; Handle one or more effects. Expects a collection of vectors (tuples) of the
;; form [effect-key effect-value]. `nil` entries in the collection are ignored
;; so effects can be added conditionally.
;;
;; usage:
;;
;; {:fx [[:dispatch [:event-id "param"]]
;;       nil
;;       [:http-xhrio {:method :post
;;                     ...}]]}
;;

#_(reg-fx
   :fx
   (fn [seq-of-effects]
     (if-not (sequential? seq-of-effects)
       (console :error "re-frame: \":fx\" effect expects a seq, but was given " (type seq-of-effects))
       (doseq [[effect-key effect-value] (remove nil? seq-of-effects)]
         (when (= :db effect-key)
           (console :warn "re-frame: \":fx\" effect should not contain a :db effect"))
         (if-let [effect-fn (get-handler kind effect-key false)]
           (effect-fn effect-value)
           (console :warn "re-frame: in \":fx\" effect found " effect-key " which has no associated handler. Ignoring."))))))

(defn build-fx-handler [self]
  (fn [seq-of-effects]
    (if-not (sequential? seq-of-effects)
      (console :error "re-frame: \":fx\" effect expects a seq, but was given " (type seq-of-effects))
      (doseq [[effect-key effect-value] (remove nil? seq-of-effects)]
        (when (= :db effect-key)
          (console :warn "re-frame: \":fx\" effect should not contain a :db effect"))
        (if-let [effect-fn (get-handler kind effect-key false)]
          (effect-fn effect-value)
          (console :warn "re-frame: in \":fx\" effect found " effect-key " which has no associated handler. Ignoring."))))))


(defmethod ig/init-key :re-fancoil.builtin.fx/fx
  [_ {:keys [registrar]}]
  (let [self {:module-name :fx
              :registrar registrar}]
    (register-handler registrar kind :fx (build-fx-handler self))
    self))

;; :dispatch-later
;;
;; `dispatch` one or more events after given delays. Expects a collection
;; of maps with two keys:  :`ms` and `:dispatch`
;;
;; usage:
;;
;;    {:dispatch-later [{:ms 200 :dispatch [:event-id "param"]}    ;;  in 200ms do this: (dispatch [:event-id "param"])
;;                      {:ms 100 :dispatch [:also :this :in :100ms]}]}
;;
;; Note: nil entries in the collection are ignored which means events can be added
;; conditionally:
;;    {:dispatch-later [ (when (> 3 5) {:ms 200 :dispatch [:conditioned-out]})
;;                       {:ms 100 :dispatch [:another-one]}]}
;;
(defn dispatch-later
  [router {:keys [ms dispatch] :as effect}]
  (if (or (empty? dispatch) (not (number? ms)))
    (console :error "re-frame: ignoring bad :dispatch-later value:" effect)
    (set-timeout! #(rf.router/dispatch router dispatch) ms)))

#_(reg-fx
 :dispatch-later
 (fn [value]
   (if (map? value)
     (dispatch-later value)
     (doseq [effect (remove nil? value)]
       (dispatch-later effect)))))

(defn build-dispatch-later-handler [{:keys [router]}]
  (fn [value]
    (if (map? value)
      (dispatch-later router value)
      (doseq [effect (remove nil? value)]
        (dispatch-later router effect)))))


;; :dispatch
;;
;; `dispatch` one event. Expects a single vector.
;;
;; usage:
;;   {:dispatch [:event-id "param"] }

#_(reg-fx
 :dispatch
 (fn [value]
   (if-not (vector? value)
     (console :error "re-frame: ignoring bad :dispatch value. Expected a vector, but got:" value)
     (router/dispatch value))))

(defn build-dispatch-handler [{:keys [router]}]
  (fn [value]
    (if-not (vector? value)
      (console :error "re-frame: ignoring bad :dispatch value. Expected a vector, but got:" value)
      (rf.router/dispatch router value))))


;; :dispatch-n
;;
;; `dispatch` more than one event. Expects a list or vector of events. Something for which
;; sequential? returns true.
;;
;; usage:
;;   {:dispatch-n (list [:do :all] [:three :of] [:these])}
;;
;; Note: nil events are ignored which means events can be added
;; conditionally:
;;    {:dispatch-n (list (when (> 3 5) [:conditioned-out])
;;                       [:another-one])}
;;
#_(reg-fx
 :dispatch-n
 (fn [value]
   (if-not (sequential? value)
     (console :error "re-frame: ignoring bad :dispatch-n value. Expected a collection, but got:" value)
     (doseq [event (remove nil? value)] (router/dispatch event)))))

(defn build-dispatch-n-handler [{:keys [router]}]
  (fn [value]
    (if-not (sequential? value)
      (console :error "re-frame: ignoring bad :dispatch-n value. Expected a collection, but got:" value)
      (doseq [event (remove nil? value)] (rf.router/dispatch router event)))))


(defmethod ig/init-key :re-fancoil.builtin.fx/dispatcher
  [_ {:keys [registrar router]}]
  (let [self {:router router
              :registrar registrar}
        fx-library {:dispatch-later (build-dispatch-later-handler self)
                    :dispatch (build-dispatch-handler self)
                    :dispatch-n (build-dispatch-n-handler self)}]
    (doseq [[k f] fx-library]
      (register-handler registrar kind k f))
    self))

;; :deregister-event-handler
;;
;; removes a previously registered event handler. Expects either a single id (
;; typically a namespaced keyword), or a seq of ids.
;;
;; usage:
;;   {:deregister-event-handler :my-id)}
;; or:
;;   {:deregister-event-handler [:one-id :another-id]}
;;
#_(reg-fx
 :deregister-event-handler
 (fn [value]
   (let [clear-event (partial clear-handlers events/kind)]
     (if (sequential? value)
       (doseq [event value] (clear-event event))
       (clear-event value)))))


(defn build-deregister-event-handler
  [{:keys [registrar]}]
  (fn [value]
    (let [clear-event (partial clear-handlers registrar :event)]
      (if (sequential? value)
        (doseq [event value] (clear-event event))
        (clear-event value)))))


(defmethod ig/init-key :re-fancoil.builtin.fx/deregister-event-handler
  [_ {:keys [registrar]}]
  (let [self {:module-name :deregister-event-handler
              :registrar registrar}]
    (register-handler registrar kind :deregister-event-handler (build-deregister-event-handler self))
    self))


;; :db
;;
;; reset! app-db with a new value. `value` is expected to be a map.
;;
;; usage:
;;   {:db  {:key1 value1 key2 value2}}
;;
#_(reg-fx
 :db
 (fn [value]
   (if-not (identical? @app-db value)
     (reset! app-db value))))


(defmethod ig/init-key :re-fancoil.builtin.fx/db
  [_ {:keys [registrar app-db]}]
  (let [self {:module-name :db
              :registrar registrar}]
    (register-handler registrar kind :db (fn [value]
                                           (if-not (identical? @app-db value)
                                             (reset! app-db value))))
    self))


