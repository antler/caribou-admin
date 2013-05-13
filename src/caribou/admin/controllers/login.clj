(ns caribou.admin.controllers.login
  (:use caribou.app.controller
        [caribou.app.pages :only [route-for]])
  (:require [caribou.model :as model]
            [caribou.auth :as auth]))

;; (import org.mindrot.jbcrypt.BCrypt)

(def nothing (constantly nil))

;; (defn hash-pw
;;   "hash a password to store it in the accounts db"
;;   [pass]
;;   (. BCrypt hashpw pass (. BCrypt gensalt 12)))

;; (defn check-pw
;;   "check a raw password against a hash from the accounts db"
;;   [pass hash]
;;   (. BCrypt checkpw pass hash))

(defn login
  [request]
  (render request))

(defn submit-login
  [request]
  (let [email (-> request :params :email)
        password (-> request :params :password)
        locale (or (-> request :params :locale) "global")
        target (or (-> request :params :target)
                   (route-for :admin.models {:locale locale :site "admin"}))
        account (model/pick :account {:where {:email email}})
        match? (and (seq password)
                    (seq (:crypted-password account))
                    (auth/check-password password (:crypted-password account)))
        target (if-not match?
                 (str (route-for :admin.login {:locale locale :site "admin"})
                      "&target=" target)
                 target)
        login (if match? "success" "failure")
        session (:session request)
        session (if-not match?
                  session
                  (assoc session
                    :admin
                    {:user (dissoc account :created-at :updated-at)
                     :locale locale}))]
    (println "USER in submit-login is " (-> session :admin :user))
    (redirect target {:session session :login login})))


(defn create-login
  [request]
  (let [email (-> request :params :email)
        password (-> request :params :password)
        first (-> request :params :first)
        last (-> request :params :last)
        hash (auth/hash-password password)
        account (model/create :account {:email email
                                        :first-name first
                                        :last-name last
                                        :crypted-password hash})
        target (route-for :admin.new-account (select-keys request [:site :locale]))
        user (dissoc account :created-at :updated-at)]
    (redirect target {:session (:session request) :user user})))

;; allow target
(defn logout
  [request]
  (render request {:session (dissoc (:session request) :admin)}))

(defn forgot-password
  [request]
  (let [email (-> request :params :email)]
    ;; send a message to that email that lets them reset their pw
    (render request)))

(comment
  ;; here is a way to test out the create-login controller from the repl
  (caribou.admin.controllers.login/create-login
   {:params {:email "justin@weareinstrument.com"
             :password "419truth"
             :first "Justin"
             :last "Lewis"}
    :template (constantly "")}))

(comment
  (caribou.admin.controllers.login/submit-login
   {:params {:email "justin@weareinstrument.com"
             :password "419truth"
             :locale "en_US"}
    :template (constantly "")}))


(comment
  (caribou.admin.controllers.login/create-login
   {:params {:email "phong@weareinstrument.com"
             :password "3Ge5pm!N"
             :first "Phong"
             :last "Ho"}
    :template (constantly "")}))

