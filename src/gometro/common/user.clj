(ns gometro.common.user
  (:require
   [afrolabs.config :as -config]
   [afrolabs.service :as -service]
   [integrant.core :as ig]
   [taoensso.timbre :as log]
   ))

(def default-log-component-dependencies
  "Component keys the log component may depend on. `as-system` makes every
  other component depend on the log component; these are exempt, so the log
  component can `#ig/ref` them without a cycle."
  #{:afrolabs.components.aws.client-config/aws-client-config
    :afrolabs.components.sentry/client
    :afrolabs.components.version-info/version-info})

(defn- log-component-setup
  [log-component-dependencies-pred]
  {:log-component-kw                :gometro.common.logging/timbre
   :log-component-dependencies-pred (or log-component-dependencies-pred
                                        default-log-component-dependencies)})

(defonce dev-system (atom nil))

(defn make-dev-system
  "Creates and starts a \"dev\" system.
  Accepts `config-files`, a map of :keyword -> resource-path-to-integrant-edn-file
  and `default-cfg`. This is passed to the `-service/as-system` fn, except
  `:log-component-dependencies-pred` (see `init-system`)."
  [config-files
   & {:keys [config-file log-component-dependencies-pred]
      :as   default-cfg}]
  (-service/as-system (merge {:log-component-setup (log-component-setup log-component-dependencies-pred)
                              :profile             :dev}
                             (dissoc default-cfg :log-component-dependencies-pred)
                             {:config-file    (or (get config-files config-file)
                                                  (when (string? config-file) config-file)
                                                  (:default config-files))})))

(defn dev-start! [& {:as   args
                     :keys [the-system
                            config-files]}]
  (reset! (or the-system
              dev-system)
          (make-dev-system config-files (dissoc args :config-files :the-system)))
  :ok)

(defn dev-stop!
  [& {:keys [the-system]}]
  (swap! (or the-system
             dev-system)
         #(when % (ig/halt! %))))

(defn dev-reset!
  [& {:as args}]
  (dev-stop! args)
  (dev-start! args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn main-impl
  "An implementation of a main file. Intended use is to be called from the real `-main`, with a first-param of
  `opts` as for `init-system`. The rest parameters are the normal cli args.

  Starts a system with respect to the `config-files` (all available) and the first param `config-file` (selected config).
  Respects `IG_CFG_KS` environment variable, to start a sub-selection of systems in the selected config.edn file."
  [{:keys [config-files
           log-component-dependencies-pred]} & [config-file & args]]
  (try
    (let [config-file (or (when config-file (keyword config-file))
                          :default)
          all-config-file-options (set (keys config-files))
          ig-cfg-ks (when-let [env-value (System/getenv "IG_CFG_KS")]
                      (log/debug (str "Using integrant-config-keys value: '" env-value "'."))
                      (let [edn-value
                            (->> env-value
                                 (-config/interpret-string-as-csv-row)
                                 (map keyword)
                                 (set))]
                        (log/with-context+ {:ig-cfg-kys edn-value}
                          (log/info (str "Starting a subset of components: " edn-value)))
                        edn-value))]
      (when-not (all-config-file-options config-file)
        (throw (ex-info (format "Specify one of the available config files to start. Invalid option: '%s'."
                                config-file)
                        {:provided config-file
                         :available all-config-file-options})))
      (-service/as-service
       (cond-> {:cli-args            args
                :config-file         (get config-files config-file)
                :log-component-setup (log-component-setup log-component-dependencies-pred)}
         ig-cfg-ks (assoc :ig-cfg-ks ig-cfg-ks)))
      (System/exit 0))
    (catch Throwable t
      (.printStackTrace t)
      (log/error t)
      (System/exit 1))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro init-system
  "Install `dev-start!`, `dev-stop!`, `dev-reset!` and `-main` in the calling namespace.

  opts:
    :config-files                     map of :keyword -> resource-path-of-integrant-edn-file.
                                      `:default` is started when no config is selected.
    :log-component-dependencies-pred  fn of a component key; true for components the log
                                      component may depend on. Replaces
                                      `default-log-component-dependencies`; include its keys
                                      when they are in the config."
  [{:as opts :keys [config-files]}]
  `(do (defn ~(symbol "dev-start!")
         [{:as args#}]
         (gometro.common.user/dev-start! (merge args# ~opts)))
       (def ~(symbol "dev-stop!")  gometro.common.user/dev-stop!)
       (defn ~(symbol "dev-reset!")
         [& {:as args#}]
         (gometro.common.user/dev-reset! (merge args# ~opts)))
       (defn ~(symbol "-main")
         [& args#]
         (apply gometro.common.user/main-impl ~opts args#))))
