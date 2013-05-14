(ns caribou.admin.controllers.roles
  (:use [caribou.app.controller]
        [caribou.app.pages :only [route-for select-route]])
  (:require [caribou.admin.rights :as rights]
            [caribou.permissions :as permissions]))

(defn edit-roles
  [request]
  (rights/with-permissions "roles/edit-roles" request
    (fn [permissions request]
      (let [role (rights/pick permissions :role
                              {:where {:title (-> request :params :title)}
                               :include {:permissions {:model {}}}})
            extract-perm (fn [p]
                           (let [m (:mask p)]
                             {:read-checked
                              (if (permissions/check-read m) "checked" "")
                              :write-checked
                              (if (permissions/check-write m) "checked" "")
                              :create-checked
                              (if (permissions/check-create m) "checked" "")
                              :delete-checked
                              (if (permissions/check-delete m) "checked" "")
                              :name (-> p :model :slug)}))
            perms (map extract-perm (:permissions role))
            perms (sort-by :name perms)
            request (assoc request :title (:title role) :tasks perms)]
        (render request)))))

(defn submit-edit-roles
  [request]
  (rights/with-permissions "roles/submit-edit-roles" request
    (fn [permissions request]
      (let [title (-> request :params :title)
            role (rights/pick permissions :role
                              {:where {:title title}
                               :include {:permissions {:model {}}}})
            keystrings (map name (filter keyword? (keys (:params request))))
            split_ (fn [s]
                     (loop [a "" b s]
                       (if (or (empty? b) (= (first b) \_))
                         [(apply str a) (apply str (rest b))]
                         (recur (concat a [(first b)]) (rest b)))))
            keyvecs (map split_ keystrings)
            perm-key? (fn [k]
                        (get #{"create" "write" "read" "delete"} (first k)))
            perm-keys (filter perm-key? keyvecs)
            perm-map (group-by last perm-keys)
            old-perms (reduce (fn [mp [k v]] (assoc mp k v)) {}
                              (map #(vector (-> % :model :slug keyword)
                                            [(:mask %) (:id %)])
                                   (:permissions role)))
            null-perms (reduce (fn [mp [k v]] (assoc mp k v)) {}
                               (map (fn [x] [(-> x :model :slug keyword) 0])
                                    (:permissions role)))
            perm-masks (reduce (fn [mp [k v]] (assoc mp k v)) {}
                               (map (fn [[slug perms]]
                                      [(keyword slug)
                                       (apply permissions/mask
                                              (map (comp keyword first) perms))])
                                    perm-map))
            all-masks (merge null-perms perm-masks)]
        (doseq [perm all-masks]
          (let [key (first perm)
                orig-perm (key old-perms)
                id (second orig-perm)]
            (rights/update permissions :permission
                           id {:mask (second perm)})))
        (redirect (route-for :admin.edit-roles (select-route :admin.edit-roles (:params request)))
                  (:session request))))))
