;; Copyright (c) Remco van 't Veer. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which
;; can be found in the file epl-v10.html at the root of this distribution.  By
;; using this software in any fashion, you are agreeing to be bound by the
;; terms of this license.  You must not remove this notice, or any other, from
;; this software.

(ns semira.stream
  (:refer-clojure :exclude [get])
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [semira.utils :as utils])
  (:import [java.io File FileInputStream IOException PipedInputStream PipedOutputStream]))

(def cache-dir (clojure.core/get (System/getenv) "SEMIRA_CACHE_DIR"
                                 "/tmp/semira"))
(def ^:dynamic *bitrate* 96)

(defn- cache-file [track type]
  (str cache-dir
       File/separator
       (str "semira-"
            (:id track)
            "."
            (string/replace type #"[^a-z0-1]" ""))))

(def conversions ^{:private true} (atom #{}))

(defn- convert [track type]
  (utils/mkdirs cache-dir)

  (let [filename (cache-file track type)
        decoder (condp re-matches (:path track)
                  #".*\.flac" ["flacparse" "!" "flacdec"] ; gst good
                  #".*\.ogg"  ["oggdemux" "!" "vorbisdec"] ; gst base
                  #".*\.mp3"  "mad" ; gst ugly
                  #".*\.m4a"  ["qtdemux" "!" "faad"]) ; gst good
        encoder (condp = type
                    "audio/mpeg" ["lamemp3enc" "target=bitrate" (str "bitrate=" *bitrate*) "!" "xingmux" "!" "id3mux"] ; gst ugly, bad
                    "audio/ogg" ["vorbisenc" (str "bitrate=" (* *bitrate* 1000)) "!" "oggmux"]) ; gst base
        command (flatten ["gst-launch-1.0" "-q"
                          "filesrc" "location=" (:path track) "!"
                          decoder "!"
                          "audioconvert" "!"
                          encoder "!"
                          "filesink" "location=" filename])
        process (.exec (Runtime/getRuntime) (into-array command))]
    (log/info "Started conversion: " command)

    ;; register running conversion
    (swap! conversions conj filename)

    ;; wait for conversion to finish and deregister it
    (let [guardian (fn []
                     (.waitFor process)
                     (swap! conversions disj filename)
                     (when-not (zero? (.exitValue process))
                       (printf "ERROR: %s: %s"
                               (pr-str command)
                               (slurp (.getErrorStream process)))))]
      (.start (Thread. guardian)))))

(defn- live-input [track type]
  (let [filename (cache-file track type)
        pipe (PipedInputStream.)
        out (PipedOutputStream. pipe)]
    (.start
     (Thread.
      (fn []
        (try
          (do
            ;; wait for file to appear
            (loop [n 100]
              (when (and (pos? n)
                         (not (-> filename File. .canRead)))
                (Thread/sleep 100)
                (recur (dec n))))

            ;; read from file till conversion no longer running
            (with-open [in (FileInputStream. filename)]
              (while (@conversions filename)
                (if (pos? (.available in))
                  (io/copy in out)
                  (Thread/sleep 100)))
              (while (pos? (.available in)) ;; read remainer of file
                (io/copy in out))))
          (catch IOException _) ; pipe closed
          (finally (.close out))))))
    pipe))

(defn- object-type [object & rest]
  (cond (:tracks object) :album
        (:path object)   :track))

(defmulti get
  "Return an input stream of given type for the given object."
  object-type)

(defmethod get :track [track type]
  (locking get
    (let [filename (cache-file track type)]
      (cond
       (@conversions filename)
       (live-input track type)

       (-> filename File. .canRead)
       (FileInputStream. filename)

       :else
       (do
         (convert track type)
         (live-input track type))))))

(defmethod get :album [album type]
  (let [pipe (PipedInputStream.)
        out (PipedOutputStream. pipe)]
    (.start
     (Thread.
      (fn []
        (try
          (doseq [track (:tracks album)]
            (with-open [in (get track type)]
              (io/copy in out)))
          (catch IOException _) ; pipe closed
          (finally (.close out))))))
    pipe))

(defmulti length
  "Return the length of the stream when already known, otherwise nil."
  object-type)

(defmethod length :track [track type]
  (let [filename (cache-file track type)
        file (File. filename)]
    (and (not (@conversions filename)) (.canRead file) (.length file))))

(defmethod length :album [album type]
  (reduce #(when %1 (let [len (length %2 type)] (when len (+ %1 len))))
          0 (:tracks album)))
