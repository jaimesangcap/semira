;; Copyright (c) Remco van 't Veer. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
;; can be found in the file epl-v10.html at the root of this distribution.  By
;; using this software in any fashion, you are agreeing to be bound by the
;; terms of this license.  You must not remove this notice, or any other, from
;; this software.

(ns semira.frontend.audio
  (:refer-clojure :exclude [next])
  (:require [cljs.core.async :as async]
            [reagent.core :as reagent])
  (:require-macros [cljs.core.async.macros :as async]))

(defonce state-atom (reagent/atom nil))
(defonce play-queue-atom (reagent/atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- player []
  (.getElementById js/document "player"))

(defn- track-uri [{:keys [id]}]
  (when-let [player (player)]
    (cond (and (.-canPlayType player)
               (not= "" (.canPlayType player  "audio/mpeg")))
          (str "/stream/track/" id ".mp3")

          (and (.-canPlayType player)
               (not= "" (.canPlayType player  "audio/ogg")))
          (str "/stream/track/" id ".ogg")

          :else
          (str "/stream/track/" id ".mp3"))))

(defn- load-and-play []
  (when-let [player (player)]
    (let [{:keys [tracks position]} @play-queue-atom]
      (when-let [current (nth tracks position nil)]
        (.pause player)
        (aset player "autoplay" true)
        (aset player "src" (track-uri current))
        (.load player)
        (.play player)
        (swap! state-atom assoc :current-track current)))))

(defn stop []
  (when-let [player (player)]
    (swap! state-atom assoc :current-track nil)
    (when-not (.-ended player)
      (try
        (aset player "src" "")
        (.load player)
        (catch js/Error e)))))

(defn play [tracks pos]
  (stop)
  (reset! play-queue-atom {:position pos, :tracks tracks})
  (load-and-play))

(defn play-pause []
  (when-let [player (player)]
    (if (.-paused player)
      (.play player)
      (.pause player))))

(defn next []
  (swap! play-queue-atom update :position inc)
  (load-and-play))

(defn prev []
  (swap! play-queue-atom update :position dec)
  (load-and-play))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- round-secs [n]
  (.round js/Math n))

(defn- get-player-state []
  (let [player (player)]
    {:error (when (.-error player)
              (-> player .-error .-code))
     :current-time (round-secs (aget player "currentTime"))
     :ended (aget player "ended")
     :muted (aget player "muted")
     :network-state (case (aget player "networkState")
                      0 :empty
                      1 :idle
                      2 :loading
                      3 :no-source
                      :unknown)
     :ready-state (case (aget player "readyState")
                    0 :nothing
                    1 :meta-data
                    2 :current-data
                    3 :future-data
                    4 :enough-data
                    :unknown)
     :seeking (aget player "seeking")
     :volume (aget player "volume")
     :paused (aget player "paused")}))

(defn- update-state! []
  (let [{:keys [current-track] :as state} @state-atom
        new-state (assoc (get-player-state)
                         :current-track current-track)]
    (when-not (= new-state state)
      (reset! state-atom new-state))
    new-state))

(defn state []
  @state-atom)

(defn setup! []
  (async/go-loop []
    (async/<! (async/timeout 50))

    (update-state!)

    ;; Advance to next track when player get status ended.  Listening for
    ;; the "ended" event isn't very reliable unfortunately.
    (when-let [player (player)]
      (when (.-ended player)
        (next)))

    (recur)))
