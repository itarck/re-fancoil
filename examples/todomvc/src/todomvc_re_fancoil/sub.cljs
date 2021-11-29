(ns todomvc-re-fancoil.sub)


(def sub-chains

  {:showing [(fn [db _]  
               (:showing db))]
   :sorted-todos [(fn [db _]
                    (:todos db))]

   :todos [(fn [_query-v]
             [[:sorted-todos]])
           (fn [[sorted-todos] _query-v]
             (vals sorted-todos))]
   
   :visible-todos [:<- [:todos]
                   :<- [:showing]
                   (fn [[todos showing] _]
                     (let [filter-fn (case showing
                                       :active (complement :done)
                                       :done   :done
                                       :all    identity)]
                       (filter filter-fn todos)))]

   :all-complete? [:<- [:todos]
                   (fn [todos _]
                     (every? :done todos))]
   :completed-count [:<- [:todos]
                     (fn [todos _]
                       (count (filter :done todos)))]
   :footer-counts [:<- [:todos]
                   :<- [:completed-count]
                   (fn [[todos completed] _]
                     [(- (count todos) completed) completed])]})