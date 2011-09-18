(ns semira.frontend
  (:require
   [semira.frontend.audio :as audio]
   [semira.frontend.html :as html]
   [semira.frontend.state :as state]
   [semira.frontend.utils :as utils]
   [goog.events :as events]
   [goog.dom :as dom]))

(declare album-listing album-row album-more-row)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn albums-query []
  (let [input (utils/by-id "search-query")]
    (. input value)))

(defn album-container [id]
  (utils/by-id (str "album-" id)))

(defn album-busy [id state]
  (let [album (dom/getAncestorByTagNameAndClass (album-container id) "li" "album")]
    (utils/busy album state)))

(defn album-collapse [id]
  (album-busy id false)
  (dom/removeChildren (album-container id)))

(defn albums-update [albums]
  (utils/busy (utils/by-id "albums-more") false)
  (let [container (utils/by-id "albums")]
    (dom/removeChildren container)
    (doseq [row (map album-row albums)]
      (dom/append container (html/build row)))
    (dom/append container (html/build (album-more-row)))))

(defn album-update [album]
  (album-busy (:id album) false)
  (let [container (album-container (:id album))]
    (dom/removeChildren container)
    (dom/append container (html/build (album-listing album)))))

(defn albums-more []
  (utils/busy (utils/by-id "albums-more") true)
  (state/more-albums (albums-query) albums-update))

(defn album-toggle [id]
  (album-busy id true)
  (if (utils/first-by-tag-class "ol" "tracks" (album-container id))
    (album-collapse id)
    (state/album id album-update)))

(defn track-play [id]
  (audio/play id))

(defn track-queue [id]
  (audio/add id))

(events/listen (utils/by-id "search")
               goog.events.EventType/SUBMIT
               #(do (. % (preventDefault))
                    (state/clear-albums)
                    (albums-more)))

(albums-more)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- track-row [track]
  (let [id (:id track)]
    [:li.track {:id (str "track-" id)}
     [:a {:onclick #(track-play id)}
      (utils/interposed-html track " / " [:artist :album :title])]
     " "
     [:a.queue {:onclick #(track-queue id )} "(+)"]
     [:span.status {:id (str "track-status-" id)}]
     [:span.length
      [:span.played {:id (str "track-current-time-" id)}]
      [:span.full (utils/seconds->time (:length track))]]]))

(defn album-listing [album]
  [:ol.tracks
   (map track-row (:tracks album))])

(defn album-row [album]
  [:li.album
   [:a {:onclick #(album-toggle (:id album))}
    (utils/interposed-html album " - " [:genre :artist :album :year])]
   " "
   [:div.album  {:id (str "album-" (:id album))}]])

(defn album-more-row []
  [:li#albums-more.more
   [:a.more {:onclick albums-more}
    [:img {:src "/images/more.png" :alt "&rarr;"}]]])

;; dev: rm -rf public/js/semira*; cljsc src '{:output-dir "public/js/semira"}' > public/js/semira.js
;; prod: rm -rf public/js/semira*; cljsc src '{:output-dir "public/js/semira" :optimizations :advanced}' > public/js/semira.js
