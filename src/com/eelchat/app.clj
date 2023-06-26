;; src/com/eelchat/app.clj
(ns com.eelchat.app
  (:require [com.biffweb :as biff :refer [q]]
            [com.eelchat.middleware :as mid]
            [com.eelchat.ui :as ui]
            [xtdb.api :as xt]))

(defn app
  "Make the app page"
  [ctx]
  (ui/app-page
    ctx
    [:p "Select a community, or create a new one."]))

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
  (if (some? (xt/entity db (parse-uuid (:id path-params))) )
    (ui/app-page
     ctx
     [:.border.border-neutral-600.p-3.bg-white.grow
      "Messages window"]
     [:.h-3]
     [:.border.border-neutral-600.p-3.h-28.bg-white
      "Compose window"])
    {:satus 303
     :headers {"Location" "/app"}}))

(def plugin
  {:routes ["" {:middleware [mid/wrap-signed-in]}
            ["/app" {:get app}]
            ["/community" {:post new-community}]
            ["/community/:id" {:get community}]]})
