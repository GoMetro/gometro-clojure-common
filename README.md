# GoMetro Common Clojure library

Include this as a git-sha dependency. Example in your client `deps.edn`:

```edn
{:deps      {org.clojure/clojure                       {:mvn/version "1.12.0"}
             com.github.gometro/gometro-clojure-common {:git/url "git@github.com:GoMetro/gometro-clojure-common.git"
                                                        :git/sha "fe7c44e3a7f1cae133d515a59076efbdffebb8e3"}
	     }
}
```

## Service entry point

```clojure
(ns my.service.main
  (:require [gometro.common.user :as -user])
  (:gen-class))

(-user/init-system {:config-files {:default "my/service/config.edn"}})
```

Installs `-main`, `dev-start!`, `dev-stop!` and `dev-reset!`. Every
component depends on `:gometro.common.logging/timbre`, except those in
`gometro.common.user/default-log-component-dependencies`.
`:log-component-dependencies-pred` replaces that set.

## Logging

```clojure
:gometro.common.logging/timbre
{:min-level :info
 :appenders {:println    {:colorized-output? true}
             :cloudwatch {:batch-period       5000
                          :log-group          "my-service"
                          :log-stream         "main"
                          :create-log-stream? true
                          :aws-client-cfg     #ig/ref :afrolabs.components.aws.client-config/aws-client-config
                          :disabled?          false}
             :sentry     {:client    #ig/ref :afrolabs.components.sentry/client
                          :min-level :warn
                          :tag-keys  [:request-id]}}}
```

All appenders are optional. `:min-level` is a timbre level or a timbre
namespace filter. `:sentry` `:min-level` and `:tag-keys` are passed to
`afrolabs.components.sentry/timbre-appender`; see the sentry component docs
in afrolabs-clj.
