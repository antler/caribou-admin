(ns caribou.admin.core
  (:use [ring.middleware.json-params :only (wrap-json-params)]
        [ring.middleware.multipart-params :only (wrap-multipart-params)]
        [ring.middleware.params :only (wrap-params)]
        [ring.middleware.file :only (wrap-file)]
        [ring.middleware.head :only (wrap-head)]
        [ring.middleware.file-info :only (wrap-file-info)]
        [ring.middleware.resource :only (wrap-resource)]
        [ring.middleware.nested-params :only (wrap-nested-params)]
        [ring.middleware.keyword-params :only (wrap-keyword-params)]
        [ring.middleware.reload :only (wrap-reload)]
        [ring.middleware.session :only (wrap-session)]
        [ring.middleware.session.cookie :only (cookie-store)]
        [ring.middleware.cookies :only (wrap-cookies)]
        [ring.middleware.content-type :only (wrap-content-type)])
  (:require [clojure.string :as string]
            [swank.swank :as swank]
            [lichen.core :as lichen]
            [caribou
             [config :as config]
             [db :as db]
             [model :as model]]
            [caribou.app
             [i18n :as i18n]
             [pages :as pages]
             [template :as template]
             [halo :as halo]
             [middleware :as middleware]
             [request :as request]
             [handler :as handler]
             [controller :as controller]]
            [caribou.admin
             [helpers :as helpers]
             [routes :as routes]
             [hooks :as hooks]]))

(declare handler)

(def base-helpers
  {:route-for (fn [slug params & additional]
                (pages/route-for slug (apply merge (cons params additional))))
   :equals =})

(defn reload-pages
  []
  (pages/add-page-routes routes/admin-routes 'caribou.admin.controllers ""))

(defn open-page?
  [uri]
  (contains?
   #{(pages/route-for :admin.login {})
     (pages/route-for :admin.logout {})
     (pages/route-for :admin.forgot_password {})
     (pages/route-for :admin.submit_login {})}
   uri))

(defn user-required
  [handler]
  (fn [request]
    (if (or (seq (-> request :session :admin :user))
            (open-page? (:uri request)))
      (handler request)
      (controller/redirect
       (pages/route-for :admin.login {})))))

(defn get-models
  [handler]
  (fn [request]
    (let [models (model/gather
                  :model
                  {:where {:locked false :join_model false}
                   :order {:id :asc}})]
      (handler (assoc request :user-models models)))))

(defn days-in-seconds
  [days]
  (* 60 60 24 days))

(defn provide-helpers
  [handler]
  (fn [request]
    (let [request (merge request base-helpers helpers/all)]
      (handler request))))


(defn admin-wrapper
  [handler]
  (-> handler
      (provide-helpers)
      (user-required)
      (get-models)))

(defn init
  []
  (config/init)
  (model/init)
  (hooks/init)
  (i18n/init)
  (template/init)
  (reload-pages)
  (halo/init
   {:reload-pages reload-pages
    :halo-reset handler/reset-handler})
  (def handler
    (-> (handler/handler)
        (admin-wrapper)
        (wrap-reload)
        (wrap-file (@config/app :asset-dir))
        (wrap-resource (@config/app :public-dir))
        (wrap-file-info)
        (wrap-head)
        (lichen/wrap-lichen (@config/app :asset-dir))
        (middleware/wrap-servlet-path-info)
        (middleware/wrap-xhr-request)
        (request/wrap-request-map)
        (wrap-json-params)
        (wrap-multipart-params)
        (wrap-keyword-params)
        (wrap-nested-params)
        (wrap-params)
        (db/wrap-db @config/db)
        (wrap-session {:store (cookie-store {:key "vEanzxBCC9xkQUoQ"})
                       :cookie-name "caribou-admin-session"
                       :cookie-attrs {:max-age (days-in-seconds 90)}})
        (wrap-cookies)))
  (when-not (= :production (config/environment))
    (swank/start-server :host "127.0.0.1" :port 4011)))
