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
}

;; alternatively

(let (result l: [], each i: range 1 100)
  (add l (i, "Fizz", "Buzz", "FizzBuzz").[
    (reduce (0, 5, 3) (fn (a, b)
      (bit_or
        (left_shift a 1)
        (if (divs? i b) 1 0))))])))
```

## Quirks

Not interesting features, just things to know to understand the language.

* Syntax: Random, mostly hacked together.
  - `[]` expressions with no comma are always calls, `()` expressions without
    commas can be single expressions, calls or blocks separated by `;`,
    `{}` without commas is an "open block" meaning newlines separate expressions
    which is the same at top level.
  - `()`, `[]` and `{}` expressions with commas respectively form tuple, list
    and set expressions, and if every expression in `{}` is a colon expression or
    it starts with `{:`, it becomes a map expression.
  - Subscripts, properties with `.` and `a(b, c)` call syntax are supported,
    `(` and `[` can be preceded by `.`. Colons such as `a: b` are the infix
    assignment syntax though you can still write `= a b`.
  - String escape scheme is different for some reason. `\uXXXX` and octal
    `\XXX` replaced with `\u(x|X|o|O|b|B)?{XXXX}` where x, X and no character
    imply hex, o and O imply octal, b and B imply binary, presumably for larger
    unicode characters. Also raw string calls like in Python which I just added.
  - Backticks are quoted identifiers
* Type system, very broken:
  - You can overload variables with any types.
    ```
    a: 3
    a: true
    incr a
    assert_is (prefer_type a Boolean, prefer_type a Int) (true, 4)
    ```
    This is how function overloading works.
  - Call expressions are typed by checking the call value for the lowest supertype
    of the following types in order (where the preferred type is for the value of
    the call expression):
    - `Template` (function that takes parser object and expressions and outputs expression)
    - `TypeChecker` (function that takes scope context and expressions and outputs typed expression)
    
    ^ ideally the above would be overloadable with some kind of `Untyped` type
    to represent untyped expressions 
    
    - `TypedTemplate[Tuple[type of arg1, type of arg2, ...], preferred type]`
      (function that takes scope context and typed expressions and outputs typed expression)
    - `Instructor[Tuple[type of arg1, type of arg2, ...], preferred type]`
      (function that takes instructions which are just processed AST and outputs value at runtime)
    - `Function[Tuple[type of arg1, type of arg2, ...], preferred type]`
    
    If functions with a supertype of the above type are found but functions with subtypes
    exist, those functions will be checked at runtime for the given arguments in a certain order.
    Otherwise a function named `call` with type
    `Function[Tuple[type of call value, Tuple[type of arg1, type of arg2, ...]], preferred type]`
    will be searched, which if not found will fail the type check for the call expression.
  - Tuple types can have varargs, ideally represented by a list at runtime but
    I don't think that's how it works currently.
  - `null` has type `None` which is a subtype of all types.
* `let` template, as seen above, allows `each` and `result` assignments. Funnily enough
  I thought of aliasing `let` to `for` a long while after thinking of `each`.