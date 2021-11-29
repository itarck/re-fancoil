(ns simple-re-fancoil.sub)

;; -- Domino 4 - Query  -------------------------------------------------------


(def sub-chains
  {:time [(fn [db _]
            (:time db))]
   :time-color [(fn [db _]
                  (:time-color db))]})


