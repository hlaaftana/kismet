# Kismet

A programming language. Started in 2016 or 2017.

## Program example

```
let (each i: range 1 100) {
  tu: reduce (0, 5, 3) [fn [a b]
    [bit_or
      [left_shift a 1]
      [if [divs? i b] 1 0]]]
  echo [i, "Fizz", "Buzz", "FizzBuzz"].[tu]


;; alternatively

(let (result l: [], each i: range 1 100)
  (add l (i, "Fizz", "Buzz", "FizzBuzz").[
    (reduce (0, 5, 3) (fn [a b]
      (bit_or
        (left_shift a 1)
        (if (divs? i b) 1 0))))])))
```