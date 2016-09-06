(ns narjure.memory-management.concept
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [taoensso.timbre :refer [debug info]]
    [clojure.core.unify :refer [unifier]]
    [narjure.control.bounded-priority-queue :as q]
    [nal.deriver.truth :refer :all]
    [narjure.memory-management.local-inference
     [process-belief :refer :all]]
    [narjure
     [global-atoms :refer :all]
     [debug-util :refer :all]
     [control-utils :refer :all]
     [defaults :refer :all]]
    #_[narjure.memory-management.local-inference
     [local-inference-utils :refer [get-task-id get-tasks]]
     [belief-processor :refer [process-belief]]
     [goal-processor :refer [process-goal]]
     [question-processor :refer [process-question]]])
  (:refer-clojure :exclude ["promise" await]))

(def display (atom '()))
(def search (atom ""))

(defn reduce-priority [task]
  (let [[p d] (:budget task)
        new-budget [(* p d) d]]
    (assoc task :budget new-budget)))

(defn task-handler
  [from [_ [task]]]
  (debuglogger search display ["task processed:" task])

  ; check observable and set if necessary
  (when-not (:observable @state)
    (let [{:keys [occurrence source]} task]
      (when (and (not= occurrence :eternal) (= source :input) (= (:statement task) (:id @state)))
        (set-state! (assoc @state :observable true)))))

  (case (:task-type task)
    :belief (set-state! (process-belief state task))
    ;:goal (process-goal state task)
    ;:question (process-question state task)
    ;:quest (process-quest state task)
    ;:anticipation (process-anticipation state task)
    )

  ;TEMP budget reduction
  (let [reduced-task (reduce-priority task)]
    (when (> (first (:budget reduced-task)) priority-threshold)
      (cast! (:task-buffer @state) [:task-msg reduced-task]))))

(defn belief-request-handler
  ""
  [from [_ [task-concept-id task]]]

  )

(defn inference-request-handler
  ""
  [from message]
  )

(defn temporal-link-creation-handler
  "Creates the termlink between two concepts or creates it if not existing.
   Used to create temporal-links from tasks via task buffer"
  [from [_ [term]]]
  )

(defn link-feedback-handler
  "Creates the termlink between two concepts or creates it if not existing.
   Used to create temporal-links from tasks via task buffer"
  [from [_ [term]]]
  )

(defn concept-state-handler
  "Sends a copy of the actor state to requesting actor"
  [from _]
  (let [concept-state @state]
    (cast! from [:concept-state-msg concept-state])))

(defn set-concept-state-handler
  "set concept state to value passed in message"
  [from [_ new-state]]
  (set-state! (merge @state new-state))
  (let [elements (:elements-map (:tasks new-state))]
    (set-state! (assoc @state :tasks (q/default-queue max-tasks)))
    (doseq [[_ el] elements]
      (set-state! (assoc @state :tasks (q/push-element (:tasks @state) el))))))

(defn shutdown-handler
  "Processes :shutdown-msg and shuts down actor"
  [from msg]
  (set-state! {})
  (unregister!)
  (shutdown!))

(defn initialise
  "Initialises actor: registers actor and sets actor state"
  [name]
  (set-state! {:id                       name
               :priority                 0.5
               :quality                  0.0
               :termlinks                {}
               :concept-manager          (whereis :concept-manager)
               :inference-request-router (whereis :inference-request-router)
               :task-buffer              (whereis :task-buffer)
               :last-forgotten           @nars-time
               :observable               false
               :host-belief              nil
               :host-event               nil}))

(defn msg-handler
  "Identifies message type and selects the correct message handler.
   if there is no match it generates a log message for the unhandled message"
  [from [type :as message]]
  (debuglogger search display message)
  (case type
    :temporal-link-creation-msg (temporal-link-creation-handler from message)
    :task-msg (task-handler from message)
    :link-feedback-msg (link-feedback-handler from message)
    :belief-request-msg (belief-request-handler from message)
    :inference-request-msg (inference-request-handler from message)
    :concept-state-request-msg (concept-state-handler from message)
    :set-concept-state-msg (set-concept-state-handler from message)
    :shutdown (shutdown-handler from message)
    (debug (str "unhandled msg: " type)))
  )

(defn concept [name]
  (gen-server
    (reify Server
      (init [_] (initialise name))
      (terminate [_ cause] #_(info (str aname " terminated.")))
      (handle-cast [_ from id message] (msg-handler from message)))))
