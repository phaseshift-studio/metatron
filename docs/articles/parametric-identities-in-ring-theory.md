---
name: Parametric Identities in Ring Theory
description: An identity can be a parametric instruction, one finite term that stands for an infinite unity, such as the diagonal relation, at every type.
author: |
  Dr. Marko A. Rodriguez
  Dr. Stynx
---

# Introduction

The identities of a classical ring are values. The zero $0$ and the unity $1$ are elements of the ring's set, and
$0 + a = a$ and $1 \cdot a = a$ hold by their place in the algebra. Stream Ring Theory
[[1]](./stream-ring-theory.md) made the identities functions. Its function ring $\langle\mathcal{F}, +, \cdot \rangle$
branches with $+$ and composes with $\cdot$. Its zero $0 (x) = \emptyset$ outputs no object, and its unity
$1 (x) = x$ outputs the object it is given. In mtron, the language of [metatron](https://metatron.phaseshift.studio),
the zero is `noobj` and the unity is `id()` [2].

mtron is a code-as-data language: an instruction can stand wherever a value stands, at either end of an arrow
included. A ring of data therefore need not hold its identities as values. It can take them from the function ring,
and this matters when the identity the data ring needs is an infinite value. Relations under composition are such a
ring. Their unity is the diagonal, the relation of every object to itself, and over infinitely many objects no value
holds it. The arrow $1 \Rightarrow 1$, from the unity of the function ring to itself, is one finite term that stands
for the diagonal. The unity is *parametric*, $1 : \forall A.\, A \to A$, so the one arrow is the unity of relations
between objects of every type, and parametricity, not a check at each type, guarantees that it is.

This article defines the relation ring and proves that no value is its unity (Theorem 1), that the arrow
$1 \Rightarrow 1$ is its unity (Theorem 2), and that parametricity makes it the unity at every type (Theorem 3).

**Live proof.** An arrow composes with an arrow that starts where it ends,
$(2 \Rightarrow 3) \cdot (3 \Rightarrow 4) = 2 \Rightarrow 4$. The unity of a relation is the arrow between two
identity instructions, and composing with it leaves the relation as it was:
$(1 \Rightarrow 1) \cdot (2 \Rightarrow 3) = 2 \Rightarrow 3$.

```mtron_pre
(2=>3) * (3=>4)
(2=>3).one()
(2=>3).one() * (2=>3)
```

# The Relation Ring

The objects, coefficients and functions are those of [1]: $x, y, z \in X$ are objects, $c, d \in \mathcal{C}$ are
coefficients of a ring with unity, and $a, b \in \mathcal{F}$ are stream functions.

::: **Definition 1** (Relation). If $x, y \in X$, then the *arrow* $x \Rightarrow y$ relates $x$ to $y$. Arrows
multiply by composition:
$$
\begin{equation*} (x \Rightarrow y) \cdot (y' \Rightarrow z) =
\begin{cases}
x \Rightarrow z & \text{if $y = y'$}, \\
0 & \text{otherwise}.
\end{cases}
\end{equation*}
$$
A *relation* $r \in \mathcal{R}$ is a finite sum of arrows with
coefficients, $r = \sum_{i=1}^{n} c_i (x_i \Rightarrow y_i)$,
and the empty sum is $0$. Relations multiply arrow by arrow, the coefficients multiplying as in the stream ring [1],
$c (x \Rightarrow y) \cdot d (y' \Rightarrow z) = (c \cdot d) ((x \Rightarrow y) \cdot (y' \Rightarrow z))$, so that
$\cdot$ distributes over $+$.
:::

With coefficients in $\mathcal{C}$, $\langle\mathcal{R}, +, \cdot \rangle$ is the ring of finitely supported matrices
over $\mathcal{C}$ whose rows and columns are indexed by $X$. The arrow $x \Rightarrow y$ is the matrix with a $1$ at
row $x$ and column $y$ and $0$ elsewhere.

**Live proof.** Arrows that meet compose,
$(2 \Rightarrow 3) \cdot (3 \Rightarrow 4) \cdot (4 \Rightarrow 5) = 2 \Rightarrow 5$, and arrows that do not meet
compose to the zero, $(2 \Rightarrow 3) \cdot (4 \Rightarrow 5) = 0$, which outputs nothing and counts nothing. A sum
multiplies arrow by arrow:
$((2 \Rightarrow 3) + (4 \Rightarrow 5)) \cdot (3 \Rightarrow 6) = (2 \Rightarrow 6) + 0 = 2 \Rightarrow 6$.

```mtron_pre
(2=>3) * (3=>4) * (4=>5)
(2=>3) * (4=>5)
(2=>3).mult((4=>5)).count()
(2=>3).plus((4=>5)) * (3=>6)
```

In mtron, an arrow is a rel, `x=>y`, and a sum of rels is a stream of them. `mult` composes and `plus` sums, written
infix as ` * ` and ` + ` with a space on each side, as a `*` with no space is a dereference. A rel argument takes
parentheses of its own, `mult((4=>5))`.

::: **Theorem 1** (No value is the unity). If $X$ is infinite, then no relation $e \in \mathcal{R}$ satisfies
$r \cdot e = r$ for every $r \in \mathcal{R}$.
:::

::: *Proof.* If $e = \sum_{i=1}^{n} c_i (x_i \Rightarrow y_i)$, then $e$ names finitely many objects. As $X$ is
infinite, there is an object $u \in X$ with $u \neq x_i$ for every $i$. Then
$$
\begin{equation*}
\begin{array}{lclll} (u \Rightarrow u) \cdot e &=& \sum_{i=1}^{n} (u \Rightarrow u) \cdot c_i (x_i \Rightarrow y_i) && [\text{$\cdot$ is left distributive}] \\
&=& \sum_{i=1}^{n} c_i ((u \Rightarrow u) \cdot (x_i \Rightarrow y_i)) && [\text{Definition 1}] \\
&=& \sum_{i=1}^{n} c_i\, 0 && [\text{$u \neq x_i$, Definition 1}] \\
&=& 0, &&
\end{array}
\end{equation*}
$$
and $0 \neq u \Rightarrow u$. ◻
:::

The unity that composition needs is the *diagonal*, the sum of the arrows from every object to itself:
$$
\begin{equation*}
\Delta_X = \sum_{x \in X} (x \Rightarrow x).
\end{equation*}
$$
It is the identity matrix. In $(x \Rightarrow y) \cdot \Delta_X$, the one term that survives is $y \Rightarrow y$, and
in $\Delta_X \cdot (x \Rightarrow y)$, it is $x \Rightarrow x$, so both products are $x \Rightarrow y$. Over an
infinite $X$, $\Delta_X$ is an infinite sum, which is why Theorem 1 finds no relation that is a unity: $\mathcal{R}$
needs a unity that no value can hold.

# Parametric Identities

::: **Definition 2** (Parametric identities). The *parametric unity* of $\mathcal{R}$ is the arrow $1 \Rightarrow 1$
with the unity $1 \in \mathcal{F}$ at both ends, and the *parametric zero* is the arrow $0 \Rightarrow 0$ with the zero
$0 \in \mathcal{F}$ at both ends. Each stands for the arrows between the outputs of its function on every object
$x \in X$. As $1 (x) = x$,
$$
\begin{equation*}
1 \Rightarrow 1 = \sum_{x \in X} (1 (x) \Rightarrow 1 (x)) = \sum_{x \in X} (x \Rightarrow x) = \Delta_X.
\end{equation*}
$$
As $0 (x) = \emptyset$ outputs no object, $0 \Rightarrow 0$ stands for no arrow and is the empty sum $0$.
:::

The zero is a finite value either way. It is the unity that needs a finite name, and the parametric unity is the
diagonal written as one arrow. $\Delta_X$ is the graph of the function $1$, and the function is finite where its
graph is not. $\Delta_X$ is not a relation of $\mathcal{R}$, but it multiplies as one, arrow by arrow, and against any
one arrow all but one of its terms are $0$.

::: **Theorem 2** (The parametric unity is the unity of $\mathcal{R}$). If $r \in \mathcal{R}$, then
$r \cdot (1 \Rightarrow 1) = (1 \Rightarrow 1) \cdot r = r$.
:::

::: *Proof.* As $\cdot$ distributes over $+$, it suffices to prove the equalities for an arrow $x \Rightarrow y$. Then
$$
\begin{equation*}
\begin{array}{lclll} (x \Rightarrow y) \cdot (1 \Rightarrow 1) &=& \sum_{z \in X} (x \Rightarrow y) \cdot (1 (z) \Rightarrow 1 (z)) && [\text{Definition 2, $\cdot$ distributes}] \\
&=& (x \Rightarrow y) \cdot (1 (y) \Rightarrow 1 (y)) && [\text{$1 (z) = y$ only at $z = y$, Definition 1}] \\
&=& (x \Rightarrow y) \cdot (y \Rightarrow y) && [1 (y) = y] \\
&=& x \Rightarrow y && [\text{Definition 1}].
\end{array}
\end{equation*}
$$
On the left, the sum collapses at $x$, where the arrow starts:
$(1 \Rightarrow 1) \cdot (x \Rightarrow y) = (1 (x) \Rightarrow 1 (x)) \cdot (x \Rightarrow y) = x \Rightarrow y$. ◻
:::

In the second line, the sum over all of $X$ collapses to its one term at $y$, the object where the arrow ends. The
product evaluates the unity at $y$ and nowhere else, so the diagonal is never enumerated. $\mathcal{R}$ has no unity
among its values (Theorem 1), and the function ring lends it one.

In mtron, `one()` asked of a rel is `id()=>id()`, printed `id?A<=A()=>id?A<=A()`: the identity instruction, whose
domain and range are the generic `A`, at both ends. `mult` recognizes the identity arrow by its instruction and
returns the other operand, which is the collapse in the proof of Theorem 2. `zero()` is `noobj=>noobj`, which mtron
reads as noobj: it prints nothing, drops out of a sum, and counts as nothing.

**Live proof.** The unity on the right, $(2 \Rightarrow 3) \cdot (1 \Rightarrow 1) = 2 \Rightarrow 3$, and in the
middle of a chain, where it composes away:
$(2 \Rightarrow 3) \cdot (1 \Rightarrow 1) \cdot (3 \Rightarrow 4) = 2 \Rightarrow 4$. The zero counts nothing, adds
nothing, $(2 \Rightarrow 3) + 0 = 2 \Rightarrow 3$, and annihilates, $(2 \Rightarrow 3) \cdot 0 = 0$.

```mtron_pre
(2=>3).mult((2=>3).one())
(2=>3) * (id()=>id()) * (3=>4)
(2=>3).zero().count()
(2=>3) + (noobj=>noobj)
(2=>3).mult((noobj=>noobj)).count()
```

# Parametricity

The diagonal is large in two ways: a type may have infinitely many objects, and there are infinitely many types. The
parametric unity compresses it twice. The first compression is the function. The diagonal of a type $A$ is the graph
of the identity on $A$, $\lambda x{:}A.\, x$, one finite rule for every arrow of the diagonal. The second compression
is polymorphism. An *ad hoc* unity is a family of functions, one per type, $\{1_A\}_A$, as many as there are types
[3]. A *parametric* unity is one term of System F [4, 5]:
$$
\begin{equation*}
1 = \Lambda A.\, \lambda x{:}A.\, x \quad : \quad \forall A.\, A \to A.
\end{equation*}
$$
The quantifier $\forall A$ ranges over every type without enumerating them. As a map, one object in and one object
out, the unity of [1] has this type, so the one arrow $1 \Rightarrow 1$ is the unity of relations between objects of
every type.

**Live proof.** One instruction is the unity of the function ring on objects of every type,
$\langle x \rangle 1 = \langle x \rangle$ for an int, a str and a lst $x$. One arrow is the unity of relations between
objects of every type, $(x \Rightarrow y) \cdot (1 \Rightarrow 1) = x \Rightarrow y$ for a str $x$ and a bool $y$, and
for a lst $x$ and a real $y$.

```mtron_pre
5.id()
'a'.id()
[2,'a'].id()
('a'=>true) * (id()=>id())
([2,3]=>4.5) * (id()=>id())
```

One term serves every type because a parametric function cannot inspect the type it is instantiated at. A total
function of type $\forall A.\, A \to A$ can do nothing with its object but return it [6]. Wadler's free theorems make
the argument short [7].

::: **Theorem 3** (The type entails the unity). If $f : \forall A.\, A \to A$ is total and parametric, then
$f_A (x) = x$ for every type $A$ and every object $x$ of type $A$.
:::

::: *Proof.* The free theorem of the type $\forall A.\, A \to A$ is $f_C \circ g = g \circ f_B$ for all types $B$ and
$C$ and every function $g : B \to C$ [7]. Let $\{\star\}$ be a type with one object. As $f_{\{\star\}}$ is total and
$\star$ is the only object of its type, $f_{\{\star\}} (\star) = \star$. If $g : \{\star\} \to A$ is $g (\star) = x$,
then
$$
\begin{equation*}
\begin{array}{lclll}
f_A (x) &=& f_A (g (\star)) && [g (\star) = x] \\
&=& g (f_{\{\star\}} (\star)) && [\text{free theorem}] \\
&=& g (\star) && [f_{\{\star\}} (\star) = \star] \\
&=& x && [g (\star) = x].
\end{array}
\end{equation*}
$$
◻
:::

So the unit laws of $1 \Rightarrow 1$ are not checked type by type: the type $\forall A.\, A \to A$ admits no other
total parametric term. This is a stronger uniqueness than the classical one. The classical argument,
$e = e \cdot e' = e'$, shows that a ring has one unity. Theorem 3 shows that a parametric unity has one term. The free
theorem is the naturality of $f$ as a transformation of the identity functor on types, so the identities
$\{1_A\}_A$, one per type, form one natural transformation, and Theorem 3 says it is the only one.

In mtron, an instruction can test the type of its object at run time, so a domain and range of `A` do not make an
instruction parametric. `id()` is parametric because its body never inspects its object. Theorem 3 holds of the term.
Only in a calculus without type inspection, such as System F, does it hold of the type alone.

Neither parametric identity depends on the relation it is asked of. `one()` answers the same arrow for a relation
between strs as for a relation between ints, and so does `zero()`. metatron's `AbstractAlgebraTest` pins this
independence from the element as the parametric law of every carrier with a sum and a product.

**Live proof.** The unity asked of a relation between strs is the unity of a relation between ints,
$(1 \Rightarrow 1) \cdot (2 \Rightarrow 3) = 2 \Rightarrow 3$, and the zero asked of it counts nothing.

```mtron_pre
('a'=>'b').one()
('a'=>'b').one() * (2=>3)
('a'=>'b').zero().count()
```

# Conclusion

A ring of data need not hold its identities as values. When the unity a ring needs is an infinite value, such as the
diagonal relation, the unity of the function ring stands for it. The arrow $1 \Rightarrow 1$ is the diagonal written as
one finite term, and composition evaluates it at one object per arrow, where it is needed (Theorem 2). The unity is
parametric, so the one term is the unity at every type, and parametricity, not a check at each type, guarantees it
(Theorem 3). The same compression applies wherever an identity is a family indexed by infinitely many objects or
types: the identity rewrite of a term rewriting system, the always-true filter of each type, and the identity morphism
of each object of a category.

**NOTES**:

- mtron composes only the identity arrow by evaluation. Any other arrow between instructions composes by `test`, as
  an arrow between values does. Read as Definition 2 reads $1 \Rightarrow 1$, the arrow $1 \Rightarrow b$ is the graph
  of $b$, and the collapse in the proof of Theorem 2
  gives $(x \Rightarrow y) \cdot (1 \Rightarrow b) = x \Rightarrow b (y)$:
  composing with a graph evaluates its function at the one object the arrow meets. Should a rel between instructions
  compose as its graph?
- As a stream function of type $\forall A.\, A \to A^*$, its free theorem, with the one-object type of Theorem 3,
  gives $f_A (x) = \langle x, \ldots, x \rangle$, with the same number $n$ of copies at every type. So the parametric
  stream
  functions are the points $n1$ of the unity [2]. The zero is $n = 0$, so both identities of the function ring are
  points of its unity.

# References

1. Rodriguez, M.A., "[Stream Ring Theory](./stream-ring-theory.md)," S/V Red Herring's Ship's Log: Chronicles in the Sea
   of Cortez, pages 10–40, Mulegé, Baja California Sur, México, February 2019.
2. Rodriguez, M.A., Dr. Stynx,
   "[A Span and Point Coefficient Algebra for Stream Rings](./span-and-point-coefficient-algebra.md)."
3. Strachey, C., "Fundamental Concepts in Programming Languages," Higher-Order and Symbolic Computation, 13, pages
   11–49, 2000. (Lecture notes, 1967.)
4. Girard, J.-Y., "Interprétation fonctionnelle et élimination des coupures de l'arithmétique d'ordre supérieur,"
   Ph.D. thesis, Université Paris VII, 1972.
5. Reynolds, J.C., "Towards a Theory of Type Structure," Colloque sur la Programmation, Lecture Notes in Computer
   Science 19, pages 408–425, Springer, 1974.
6. Reynolds, J.C., "Types, Abstraction and Parametric Polymorphism," Information Processing 83, pages 513–523,
   North-Holland, 1983.
7. Wadler, P., "Theorems for Free!," Proceedings of the Fourth International Conference on Functional Programming
   Languages and Computer Architecture (FPCA '89), pages 347–359, 1989.