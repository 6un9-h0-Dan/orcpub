(ns orcpub.template
  (:require [clojure.spec :as spec]
            [orcpub.modifiers :as modifiers]
            [orcpub.common :as common]))

(spec/def ::name string?)
(spec/def ::key keyword?)
(spec/def ::min (spec/nilable (spec/int-in 0 100)))
(spec/def ::max (spec/nilable (spec/int-in 1 100)))
(spec/def ::attribute (spec/keys :req [::name ::key]))
(spec/def ::attributes (spec/+ ::attribute))
(spec/def ::derived-value (spec/or :func (spec/fspec :args (spec/cat :entity map?))
                                   :keyword keyword?))
(spec/def ::derived-attribute (spec/keys :req [::name ::key ::derived-value]))
(spec/def ::derived-attributes (spec/+ ::derived-attribute))
(spec/def ::modifiers (spec/+ ::modifiers/modifier))
(spec/def ::option (spec/keys :req [::name ::key]
                              :opt [::modifiers ::selections]))
(spec/def ::options (spec/+ ::option))
(spec/def ::selection (spec/keys :req [::name ::key ::options]
                                 :opt [::min ::max]))
(spec/def ::selections (spec/* ::selection))
(spec/def ::template (spec/keys :opt [::attributes ::derived-attributes ::selections]))

(spec/def ::modifier-map-value (spec/or :modifiers ::modifiers
                                        :modifier-map ::modifier-map))
(spec/def ::modifier-map-entry (spec/tuple keyword? ::modifier-map-value))
(spec/def ::modifier-map (spec/map-of keyword? (spec/or :modifier-map-value ::modifier-map-value
                                                        :min ::min
                                                        :max ::max)))

(defn selection-cfg [{:keys [name key source page options help min sequential? new-item-fn] :as cfg}]
  (let [max (if (find cfg :max) (:max cfg) 1)]
    {::name name
     ::key (or key (common/name-to-kw name))
     ::source (or source :phb)
     ::page page
     ::options (vec options)
     ::help help
     ::min (or min 1)
     ::max max
     ::sequential? (boolean sequential?)
     ::new-item-fn new-item-fn}))

(defn selection-with-key
  ([name key options]
   (selection-with-key name key options 1 1))
  ([name key options min max &[sequential? new-item-fn]]
   {::name name
    ::key key
    ::options (vec options)
    ::min min
    ::max max
    ::sequential? (boolean sequential?)
    ::new-item-fn new-item-fn}))

(defn selection
  ([name options]
   (selection name options 1 1))
  ([name options min max &[sequential? new-item-fn]]
   (selection-with-key name (common/name-to-kw name) options min max sequential? new-item-fn)))

(defn selection? [name options]
  (selection name options 0 1))

(defn selection+ [name new-item-fn options]
  (selection name options 1 nil false new-item-fn))

(defn selection* [name new-item-fn options]
  (selection name options 0 nil false new-item-fn))

(defn sequential-selection [name new-item-fn options]
  (selection name options 1 nil true new-item-fn))

(defn option-cfg [{:keys [name key help selections modifiers prereqs] :as cfg}]
  {::name name
   ::key (or key (common/name-to-kw name))
   ::help help
   ::selections selections
   ::modifiers modifiers
   ::prereqs prereqs})

(defn option [name key selections modifiers & [prereqs]]
  (cond-> {::name name
           ::key key}
    selections (assoc ::selections (vec selections))
    modifiers (assoc ::modifiers modifiers)
    prereqs (assoc ::prereqs prereqs)))

(defn select-option [name selections & [prereqs]]
  (option name (common/name-to-kw name) selections nil prereqs))

(defn mod-option [name modifiers & [prereqs]]
  (option name (common/name-to-kw name) nil modifiers prereqs))

(declare make-modifier-map-from-selections)
(declare make-plugin-map-from-selections)

(defn make-modifier-map-entry-from-option [option]
  [(::key option)
   (let [modifiers option
         selections (::selections option)]
     (if selections
       (merge (make-modifier-map-from-selections selections) modifiers)
       modifiers))])

(spec/fdef
 make-modifier-map-entry-from-option
 :args ::option
 :ret ::modifier-map-entry)

(defn make-modifier-map-entry-from-selection [selection]
  [(::key selection)
   (into (select-keys selection [::min ::max])
         (map make-modifier-map-entry-from-option (::options selection)))])

(spec/fdef
 make-modifier-map-entry-from-selection
 :args ::selection
 :ret ::modifier-map-entry)

(defn make-modifier-map-from-selections [selections]
  (into {} (map make-modifier-map-entry-from-selection selections)))

(spec/fdef
 make-modifier-map-entry-from-selections
 :args ::selections
 :ret ::modifier-map)

(defn make-modifier-map [template]
  (make-modifier-map-from-selections (::selections template)))

(spec/fdef
 make-modifier-map
 :args ::template
 :ret ::modifier-map)

(spec/fdef make-modifier-map
           :args (spec/cat :template ::template)
           :ret ::modifier-map)
