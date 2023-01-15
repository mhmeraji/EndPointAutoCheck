(defproject IE/EndpointAutoCheck "1.0.0-SNAPSHOT"
  :description "A piece of web application to automatically check the user inserted endpoints according to IE cource specifications"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.3"]

                 ;; Solve dependency issues
                 [com.taoensso/encore "3.21.0"]
                 [org.clojure/tools.reader "1.3.6"]
                 ;; End of dependency clearance

                 [io.pedestal/pedestal.service "0.5.10"]
                 [io.pedestal/pedestal.jetty "0.5.10"]

                 ;; buddy
                 [buddy/buddy-hashers "1.8.158"]
                 [buddy/buddy-auth "3.0.323"]

                 ;; mongodb
                 [com.novemberain/monger "3.5.0"]

                 [com.taoensso/timbre "5.1.2"]
                 [com.taoensso/nippy "3.1.1"]
                 [aero "1.1.6"]
                 [clj-http "3.12.3"]
                 [cheshire "5.10.2"]
                 [tick "0.5.0-RC5"]

                 [clojure.java-time "0.3.3"]
                 [org.clojure/core.async "1.3.618"]

                 [hermes.lib/component "1.0.0"]

                 ;; Logging - Redirect All to SLF4J API
                 [org.slf4j/jcl-over-slf4j "1.7.36"]
                 [org.slf4j/log4j-over-slf4j "1.7.36"]
                 [org.slf4j/osgi-over-slf4j "1.7.36"]
                 [org.slf4j/jul-to-slf4j "1.7.36"]
                 ;; [org.apache.logging.log4j/log4j-to-slf4j "2.17.2"]

                 ;; NOTE that this section is fertile ground for dependency hell
                 ;;      any changes must be done with utmost care

                 ;; Logging - Log4j as Backend
                 [org.apache.logging.log4j/log4j-api "2.17.2"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.17.2"]
                 [org.apache.logging.log4j/log4j-core "2.17.2"]
                 [com.fasterxml.jackson.dataformat/jackson-dataformat-yaml "2.13.2"]
                 [com.fasterxml.jackson.core/jackson-core "2.13.2"]
                 [com.fasterxml.jackson.core/jackson-databind "2.13.2.2"]]

  :resource-paths ["config", "resources"]

  :min-lein-version "2.0.0"

  :jvm-opts       ["-XX:+UseG1GC"
                   ;; <- To avoid this problem in development systems :
                   ;; - Can't connect to X11 window server using ':0' as the value of the DISPLAY variable.
                   "-Djava.awt.headless=true"]

  :javac-options  ["-target" "11" "-source" "11"]

  :global-vars  {*warn-on-reflection* false
                 *assert*             true}

  ;; If you use HTTP/2 or ALPN, use the java-agent to pull in the correct alpn-boot dependency
;:java-agents [[org.mortbay.jetty.alpn/jetty-alpn-agent "2.0.5"]]
  :profiles {:dev     {:aliases      {"run-dev" ["trampoline" "run" "-m" ]}
                       :global-vars  {*warn-on-reflection* true
                                      *assert*             true}
                       :plugins      [[jonase/eastwood "1.2.3"]]
                       :dependencies [[io.pedestal/pedestal.service-tools "0.5.10"]]}
             :uberjar {:aot [endpoint-autocheck.core]}}

  :repositories [["releases"  {:url           "https://nexus.stellaramc.ir/repository/maven-releases/"
                               :username      :env/nexus_username
                               :password      :env/nexus_password
                               :sign-releases false}]
                 ["snapshots" {:url           "https://nexus.stellaramc.ir/repository/maven-snapshots/"
                               :username      :env/nexus_username
                               :password      :env/nexus_password
                               :sign-releases false}]]

  :main endpoint-autocheck.core)
