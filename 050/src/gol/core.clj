(ns gol.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.math.combinatorics :as combo]
            [clojure.java.shell :as shell]
            [clojure.string :as str])
  (:gen-class))

; globals (defaults handled by args)
(def born nil)
(def survive nil)
(def interval nil)

(defn convert-board
  "take board as string and convert to nested vector bools"
  [input]
  (vec
   (map (fn [instring] (vec (into [] (map #(not= \. %) instring))))
        (filter not-empty
                (map str/trim
                     (str/split input #"\n|\r\n")
                     )
                )
        )
   )
  )

(defn tput
  "return tput output as int"
  [arg]
  (Integer/parseInt
   (last
    (re-find (re-pattern (apply str (concat arg " (\\d+)" )))
             (:out (shell/sh "/bin/sh" "-c" "stty -a < /dev/tty"))
             )
    )
   )
  )

(defn size-board
  "resize the board to match terminal."
  [board]
  (let [board-width (count (nth board 0))
        term-width (tput "columns")
        ]

      ; width
    (let [board (if (< board-width term-width)
                    (mapv #(into % (vec (boolean-array (- term-width board-width)))) board)
                  (mapv #(drop-last (- board-width term-width) %) board)
                )
          board-height (count board)
          board-width (count (nth board 0))
          term-height (tput "rows")
          ]

        ; height
        (if (< board-height term-height)
          (into board (for [to-add (range 0 (- term-height board-height))] (vec (boolean-array board-width))))
          (drop-last (- board-height term-height) board)
        )
      )
    )
  )

(defn get-surrounding-cells
  "return the surrounding cells from x y"
  [x y]
  (for [dx [-1 0 1]
        dy [-1 0 1]
        :when (not= 0 dx dy)]
    [
     (+ dx x)
     (+ dy y)
     ]))

(defn get-cell-state
  "get true/false in map - treat off map as false"
  [x y board]
  (nth
   (nth board x [])
   y false)
  )

(defn count-live-neighbors
  "get a count of live neighbors for game of life"
  [x y board]
  (->>
   (get-surrounding-cells x y)
   (map #(apply get-cell-state (conj % board)))
   (filter #(= % true))
   count
   )
  )

(defn get-next-state
  "get the next state"
  [x y board]
  (let [alive (get-cell-state x y board)]
    (some #(= (count-live-neighbors x y board) %)
          (if alive survive born)
          )
    )
  )

(defn render-board [board]
  (println
   (str/join "\n"
     (for [row board]
       ; apply to not get lazy seq signature back as string
       (apply str
        (for [cell row] (if cell "█" " "))
        )))))

(defn get-next-board
  [board]
  (into []
  (for [x (range 0 (count board))]
    (mapv
     #(get-next-state x % board)
     (range 0 (count (nth board 0))))))
  )

(defn step [board]
  (print (str (char 27) "[2J")) ; clear screen
  (print (str (char 27) "[;H")) ; move cursor to the top left corner of the screen
  (render-board board)
  (Thread/sleep interval)
  (get-next-board board)
  )

(defn run [board accum]
  (recur
   (step
    (if (= 0 (mod accum 8))
      (size-board board)
        ;board
      board
      )
    )
   (inc accum)
   )
  )

(def cli-options
  [
   ["-f" "--file FILE" "File containing startmap"
   :default "default.map"]

   ["-i" "--interval INTERVAL" "Sleep interval between generations (in milliseconds)"
    :default 1
    :parse-fn #(Integer/parseInt %)]

   ["-s" "--survive SURVIVE_COUNT" "valid numbers for survival"
    :default "23"]

   ["-b" "--born BORN_COUNT" "valid numbers for cell to be born"
    :default "3"]

   ["-h" "--help"]
   ]
  )

; couldn't get this to work as anon function in tools.cli - worked in repl as such..
(defn numberstring-to-vector
  "turn strings of numbers into vectors of numbers."
  [input]
  (into [] (map #(Integer/parseInt (str %)) input))
  )

(defn print-board-stats
  [board]
  (let [
        board-height (count board)
        board-width (count (nth board 0))
        ]
    (println board-height)
    (println board-width)
    )
  )

;; some good example inputs can be found at the bottom of this page:
;; http://www.cs.nyu.edu/courses/fall13/CSCI-GA.1133-001/HW/game-of-life.html
(defn -main
  [& args]
  (let [arg-result (parse-opts args cli-options)
        options (:options arg-result)
        help-text (:summary arg-result)
        ]

    (if (:help options)
      (println help-text)

      (do
        (def interval (:interval options))
        (def survive (numberstring-to-vector (:survive options)))
        (def born (numberstring-to-vector (:born options)))

        #_(let [board (convert-board (slurp (:file options)))]
          (print-board-stats board)
          (print-board-stats (size-board board))
          )

        (run (size-board (convert-board (slurp (:file options)))) 0)
        )
      )
    )
  )
