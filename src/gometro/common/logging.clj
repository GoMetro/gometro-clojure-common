(ns gometro.common.logging
  (:require
   [afrolabs.spec :as -spec]
   [afrolabs.logging :as -logging]
   [afrolabs.aws.cloudwatch-logs :as -cloudwatch-logs]
   [taoensso.timbre :as log]
   [timbre-json-appender.core]
   [integrant.core :as ig]
   [clojure.spec.alpha :as s]
   [cognitect.aws.client.api :as aws]))

(s/def ::colorized-output? boolean?)

(s/def ::println
  (s/keys :req-un [::colorized-output?]))

(s/def ::aws-client-cfg map?)
(s/def ::disabled? boolean?)

(s/def ::cloudwatch
  (s/keys :req-un [:afrolabs.aws.cloudwatch-logs/batch-period
                   :afrolabs.aws.cloudwatch-logs/log-group
                   :afrolabs.aws.cloudwatch-logs/log-stream
                   :afrolabs.aws.cloudwatch-logs/create-log-stream?
                   ::aws-client-cfg
                   ::disabled?]))

(s/def ::appenders
  (s/keys :req-un []
          :opt-un [::println
                   ::cloudwatch
                   #_::sentry]))
(s/def ::logging-cfg
  (s/keys :req-un [::min-level
                   ::appenders]))

(defmethod ig/init-key ::timbre
  [_ log-config]
  (-spec/assert! ::logging-cfg log-config)
  (let [{:keys              [min-level]
         {:keys [println
                 cloudwatch
                 #_sentry]} :appenders} log-config
        aws-log-dispatcher (if (or (not cloudwatch)
                                   (:disabled? cloudwatch))
                             (constantly nil)
                             (let [{:keys [batch-period
                                           log-group
                                           log-stream
                                           create-log-stream?
                                           aws-client-cfg]} cloudwatch
                                   client (aws/client (assoc aws-client-cfg :api :logs))]
                               (-cloudwatch-logs/create-log-dispatcher client log-group log-stream
                                                                       :batch-period-ms batch-period
                                                                       :create-log-stream? create-log-stream? )))]
    (log/merge-config! {:min-level min-level
                        :appenders
                        (cond-> {}
                          println
                          (assoc :println (-logging/context-println-appender :colorized-output? (:colorized-output println)))

                          cloudwatch
                          (assoc :cloudwatch {:output-fn (timbre-json-appender.core/make-json-output-fn {:msg-key             :message
                                                                                                         :should-log-field-fn (constantly true)})
                                              :enabled?  true
                                              :fn        (fn [{:keys [output_ ^java.util.Date instant]}]
                                                           (aws-log-dispatcher {:message   (force output_)
                                                                                :timestamp (.getTime instant)}))})



                          #_#_sentry
                          (assoc :sentry (sentry-appender sentry-service)))})

    {:cleanup #(do (aws-log-dispatcher :stop)
                   (log/merge-config! {:appenders {:cloudwatch nil}}))}))

(defmethod ig/halt-key! ::timbre
  [_ {:keys [cleanup]}]
  (when cleanup
    (cleanup)))

(defn setup-simple-cli-logging!
  "Sets up logging with the context-aware println logger."
  []
  (log/merge-config! {:appenders
                      {:println (-logging/context-println-appender :colorized-output? true)}}))
