(ns com.eelchat.command
  (:require [com.biffweb :as biff]
            [clojure.string :as str]))

(defn command-tx
  [{:keys [biff/db channel roles params]}]
  (let [subscribe-url (second (re-find #"^/subscribe ([^\s]+)" (:text params)))
        unsubscribe-url (second (re-find #"^/unsubscribe ([^\s]+)" (:text params)))
        list-command (= (str/trimr (:text params)) "/list")
        message (fn [text]
                  {:db/doc-type :message
                   :msg/mem :system
                   :msg/channel (:xt/id channel)
                   :msg/text text
                   ;; Make sure this message comes after the user's message.
                   :msg/created-at (biff/add-seconds (java.util.Date.) 1)})]
    (cond
      (not (contains? roles :admin))
      nil

      subscribe-url
      [{:db/doc-type :subscription
        :db.op/upsert {:sub/url subscribe-url
                       :sub/chan (:xt/id channel)}}
       (message (str "Subscribed to " subscribe-url))]

      unsubscribe-url
      [{:db/op :delete
        :xt/id (biff/lookup-id db :sub/chan (:xt/id channel) :sub/url unsubscribe-url)}
       (message (str "Unsubscribed from " unsubscribe-url))]

      list-command
      [(message (apply
                 str
                 "Subscriptions:"
                 (for [url (->> (biff/q db
                                        '{:find (pull sub [:sub/url])
                                          :in [channel]
                                          :where [[sub :sub/chan channel]]}
                                        (:xt/id channel))
                                (map :sub/url)
                                sort)]
                   (str "\n - " url))))])))
