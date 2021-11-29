(ns simple-re-fancoil.event)

;; -- Domino 2 - Event Handlers -----------------------------------------------


(def event-db-chains
  {:initialize                 ;; usage:  (dispatch [:initialize])
    [(fn [_db _]                   ;; the two parameters are not important here, so use _
       {:time (js/Date.)         ;; What it returns becomes the new application state
        :time-color "#f88"})]

   :time-color-change            ;; dispatched when the user enters a new colour into the UI text field
    [(fn [db [_ new-color-value]]  ;; -db event handlers given 2 parameters:  current application state and event (a vector)
       (assoc db :time-color new-color-value))]

   :timer                         ;; every second an event of this kind will be dispatched
    [(fn [db [_ new-time]]          ;; note how the 2nd parameter is destructured to obtain the data value
      (assoc db :time new-time))]})



