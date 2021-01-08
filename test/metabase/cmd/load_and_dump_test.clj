(ns metabase.cmd.load-and-dump-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [metabase.cmd.compare-h2-dbs :as compare-h2-dbs]
            [metabase.cmd.copy.h2 :as h2]
            [metabase.cmd.dump-to-h2 :as dump-to-h2]
            [metabase.cmd.load-from-h2 :as load-from-h2]
            [metabase.db.connection :as mdb.connection]
            [metabase.db.setup :as mdb.setup]
            [metabase.db.spec :as db.spec]
            [metabase.driver :as driver]
            [metabase.models.setting :as setting]
            [metabase.test :as mt]
            [metabase.test.data.interface :as tx]
            [toucan.db :as db]
            [toucan.util.test :as tt]))

(defn- abs-path
  [path]
  (.getAbsolutePath (io/file path)))

(deftest load-and-dump-test
  (testing "loading data to h2 and porting to DB and migrating back to H2"
    (let [h2-fixture-db-file (abs-path "frontend/test/__runner__/test_db_fixture.db")
          h2-file            (abs-path "/tmp/out.db")
          db-name            "dump-test"]
      (mt/test-drivers #{:postgres :mysql :h2}
        (h2/delete-existing-h2-database-files! h2-file)
        (binding [mdb.connection/*db-type*   driver/*driver*
                  mdb.connection/*jdbc-spec* (if (= driver/*driver* :h2)
                                               {:subprotocol "h2"
                                                :subname     (format "mem:%s;DB_CLOSE_DELAY=10" (mt/random-name))
                                                :classname   "org.h2.Driver"}
                                               (let [details (tx/dbdef->connection-details driver/*driver*
                                                                                           :db {:database-name db-name})]
                                                 ((case driver/*driver*
                                                    :postgres db.spec/postgres
                                                    :mysql    db.spec/mysql) details)))]
          (when-not (= driver/*driver* :h2)
            (tx/create-db! driver/*driver* {:database-name db-name}))
          (load-from-h2/load-from-h2! h2-fixture-db-file)
          (dump-to-h2/dump-to-h2! h2-file)
          (is (not (compare-h2-dbs/different-contents?
                    h2-file
                    h2-fixture-db-file))))))))
