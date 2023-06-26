;; src/com/eelchat/app.clj
(ns com.eelchat.app
  (:require [com.biffweb :as biff :refer [q]]
            [com.eelchat.middleware :as mid]
            [com.eelchat.ui :as ui]
            [xtdb.api :as xt]))

(defn app [{:keys [session biff/db] :as ctx}]
  (let [{:user/keys [email]} (xt/entity db (:uid session))]
    (ui/page
     {}
     [:div "Signed in as " email ". "
      (biff/form
       {:action "/auth/signout"
        :class  "inline"}
       [:button.text-blue-500.hover:text-blue-800 {:type "submit"}
        "Sign out"])
      "."]
     [:.h-6]
     (biff/form
      {:action "/community"}
      [:button.btn {:type "submit"} "New Community"]))))

(defn new-community
  "Create new community"
  [{:keys [session] :as ctx}]
  (let [community-id (random-uuid)]
    (biff/submit-tx ctx
                    [{:db/doc-type :community
                      :xt/id community-id
                      :comm/title (str "Community #" (rand-int 1000))}
                     {:db/doc-type :membership
                      :mem/user (:uid session)
                      :mem/comm community-id
                      :mem/roles #{:admin}}])
    (println "redirecting to " (str "/community/" community-id))
    {:status 303
     :headers {"Location" (str "/community/" community-id)}}))

(defn community
  "Community page"
  [{:keys [biff/db path-params] :as ctx}]
  (if-some [comm (xt/entity db (parse-uuid (:id path-params)))]
    (ui/page
     {}
     [:p "Welcome to " (:comm/title comm)])
    {:satus 303
     :headers {"Location" "/app"}}))

(def plugin
  {:routes ["" {:middleware [mid/wrap-signed-in]}
            ["/app" {:get app}]
            ["/community" {:post new-community}]
            ["/community/:id" {:get community}]]})
