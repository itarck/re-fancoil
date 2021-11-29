(ns simple-re-fancoil.view
  (:require
   [clojure.string :as str]
   [integrant.core :as ig]))

;; -- Domino 5 - View Functions ----------------------------------------------

(defn clock
  [{:keys [subscribe]}]
  [:div.example-clock
   {:style {:color @(subscribe [:time-color])}}
   (-> @(subscribe [:time])
       .toTimeString
       (str/split " ")
       first)])

(defn color-input
  [{:keys [dispatch subscribe]}]
  [:div.color-input
   "Time color: "
   [:input {:type "text"
            :value @(subscribe [:time-color])
            :on-change #(dispatch [:time-color-change (-> % .-target .-value)])}]])  ;; <---

(defn ui
  [env]
  [:div
   [:h1 "Hello world, it is now"]
   [clock env]
   [color-input env]])


(defmethod ig/init-key :simple-re-fancoil.view/root-view
  [_ {:keys [router subs]}]
  (let [dispatch-fn (get-in router [:dispatch-fn])
        subscribe-fn (get-in subs [:subscribe-fn])]
    [ui {:dispatch dispatch-fn
         :subscribe subscribe-fn}]))
