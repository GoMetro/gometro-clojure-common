(ns gometro.common.data-specifications)

(def non-empty-string
  [:string {:min 1}])

;;;;;;;;;;;;;;;;;;;;

;; TODO: There are unit tests for instant-regex-string in telemetry codebase that must be moved to this codebase instead
(def ^:const valid-instant?-regex-string
  (let [common-timestamp-part "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"
        fractions-of-second-part "\\.\\d+"
        timezone-offset-hms? "(\\d{2}:\\d{2}|\\d{2}:\\d{2}:\\d{2})"
        timezone-part (str "(Z"
                           "|\\+" timezone-offset-hms?
                           "|\\-" timezone-offset-hms?
                           ")")]
    (str common-timestamp-part timezone-part
         "|"
         common-timestamp-part fractions-of-second-part timezone-part)))

(def regex:instant-string [:re {:id                 "regex-instant-string"
                                :description        "A string representing an instant in time, formatted as ISO8601. It is validated using a regular expression."
                                :error/message      "Requires an ISO 8601 formatted datetime string, example \"1111-11-11T11:11:11Z\"."
                                :json-schema/type   "string"}
                           valid-instant?-regex-string])
