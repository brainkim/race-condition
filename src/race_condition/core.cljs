(ns race-condition.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :as async])
  (:require-macros [cljs.core.async.macros :refer [go]]))
(enable-console-print!)

(def !app-state (atom {:text "Hello?"}))

(def ^:dynamic textarea nil)

(defn app
  [data owner]
  (letfn
    [(change [ev]
       (om/update! data [:text] (.. ev -target -value)))]
    (reify 
      om/IDidMount
      (did-mount [_]
        (set! textarea (om/get-node owner "textarea")))
      om/IRender
      (render [_]
        (dom/textarea #js {:onChange change
                           :ref "textarea"
                           :rows 50
                           :cols 80
                           :value (data :text)})))))

(defn mock-external-update
  []
  (go
    (<! (async/timeout 1000))
    (reset! !app-state {:text "Hello?"})
    (println "reset!")
    (<! (async/timeout 0))
    ;; When the user is typing, there is a brief moment where the dom disagrees
    ;; the app-state. This usually isn't a problem, but occasionally, the user
    ;; will trigger an update right after a re-render, causing the external
    ;; !app-state reset to be missed entirely. This is rare occurrence, but if
    ;; you smash the keyboard a bit and watch closely you'll see it happen
    ;; eventually.
    (let [app-text (@!app-state :text)
          dom-text (.-value textarea)]
      (when (not= app-text dom-text)
        (println "Possible race condition?" \newline
                 "app text:" (pr-str app-text) \newline
                 "dom text:" (pr-str dom-text))))
    (mock-external-update)))

(om/root
  app
  !app-state
  {:target (.getElementById js/document "app")})

(mock-external-update)
