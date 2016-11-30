(set-env!
  :source-paths #{"src"}
  :resource-paths #{"test"}
  :dependencies '[[org.clojure/clojure "1.8.0"]
                  [org.zeromq/jeromq   "0.3.6"]
                  [clj-wallhack        "1.0.1"]
                  [potemkin            "0.4.3"]
                  [adzerk/bootlaces    "0.1.13" :scope "test"]
                  [adzerk/boot-test    "1.1.2"]])

(require '[adzerk.boot-test :refer :all]
         '[adzerk.bootlaces :refer :all])

(def +version+ "0.5.1")
(bootlaces! +version+)

(task-options!
  pom {:project 'io.djy/ezzmq
       :version +version+
       :description "A small library of opinionated ZeroMQ boilerplate for Clojure"
       :url "https://github.com/daveyarwood/ezzmq"
       :scm {:url "https://github.com/daveyarwood/ezzmq"}
       :license {"name" "Eclipse Public License"
                 "url"  "http://www.eclipse.org/legal/epl-v10.html"}}
  test {:namespaces '#{ezzmq.poll-test
                       ezzmq.pub-sub-test
                       ezzmq.push-pull-test
                       ezzmq.req-rep-test}})

(deftask deploy
  "Builds uberjar, installs it to local Maven repo, and deploys it to Clojars."
  []
  (comp (build-jar) (push-release)))
