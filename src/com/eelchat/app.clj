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

(defn join-community
  "Join a community"
  [{:keys [user community] :as ctx}]
  (biff/submit-tx
   ctx
   [{:db/doc-type :membership
     :db.op/upsert {:mem/user (:xt/id user)
                    :mem/comm (:xt/id community)}
     :mem/roles [:db/default #{}]}])
  {:status 303
   :headers {"Location" (str "/community/" (:xt/id community))}})

(defn new-channel
  "Create new channel"
  [{:keys [community roles] :as ctx}]
  (if (and community (contains? roles :admin))
    (let [chan-id (random-uuid)]
      (biff/submit-tx
       ctx
       [{:db/doc-type :channel
         :xt/id       chan-id
         :chan/title  (str "Channel #" (rand-int 1000))
         :chan/comm   (:xt/id community)}])
      {:status 303
       :headers {"Location" (str "/community/" (:xt/id community) "/channel/" chan-id)}})
    {:status 403
     :body "Forbidden."}))

(defn delete-channel [{:keys [channel roles] :as ctx}]
  (when (contains? roles :admin)
    (biff/submit-tx ctx
                    [{:db/op :delete
                      :xt/id (:xt/id channel)}]))
  [:<>])

(defn community
  "Community page"
  [{:keys [biff/db user community] :as ctx}]
  (let [member (some (fn [mem]
                       (= (:xt/id community) (get-in mem [:mem/comm :xt/id])))
                     (:user/mems user))]
    (ui/app-page
     ctx
     (if member
       [:<>
        [:.border.border-neutral-600.p-3.bg-white.grow
         "Messages window"]
        [:.h-3]
        [:.border.border-neutral-600.p-3.h-28.bg-white
         "Compose window"]]
       [:<>
        [:.grow]
        [:h1.text-3xl.text-center (:comm/title community)]
        [:.h-6]
        (biff/form
         {:action (str "/community/" (:xt/id community) "/join")
          :class "flex justify-center"}
         [:button.btn {:type "submit"} "Join this community"])
        [:div {:class "grow-[1.75]"}]]))))

(defn channel-page
  "Show channel"
  [ctx]
  (community ctx))

(defn wrap-community
  "Add community and user roles to ctx"
  [handler]
  (fn [{:keys [biff/db user path-params] :as ctx}]
    (if-some [community (xt/entity db (parse-uuid (:id path-params)))]
      (let [roles (->> (:user/mems user)
                       (filter (fn [mem]
                                 (= (:xt/id community) (get-in mem [:mem/comm :xt/id]))))
                       first
                       :mem/roles)]
        (handler (assoc ctx
                        :community community
                        :roles roles)))
      {:status 303
       :headers {"Location" "/app"}})))

(defn wrap-channel
  [handler]
  (fn [{:keys [biff/db user community path-params] :as ctx}]
    (let [channel (xt/entity db (parse-uuid (:chan-id path-params)))]
      (if (= (:chan/comm channel) (:xt/id community))
        (handler (assoc ctx :channel channel))
        {:status  303
         :headers {"Location" (str "/community/" (:xt/id community))}}))))

(def plugin
  {:routes ["" {:middleware [mid/wrap-signed-in]}
            ["/app" {:get app}]
            ["/community" {:post new-community}]
            ["/community/:id" {:middleware [wrap-community]}
             [""      {:get community}]
             ["/join" {:post join-community}]
             ["/channel" {:post new-channel}]
             ["/channel/:chan-id" {:middleware [wrap-channel]}
              ["" {:get    channel-page
                   :delete delete-channel}]]]]})
