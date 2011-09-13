(ns katello.tests.suite
  (:refer-clojure :exclude [fn])
  (:require (katello.tests [setup :as setup]
                           [organizations :as orgs]
                           [providers :as providers]
                           [promotions :as promotions]
                           [sync_management :as sync]
                           [login :as login]
                           [environments :as envs]
                           [systems :as systems]
                           [users :as users])
   
            (katello [tasks :as tasks]
                     [conf :as conf]
                     [api-tasks :as api]
                     [validation :as validate])
            
            [test.tree :as test] 
            [com.redhat.qe.auto.selenium.selenium :as selenium])
  (:use [test.tree :only [fn]]
        [com.redhat.qe.auto.bz :only [blocked-by-bz-bugs]]))

(declare login-tests org-tests environment-tests provider-tests system-tests user-tests sync-tests)

(defn suite []
  (with-meta
    (test/before-all (fn [] (tasks/navigate :top-level))
                     {:name "login as admin"
                      :steps login/admin
                      :more (concat (org-tests)
                                    (provider-tests)
                                    (sync-tests)
                                    (promotions/tests)
                                    (system-tests)
                                    (user-tests)
                                    (test/data-driven  {:name "login as invalid user"
                                                        :blockers (blocked-by-bz-bugs "730738")} 
                                                       login/invalid
                                                       login/invalid-logins))})
    (merge {:threads 3} setup/runner-config)))

(defn org-tests []
  [{:name "create an org"
    :steps orgs/create
    :more (concat
           [{:name "delete an org"
             :blockers (blocked-by-bz-bugs "716972")
             :steps orgs/delete}
            
            {:name "duplicate org disallowed"
             :blockers (blocked-by-bz-bugs "726724")
             :steps orgs/dupe-disallowed}

            {:name "org name required"
             :blockers (blocked-by-bz-bugs "726724")
             :steps orgs/name-required}

            {:name "edit an org"
             :steps orgs/edit}]
           
           (test/data-driven {:name "org valid name"
                              :blockers (blocked-by-bz-bugs "726724")}
                             orgs/valid-name
                             orgs/valid-name-data)
           
           (environment-tests))}])

(defn environment-tests []
  [{:configuration true
    :name "create a test org"
    :steps envs/create-test-org
    :more [{:name "create environment"
            :blockers (blocked-by-bz-bugs "693797" "707274")
            :steps envs/create
            :more [{:name "delete environment"
                    :steps envs/delete}
                   
                   {:name "duplicate environment disallowed"
                    :blockers (blocked-by-bz-bugs "726724")
                    :steps envs/dupe-disallowed}
                   
                   {:name "rename an environment"
                    :steps envs/rename}

                   {:name "swap environment paths"
                    :description "Create two promotion paths.  Take
                                  the last env in the 2nd path (z),
                                  and move it to the end of the first
                                  path.  Verify that items in the path
                                  still disallow editing to set z as a
                                  prior."
                    :steps envs/swap-paths}]}

           {:name "environment name required"
            :blockers (blocked-by-bz-bugs "726724")
            :steps envs/name-required}]}])

(defn provider-tests []
  [{:name "create a custom provider"
     :steps providers/create-custom
     :more (concat
            [{:name "duplicate provider disallowed"
              :steps providers/dupe-disallowed}
             
             {:name "rename a provider"
              :steps providers/rename}

             {:name "delete a provider"
              :steps providers/delete}

             {:configuration true
              :name "create provider for testing products and repos"
              :steps providers/setup-custom
              :more [{:name "create a product"
                      :steps providers/create-product
                      :more [{:name "create a repository"
                              :steps providers/create-repo}
                             
                             {:name "delete a repository"
                              :steps providers/delete-repo}]}
                     {:name "delete a product"
                      :steps providers/delete-product}]}]
            
            (test/data-driven {:name "provider validation"}
                              providers/validation
                              (providers/validation-data)))}
   {:name "get latest subscription manifest"
    :steps providers/manifest-setup
    :configuration true
    :blockers providers/manifest-testing-blockers
    :more [{:name "upload subscription manifest"
            :steps providers/upload-manifest}]}])

(defn sync-tests []
  [{:name "simple sync"
    :description "Sync a product with just a few packages in one repo."
    :blockers (juxt (constantly "auto broken, working on fix") (blocked-by-bz-bugs "705355" "711105" "712318" "715004" "727674" "727627"))
    :steps sync/simple}
   {:name "create a sync plan"
    :steps sync/create-plan
    :more (concat [{:name "edit a sync plan"
                    :steps sync/edit-plan}
                   {:name "rename a sync plan"
                    :steps sync/rename-plan}
                   {:name "duplicate sync plan disallowed"
                    :steps sync/dupe-disallowed}]
                  (test/data-driven {:name "sync plan validation"}
                                    sync/plan-validate
                                    (sync/plan-validation-data)))}])

(defn system-tests []
  [{:name "setup environment for systems"
    :configuration true
    :steps systems/create-env
    :blockers (blocked-by-bz-bugs "717408" "728357")
    :more [{:name "rename a system"
            :description "Adds a system via REST api and then renames it in the UI"
            :steps systems/rename}
           
           {:name "system appears on environment page"
            :description "Registers a system to an environment, and verifies it appears
                          on the Systems/Registered/Environments/[environment] page."
            :blockers (blocked-by-bz-bugs "738054")
            :steps systems/in-env}
           
           {:name "subscribe a system to a product"
            :blockers (blocked-by-bz-bugs "733780" "736547")
            :steps systems/subscribe}

           {:name "create an activation key"
            :steps systems/create-activation-key
            :more [{:name "delete an activation key"
                    :steps systems/remove-activation-key}

                   {:name "duplicate activation key disallowed"
                    :steps systems/activation-key-dupe-disallowed}]}]}])

(defn user-tests []
  [{:name "create a user"
    :steps users/create
    :more [{:name "edit a user"
            :blockers (blocked-by-bz-bugs "720469")
            :steps users/edit}]}])

(defn -main [ & args]
  (let [reports (test/run-suite (suite))]
    (println "Blockers: " (->> reports
                               vals
                               (map deref)
                               (mapcat :blocked-by)
                               distinct))))
