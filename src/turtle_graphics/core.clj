(ns turtle-graphics.core
  (:require [quil.core :as q]
            [quil.middleware :as m]
            [turtle-graphics.turtle :as turtle]
            [clojure.core.async :as async]
            [clojure.tools.namespace.repl :refer [refresh]]))

(def channel (async/chan))

(defn fibs
  ([a b]
   (lazy-seq
     (cons a (fibs b (+ a b)))))
  ([] (fibs 1 1))
  )

(def PHI (/ (+ 1 (Math/sqrt 5)) 2))

(defn forward [distance] (async/>!! channel [:forward distance]))
(defn back [distance] (async/>!! channel [:back distance]))
(defn right [angle] (async/>!! channel [:right angle]))
(defn left [angle] (async/>!! channel [:left angle]))
(defn pen-up [] (async/>!! channel [:pen-up]))
(defn pen-down [] (async/>!! channel [:pen-down]))
(defn hide [] (async/>!! channel [:hide]))
(defn show [] (async/>!! channel [:show]))
(defn weight [weight] (async/>!! channel [:weight weight]))
(defn speed [speed] (async/>!! channel [:speed speed]))

(defn dot []
  (weight 5)
  (pen-down)
  (forward 1)
  (pen-up)
  (back 1))

(defn fib-spiral []
  (speed 1000)
  (weight 3)
  (doseq [f (take 20 (fibs))]
    (weight (inc (Math/log (double f))))
    (forward f)
    (right 90)
    (dot))
  )

(defn rect [a b]
  (doseq [_ (range 2)]
    (forward a)
    (right 90)
    (forward b)
    (right 90)))

(defn phi-rect [n]
  (pen-down)
  (weight 3)
  (rect n (* n PHI)))

(defn scaled-rect [a b scale]
  (let [width (* a scale)
        height (* b scale)]
    (rect width height)
    (forward width)
    (right 90))
  )

(defn phi-spiral []
  (left 90)
  (forward 100)
  (right 90)
  (pen-down)
  (weight 2)
  (speed 20)
  (loop [a 1 b PHI]
    (scaled-rect a b 10)
    (if (> a 40)
      nil
      (recur b (* b PHI))
      )
    )
  )

(defn square-the-rect [length height f]
  (if (zero? height)
    (println "Rational.")
    (if (> 1 length)
      (println "Irrational")
      (let [n (quot length height)
            r (rem length height)]
        (println "Squares: " n " " (/ height f))
        (doseq [_ (range n)]
          (forward height)
          (right 90)
          (pen-down)
          (forward height)
          (pen-up)
          (back height)
          (left 90))
        (forward r)
        (right 90)
        (square-the-rect height r f)))))

(defn triangle [triplet]
  (let [[a b c] (sort triplet)
        scale (/ 980 b)
        al (* scale a)
        bl (* scale b)
        cl (* scale c)
        atan (+ 90 (/ (* 180.0 (Math/atan2 b a)) Math/PI))
        ]
    (left 90)
    (pen-up)
    (forward bl)
    (right atan)
    (pen-down)
    (forward cl)
    (pen-up)
    (left atan)
    (right 90)
    (back al)))

(defn scale [n]
  (let [tic (/ 980 n)]
    (dotimes [x n]
      (let [len (if (zero? (mod x 10)) 10 5)]
        (pen-up)
        (right 90)
        (pen-down)
        (forward len)
        (pen-up)
        (back len)
        (left 90)
        (forward tic)))
    (back 980)))


(defn triangles [triplets]
  (speed 1000)
  (pen-up)
  (right 90)
  (forward 490)
  (right 90)
  (forward 490)
  (right 180)
  (scale 100)
  (pen-down)
  (weight 2)
  (forward 980)
  (pen-up)
  (back 980)
  (left 90)
  (pen-down)
  (forward 980)
  (pen-up)
  (back 980)
  (right 90)
  (pen-down)
  (doseq [triplet triplets] (triangle triplet))
  )

(defn square-spiral [a b]
  (let [length (max a b)
        height (min a b)
        f (quot 980 length)
        length (* length f)
        height (* height f)
        _ (prn length height)]
    (speed 20)
    (pen-up)
    (back (quot length 2))
    (left 90)
    (forward (quot height 2))
    (right 90)
    (pen-down)
    (weight 2)
    (rect length height)
    (pen-up)
    (square-the-rect length height f)))

(defn flower-spiral [theta]
  (let [petals 250
        radius-increment 2]
    (speed 1000)
    (doseq [x (range petals)]
      (right theta)
      (forward (* radius-increment x))
      (dot)
      (back (* radius-increment x)))
    (hide)))

(defn polygon [theta, l, n]
  (pen-down)
  (speed 1000)
  (doseq [_ (range n)]
    (forward l)
    (right theta)))

(defn spiral [theta length-f n]
  (pen-down)
  (speed 1000)
  (loop [i 0 len 1]
    (if (= i n)
      nil
      (do
        (forward len)
        (right theta)
        (recur (inc i) (length-f len))))))

(defn gcd
  ([a b]
   (if (zero? b)
     a
     (gcd b (mod a b))))
  ([l]
   (reduce gcd l))
  )

(defn find-triplets-up-to [n]
  (let [nn (range 1 (inc n))
        nsq (set (map #(* % %) nn))
        triplets (for [a nsq b nsq]
                   (when (contains? nsq (+ a b))
                     [(int (Math/sqrt a)) (int (Math/sqrt b)) (int (Math/sqrt (+ a b)))]))
        triplets (filter some? triplets)]
    (filter (fn [[a b c]] (<= a b c)) triplets)))

(defn find-unique-triplets [n]
  (sort-by first (filter #(= 1 (gcd %)) (find-triplets-up-to n))))

(defn turtle-script []
  (triangles (find-unique-triplets 1000))
  )


(defn setup []
  (q/frame-rate 60)
  (q/color-mode :rgb)
  (let [state {:turtle (turtle/make)
               :channel channel}]
    (async/go
      (turtle-script)
      (prn "Turtle script complete"))
    state)
  )

(defn handle-commands [channel turtle]
  (loop [turtle turtle]
    (let [command (if (= :idle (:state turtle))
                    (async/poll! channel)
                    nil)]
      (if (nil? command)
        turtle
        (recur (turtle/handle-command turtle command))))))

(defn update-state [{:keys [channel] :as state}]
  (let [turtle (:turtle state)
        turtle (turtle/update-turtle turtle)]
    (assoc state :turtle (handle-commands channel turtle)))
  )

(defn draw-state [state]
  (q/background 240)
  (q/with-translation
    [500 500]
    (let [{:keys [turtle]} state]
      (turtle/draw turtle)))
  )

(declare turtle-graphics)

(defn ^:export -main [& args]
  (q/defsketch turtle-graphics
               :title "Turtle Graphics"
               :size [1000 1000]
               :setup setup
               :update update-state
               :draw draw-state
               :features [:keep-on-top]
               :middleware [m/fun-mode])
  args)