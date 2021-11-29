(ns todomvc-re-fancoil.event
  (:require
   [re-fancoil.global.std-interceptors :refer [path after]]
   [cljs.spec.alpha :as s]
   [todomvc-re-fancoil.local-storage :as local-storage]))


;; -- Interceptors --------------------------------------------------------------
;;
;; Interceptors are a more advanced topic. So, we're plunging into the deep
;; end here.
;;
;; There is a tutorial on Interceptors in re-frame's `/docs`, but to get
;; you going fast, here's a very high level description ...
;;
;; Every event handler can be "wrapped" in a chain of interceptors. A
;; "chain of interceptors" is actually just a "vector of interceptors". Each
;; of these interceptors can have a `:before` function and an `:after` function.
;; Each interceptor wraps around the "handler", so that its `:before`
;; is called before the event handler runs, and its `:after` runs after
;; the event handler has run.
;;
;; Interceptors with a `:before` action, can be used to "inject" values
;; into what will become the `coeffects` parameter of an event handler.
;; That's a way of giving an event handler access to certain resources,
;; like values in LocalStore.
;;
;; Interceptors with an `:after` action, can, among other things,
;; process the effects produced by the event handler. One could
;; check if the new value for `app-db` correctly matches a Spec.
;;


;; -- First Interceptor ------------------------------------------------------
;;
;; Event handlers change state, that's their job. But what happens if there's
;; a bug in the event handler and it corrupts application state in some subtle way?
;; Next, we create an interceptor called `check-spec-interceptor`.
;; Later, we use this interceptor in the interceptor chain of all event handlers.
;; When included in the interceptor chain of an event handler, this interceptor
;; runs `check-and-throw` `after` the event handler has finished, checking
;; the value for `app-db` against a spec.
;; If the event handler corrupted the value for `app-db` an exception will be
;; thrown. This helps us detect event handler bugs early.
;; Because all state is held in `app-db`, we are effectively validating the
;; ENTIRE state of the application after each event handler runs.  All of it.


(defn check-and-throw
  "Throws an exception if `db` doesn't match the Spec `a-spec`."
  [a-spec db]
  (when-not (s/valid? a-spec db)
    (throw (ex-info (str "spec check failed: " (s/explain-str a-spec db)) {}))))

;; now we create an interceptor using `after`
(def check-spec-interceptor (after (partial check-and-throw :todomvc-re-fancoil.db/db)))


;; -- Second Interceptor -----------------------------------------------------
;;
;; Part of the TodoMVC challenge is to store todos in local storage.
;; Next, we define an interceptor to help with this challenge.
;; This interceptor runs `after` an event handler, and it stores the
;; current todos into local storage.
;; Later, we include this interceptor into the interceptor chain
;; of all event handlers which modify todos.  In this way, we ensure that
;; every change to todos is written to local storage.

(def ->local-store (after local-storage/todos->local-store))


;; -- Interceptor Chain ------------------------------------------------------
;;
;; Each event handler can have its own chain of interceptors.
;; We now create the interceptor chain shared by all event handlers
;; which manipulate todos.
;; A chain of interceptors is a vector of interceptors.
;; Explanation of the `path` Interceptor is given further below.
(def todo-interceptors [check-spec-interceptor    ;; ensure the spec is still valid  (after)
                        (path :todos)             ;; the 1st param given to handler will be the value from this path within db
                        ->local-store])            ;; write todos to localstore  (after)


;; -- Helpers -----------------------------------------------------------------

(defn allocate-next-id
  "Returns the next todo id.
  Assumes todos are sorted.
  Returns one more than the current largest id."
  [todos]
  ((fnil inc 0) (last (keys todos))))


;; -- Event Handlers ----------------------------------------------------------

(defn set-showing-handler
  [db [_ new-filter-kw]]     ;; new-filter-kw is one of :all, :active or :done
  (assoc db :showing new-filter-kw))


(defn add-todo-handler
  [todos [_ text]]
  (let [id (allocate-next-id todos)]
    (assoc todos id {:id id :title text :done false})))

(defn toggle-done-handler
  [todos [_ id]]
  (update-in todos [id :done] not))

(defn save-handler
  [todos [_ id title]]
  (assoc-in todos [id :title] title))


(defn delete-todo-handler
  [todos [_ id]]
  (dissoc todos id))


(defn clear-completed-handler [todos _]
  (let [done-ids (->> (vals todos)
                      (filter :done)
                      (map :id))]
    (reduce dissoc todos done-ids)))


(defn complete-all-toggle-handler [todos _]
  (let [new-done (not-every? :done (vals todos))]   ;; work out: toggle true or false?
    (reduce #(assoc-in %1 [%2 :done] new-done)
            todos
            (keys todos))))


;; -- Event chains -----------------------------------------------------------------


(def event-db-chains
  
  {:set-showing [[check-spec-interceptor] set-showing-handler]
   :add-todo [todo-interceptors add-todo-handler]
   :toggle-done [todo-interceptors toggle-done-handler]
   :save [todo-interceptors save-handler]
   :delete-todo [todo-interceptors delete-todo-handler]
   :clear-completed [todo-interceptors clear-completed-handler]
   :complete-all-toggle [todo-interceptors complete-all-toggle-handler]})


(def event-fx-chains
  {:initialise-db [[:cofx/local-store-todos
                    check-spec-interceptor]
                   (fn [{:keys [db local-store-todos]}]
                     {:db (assoc db :todos local-store-todos)})]})
