(ns redgenes.components.search.views
  (:require [reagent.core :as reagent]
            [clojure.string :as str]
            [redgenes.components.search.resultrow :as resulthandler]
            [redgenes.components.search.filters :as filters]
            [re-frame.core :as re-frame :refer [subscribe dispatch]]
))

;;;;TODO: Cleanse the API/state arguments being passed around from the functions here. This is legacy of an older bluegenes history and module based structure.
;;;;TODO ALSO: abstract away from IMJS.
;;;;TODO: probably abstract events to the events... :D this file is a mixture of views and handlers, but really we just want views in the view file.

(def search-results (reagent.core/atom {:results nil}))
(def max-results 99);;todo - this is only used in a cond right now, won't modify number of results returned. IMJS was being tricky;

(defn sort-by-value [result-map]
 "Sort map results by their values. Used to order the category maps correctly"
 (into (sorted-map-by (fn [key1 key2]
                        (compare [(get result-map key2) key2]
                                 [(get result-map key1) key1])))
       result-map))

(defn results-handler [results searchterm]
   "Store results in local state once the promise comes back."
   (if (:active-filter @search-results)
     ;;if we're resturning a filter result, leave the old facets intact.
     (swap! search-results
       assoc :results (.-results results) :term searchterm)
     ;;if we're returning a non-filtered result, add new facets to the atom
     (reset! search-results
       {
       :results  (.-results results)
       :term searchterm
       :highlight-results (:highlight-results @search-results)
       :facets {
         :organisms (sort-by-value (js->clj (aget results "facets" "organism.shortName")))
         :category (sort-by-value (js->clj (aget results "facets" "Category")))}}))
   )

     (defn search
       "search for the given term via IMJS promise. Filter is optional"
       [& filter]
         (let [searchterm @(re-frame/subscribe [:search-term])
               mine (js/imjs.Service. (clj->js {:root @(subscribe [:mine-url])}))
               search {:q searchterm :Category filter}
               id-promise (-> mine (.search (clj->js search)))]
           (-> id-promise (.then
               (fn [results]
                 (results-handler results searchterm))))))

     (defn is-active-result? [result]
       "returns true is the result should be considered 'active' - e.g. if there is no filter at all, or if the result matches the active filter type."
         (or
           (= (:active-filter @search-results) (.-type result))
           (nil? (:active-filter @search-results))))

     (defn count-total-results [state]
       "returns total number of results by summing the number of results per category. This includes any results on the server beyond the number that were returned"
       (reduce + (vals (:category (:facets state))))
       )

     (defn count-current-results []
       "returns number of results currently shown, taking into account result limits nd filters"
       (count
         (remove
           (fn [result]
             (not (is-active-result? result))) (:results @search-results))))

     (defn results-count []
       "Visual component: outputs the number of results shown."
         [:small " Displaying " (count-current-results) " of " (count-total-results @search-results) " results"])

     (defn load-more-results [api search-term]
       (search (:active-filter @search-results))
       )

     (defn results-display [api search-term]
       "Iterate through results and output one row per result using result-row to format. Filtered results aren't output. "
       [:div.results
         [:h4 "Results for '" (:term @search-results) "'"  [results-count]]
        (.log js/console "%csearch-results" "color:hotpink;font-weight:bold;" (clj->js @search-results))
         [:form
           (doall (let [state search-results
             ;;active-results might seem redundant, but it outputs the results we have client side
             ;;while the remote results are loading. Good for slow connections.
             active-results (filter (fn [result] (is-active-result? result)) (:results @state))
             filtered-result-count (get (:category (:facets @state)) (:active-filter @state))]
               ;;load more results if there are less than our preferred number, but more than
               ;;the original search returned
               (cond (and  (< (count-current-results) filtered-result-count)
                           (<= (count-current-results) max-results))
                 (load-more-results api search-term))
               ;;output em!
               (for [result active-results]
                 ^{:key (.-id result)}
                 [resulthandler/result-row {:result result :state state :api api :search-term @search-term}])))]
        ])


     (defn search-form [search-term api]
       "Visual form component which handles submit and change"
       [:div.search-fullscreen
        [:div.response
           [filters/facet-display search-results api search @search-term]
           [results-display api search-term]]])

     (defn ^:export main []
       (let [global-search-term (re-frame/subscribe [:search-term])]
       (reagent/create-class
         {:reagent-render
           (fn render [{:keys [state upstream-data api]}]
             [search-form global-search-term api]
             )
           :component-will-mount (fn [this]
             (let [api (:api (reagent/props this))]
               (cond (some? global-search-term)
                   (search))
               ))
           :component-will-update (fn [this]
             (let [api (:api (reagent/props this))]
               (search)))
     })))
