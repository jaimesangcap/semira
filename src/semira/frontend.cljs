(ns semira.frontend
  (:require
   [semira.frontend.audio :as audio]
   [semira.frontend.html :as html]
   [semira.frontend.state :as state]
   [semira.frontend.utils :as utils]
   [goog.events :as events]
   [goog.dom :as dom]))

(def page-size 15)
(declare album-listing album-row album-more-row album-not-found)

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

(defn albums-update [albums & {offset :offset end-reached :end-reached}]
  (doseq [id ["albums-more" "search-query"]]
    (utils/busy (utils/by-id id) false))

  (let [container (utils/by-id "albums")]
    (cond (empty? albums)
          (do (dom/removeChildren container)
              (dom/append container (html/build (album-not-found))))

          (or (nil? offset) (= offset 0))
          (do
            (dom/removeChildren container)
            (doseq [row (map album-row albums)]
              (dom/append container (html/build row))))

          :else
          (do
            (when (utils/by-id "albums-more")
              (dom/removeNode (utils/by-id "albums-more")))
            (doseq [row (map album-row (drop offset albums))]
              (dom/append container (html/build row)))
            (set! (.. js/window location hash)
                  (str "album-"
                       (:id (first (drop (dec offset) albums)))))))
    (when-not end-reached
      (dom/append container (html/build (album-more-row))))))

(defn album-update [album]
  (album-busy (:id album) false)
  (let [container (album-container (:id album))]
    (dom/removeChildren container)
    (dom/append container (html/build (album-listing album)))))

(defn albums-more []
  (utils/busy (utils/by-id "albums-more") true)
  (state/more-albums (albums-query) page-size albums-update))

(defn album-toggle [id]
  (album-busy id true)
  (if (utils/first-by-tag-class "ol" "tracks" (album-container id))
    (album-collapse id)
    (state/album id album-update)))

(defn album-search []
  (utils/busy (utils/by-id "search-query") true)
  (state/clear-albums)
  (albums-more))

(defn track-play [tracks]
  (if (= (first tracks) (audio/current))
    (audio/play-pause)
    (audio/play tracks)))

(events/listen (utils/by-id "search")
               goog.events.EventType/SUBMIT
               #(do (. % (preventDefault))
                    (album-search)))

(albums-more)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn track-row [track album]
  (let [id (:id track)]
    [:li.track {:id (str "track-" id)
                :onclick #(track-play (drop-while (fn [x] (not= id (:id x)))
                                                  (map (fn [x] {:id (:id x) :album-id (:id album)})
                                                       (:tracks album))))}
     [:span.title
      (utils/interposed-html track " / " [:artist :album :title])]
     " "
     [:span.status {:id (str "track-status-" id)}]
     [:span.length
      [:span.played {:id (str "track-current-time-" id)}]
      [:span.full (utils/seconds->time (:length track))]]]))

(defn album-listing [album]
  [:ol.tracks
   (map (fn [t] (track-row t album)) (:tracks album))])

(defn album-row [album]
  [:li.album
   [:div.album-info  {:onclick #(album-toggle (:id album))}
    (utils/interposed-html album " " [:year :genre :artist :album])]
   " "
   [:div.album  {:id (str "album-" (:id album))}]])

(defn album-not-found []
  [:li.not-found "not found.."])

(defn album-more-row []
  [:li#albums-more.more {:onclick albums-more}
   [:img {:src "/images/more.png" :alt "&rarr;"}]])

;; dev: rm -rf public/js/semira*; cljsc src '{:output-dir "public/js/semira"}' > public/js/semira.js
;; prod: rm -rf public/js/semira*; cljsc src '{:output-dir "public/js/semira" :optimizations :advanced}' > public/js/semira.js
