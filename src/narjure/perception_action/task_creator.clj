(ns narjure.perception-action.task-creator
  (:require
    [co.paralleluniverse.pulsar.actors :refer [! spawn gen-server register! cast! Server self whereis state set-state! shutdown! unregister!]]
    [narjure.actor.utils :refer [defactor]]
    [taoensso.timbre :refer [debug info]]
    [clojure.set :as set]
    [narjure.defaults :refer :all])
  (:refer-clojure :exclude [promise await]))

(def aname :task-creator)

(defn sentence [_ _]
  (debug aname "process-sentence"))

(defn system-time-tick-handler
  "inc :time value in actor state for each system-time-tick-msg"
  []
  #_(debug aname "process-system-time-tick")
  (set-state! (update @state :time inc)))

(defn get-id
  "inc the task :id in actor state and returns the value"
  []
  (set-state! (update @state :id inc))
  (@state :id))

(defn get-time
  "return the current time from actor state"
  []
  (@state :time))

(defn initialise
  "Initialises actor:
      registers actor and sets actor state"
  [aname actor-ref]
  (register! aname actor-ref)
  (set-state! {:time 0 :id -1 :task-dispatcher (whereis :task-dispatcher)}))

(defn create-new-task
  "create a new task with the provided sentence and default values
   convert tense to occurrence time if applicable"
  [sentence time id]
  (let [future-past-offset 1000                             ;n steps offset for now
        toc (case (:tense sentence)                         ;TODO: grammar extension :/k: :\h: ?
              :eternal :eternal
              :present time
              :past (- time future-past-offset)
              :future (+ time future-past-offset))
        content (:content sentence)
        task-type (:punctuation sentence)
        budget (case task-type
                 :goal goal-budget
                 :belief belief-budget
                 :question question-budget
                 :quest quest-budget)]
  {:truth (:truth sentence)
   :desire (:desire sentence)
   :budget budget
   :creation time
   :occurrence toc
   :source :input
   :id id
   :evidence '(id)
   :sc (syntactic-complexity content)
   :terms (termlink-subterms 0 content)                                               ;<- TODO add subterms
   :solution nil
   :task-type task-type
   :term content
   }))

(defn compound?
  [content]
  (and (sequential? content) (not= (first content) :interval))
  )

(defn syntactic-complexity                                  ;TODO move to term utils
  "Calculates the syntactic complexity of a content term,
  for example (| (& a b) c) has complexity 5"
  [content]
  (if (compound? content)
    (reduce + (map syntactic-complexity content))
    1))

(defn termlink-subterms                                     ;TODO: filter out --> etc. since we don't need to termlink them
  "Extract the termlink relevant subterms of the term up to 3 levels as demanded by the NAL rules"
  [level content]
  (if (and (< level 3) (compound? content))
    (set/union #{content} (reduce set/union (map (partial termlink-subterms (+ level 1)) content)))
    #{content}))

(defn create-derived-task
  "Create a derived task with the provided sentence, budget and occurence time
   and default values for the remaining parameters"
  [sentence budget occurrence time id]
  {:creation time
   :occurrence occurrence
   :source :derived
   :id id
   :evidence '(id)
   :solution nil
   :content (:content sentence)})

(defn sentence-handler
  "Processes a :sentence-msg"
  [from [_ sentence]]
  (cast! (:task-dispatcher @state) [:task-msg (create-new-task sentence (:time @state) (get-id))]))

(defn derived-sentence-handler
  "processes a :derived-sentence-msg"
  [from [msg sentence budget occurrence]]
  (cast! (:task-dispatcher @state) [:task-msg (create-derived-task sentence budget occurrence (:time @state) (get-id))]))

(defn shutdown-handler
  "Processes :shutdown-msg and shuts down actor"
  [from msg]
  (unregister!)
  (shutdown!))

(defn msg-handler
  "Identifies message type and selects the correct message handler.
   if there is no match it generates a log message for the unhandled message "
  [from [type :as message]]
  (case type
    :sentence-msg (sentence-handler from message)
    :derived-sentence-msg (derived-sentence-handler from message)
    :system-time-tick-msg (system-time-tick-handler)
    :shutdown (shutdown-handler from message)
    (debug aname (str "unhandled msg: " type))))

(def task-creator (gen-server
                    (reify Server
                      (init [_] (initialise aname @self))
                      (terminate [_ cause] #_(info (str aname " terminated.")))
                      (handle-cast [_ from id message] (msg-handler from message)))))