---
name: A Span and Point Coefficient Algebra for Stream Rings
description: An algebra concerning functions and objects where a point has magnitude and a span has a linear extension in space.
author: |
  Dr. Marko A. Rodriguez
  Dr. Stynx
---

**DRAFT**: This article is currently being developed. As of November 10, 2026, the algebra has been fully specified, but
the entailments of the presented theory have yet to be sufficiently explored.

# Introduction

Stream Ring Theory [[1]](./stream-ring-theory.md) gave every function a
coefficient. A term $ca$ of the stream ring
$\langle\mathcal{C}\mathcal{F}, +, \cdot \rangle$ pairs a coefficient
$c \in \mathcal{C}$ with a function $a \in \mathcal{F}$, and the coefficient
floats: $ca = a \cdot c$. This is called the *point* coefficient and it denotes the magnitude of the function. Stream
ring theory finds practical application in its realization in the mtron programming language (the language
of [metatron](http://metatron/phaseshift.studio)). In mtron the *point* coefficient is written after its corresponding
instruction, `plus{3}(1)` and denotes three parallel evaluations of the `+1`.

The position in front of the instruction holds a second coefficient, the *span*: `{3}plus(1)` is semantically equivalent
to `plus(1).plus(1).plus(1)`, three steps laid one after
another. The two coefficients form a compression of the two ring operations of stream ring theory: branch ($+$) and
compose ($\cdot$). For a natural number $n \in \mathbb{N}$, the point counts additions, $na = a + \cdots + a$, and the
span counts multiplications, $a^n = a \cdot \cdots \cdot a$. A point can be understood as the _mass_ at a single
memory address. The span is the _extent_ along a sequential series of addresses.

Stream ring theory holds the bridge between them: the third equality of
Theorem 6: $(ca)^n = c^n a^n$. In mtron, `{n}a{c}` is $(ca)^n = c^n a^n$:
the span exponentiates the point.

**Live proof.** Two steps of `plus{3}(34)` from `1`: the value climbs to `69`,
and the point squares to `9`. Thus, $(3a)^2$ and

```mtron
mtron> 1..{2}plus{3}(34)
==>{9}69
```

In _branch normal form_, the point expanded, $3a = a + a + a$, so $(3a)^2 = (a + a + a)^2$:

```mtron
mtron> 1..{2}{(plus(34),plus(34),plus(34)}
==>{9}69
```

In _compose normal form_, the span expanded, $(3a)^2 = 3a \cdot 3a$:

```mtron
mtron> 1..plus{3}(34).plus{3}(34)
==>{9}69
```

Finally, in both compose and branch normal form, the sum of the $3^2$ words $a \cdot a$:

```mtron
mtron> 1..{plus(34).plus(34),
           plus(34).plus(34),
           plus(34).plus(34),
           plus(34).plus(34),
           plus(34).plus(34),
           plus(34).plus(34),
           plus(34).plus(34),
           plus(34).plus(34),
           plus(34).plus(34)}
==>{9}69
```

The purpose of this article is to prove that the laws of each coefficient alone and of the two together,
can be derived solely from the stream ring axioms of [1] as well as to demonstrate practical, utilitarian
consequences of the interplay of these two coefficients with respects to data compression,
process compression, co-located vs. distributed data structures, implications for compiler rewrite engines, and their
role in mechanical induction.

## Two Coefficients

The objects, coefficients and functions are those of [1]: $x, y, z \in X$ are
objects, $c, d, e \in \mathcal{C}$ are coefficients of a ring with unity, and
$a, b, f \in \mathcal{F}$ are stream functions. The function ring has the zero
$0 (x) = \emptyset$ and the unity $1 (x) = x$, and a stream is written
$\langle x, y, z \rangle$, with $\langle x \rangle a$ the stream $a$ outputs on
$x$. The proofs below cite the stream ring axioms of [1] by name: the *apply*, *split*, *merge* and *bulk*
axioms, $\langle 0x \rangle = \langle\rangle$, and
the stream multiplication $ca \cdot db = (c \cdot d)(a \cdot b)$.

::: definition **Definition 1** (Point). The *point* of a term
$ca \in \mathcal{C}\mathcal{F}$ is its coefficient $c \in \mathcal{C}$. In mtron
it is written after the instruction, `a{c}`. If $n \in \mathbb{N}$, then by the
stream ring's addition
$$
\begin{equation*}
na = \underbrace{1a + \cdots + 1a}_{n}.
\end{equation*}
$$
:::

::: definition **Definition 2** (Span). The *span* of a term is the exponent
$n \in \mathbb{N}$ of its run, where
$$
\begin{equation*}
a^0 = 1 \quad \text{and} \quad a^{n+1} = a^n \cdot a.
\end{equation*}
$$
In mtron it is written before the instruction, `{n}a`, and the parser lays the
run out: `{3}a` is `a.a.a`, and `{0}a` is no instruction at all. A term with
both coefficients, `{n}a{c}`, is $(ca)^n$.
:::

An mtron chain is read as a stream ring expression:

| mtron         | stream ring           |
|---------------|-----------------------|
| `x.a`         | $\langle x \rangle a$ |
| `a.b`         | $a \cdot b$           |
| `branch(a,b)` | $a + b$               |
| `id()`        | $1$                   |
| `noobj`       | $0$                   |
| `{c}x`        | $cx$                  |
| `a{c}`        | $ca$                  |
| `{n}a`        | $a^n$                 |
| `{n}a{c}`     | $(ca)^n$              |

Where a coefficient is written depends on what it is written on:

| written on     | prefix                           | postfix                               |
|----------------|----------------------------------|---------------------------------------|
| an instruction | span: `{n}a` is $a^n$            | point: `a{c}` is $ca$                 |
| a type         | span: `{n}T` is $n$ slots of `T` | point: `T{c}` is $c$ copies of `T`    |
| a value        | point: `{c}x` is $cx$            | span: `x{n}` lays `x` along $n$ slots |

The point coefficient can be understood as the _mass_ at an address, copies compressed into a single point in space.
The span coefficient can be understood as the _extent_ along a contiguous sequence of points in space. In this way, $+$
is the 0-dimensional operation and $\cdot$ the 1-dimensional operation, which leads to the realization of the point
iterating $+$ and the
span iterating $\cdot$. In mtron, extension of instructions is function composition `(a.b.c)`, where the locality of
composition is in a uri address space. For example, `plus(1).plus(2)` compiles to `plus(1)@<0>.plus(2)@<1>`, where
`<0>` and `<1>` are _relative uri addresses_ in the virtual machine's _variable stack_. It's important to realize
that composition is not application. A span lays one instruction
along the axis of sequential addresses such that: `{3}plus(1)` is `plus(1)@<0>.plus(1)@<1>.plus(1)@<2>`.

**NOTES**:

- The mirror: instructions and types write `{span}x{point}`, values write
  `{point}value{span}`. `{3}2` is three 2s at one address; `2{3}` is 2 laid at
  the uris 0, 1 and 2. Why does the value side flip? (My guess, not settled:
  diagrammatic order, a value being where a chain starts.)
- Caution: the stream notation $\langle x \rangle$ above is a stream, so the
  positions `<0>`, `<1>` stay in code font here, never in math.]

### The Point Coefficient

::: theorem **Theorem 1** (Points add across a branch). If $a \in \mathcal{F}$
and $c,d \in \mathcal{C}$, then $ca + da = (c+d)a$.
:::

::: proof *Proof.* If $a (x) = y$, then
$$
\begin{equation*}
\begin{array}{lclll}
ca + da &=& (c+d)a && \\
\langle x \rangle (ca + da) &=& \langle x \rangle (c+d)a && [\text{apply $1x$}] \\ (\langle x \rangle ca) + (\langle x \rangle da) &=& \langle (c+d)y \rangle && [\text{split, apply axioms}] \\
\langle cy \rangle + \langle dy \rangle &=& \langle (c+d)y \rangle && [\text{apply axiom}] \\
\langle cy, dy \rangle &=& \langle (c+d)y \rangle && [\text{merge axiom}] \\
\langle (c+d)y \rangle &=& \langle (c+d)y \rangle && [\text{bulk axiom}].
\end{array}
\end{equation*}
$$
◻
:::

::: theorem **Theorem 2** (Points multiply along a chain). If
$a,b \in \mathcal{F}$ and $c,d \in \mathcal{C}$, then
$ca \cdot db = (c \cdot d)(a \cdot b)$.
:::

::: proof *Proof.* If $a (x) = y$ and $b (y) = z$, then
$$
\begin{equation*}
\begin{array}{lclll}
ca \cdot db &=& (c \cdot d)ab && \\
\langle x \rangle ca \cdot db &=& \langle x \rangle (c \cdot d)ab && [\text{apply $1x$}] \\
\langle cy \rangle db &=& \langle (c \cdot d)z \rangle && [\text{apply axiom}] \\
\langle (c \cdot d)z \rangle &=& \langle (c \cdot d)z \rangle && [\text{apply axiom}].
\end{array}
\end{equation*}
$$
◻
:::

::: theorem **Theorem 3** (Points are central). If
$a,b \in \mathcal{F}\setminus \mathcal{F}_r$ and $c \in \mathcal{C}$, then
$ca \cdot b = a \cdot cb = (ab)c$.
:::

::: proof *Proof.*
$$
\begin{equation*}
\begin{array}{lclll}
ca \cdot b &=& ca \cdot 1b && [b = 1b] \\
&=& (c \cdot 1)(a \cdot b) && [\text{stream mult}] \\
&=& (1 \cdot c)(a \cdot b) && [\text{$1$ is unity in $\mathcal{C}$}] \\
&=& 1a \cdot cb && [\text{stream mult}] \\
&=& a \cdot cb && [a = 1a].
\end{array}
\end{equation*}
$$
The coefficient of $c(ab)$ floats to its end, $c(ab) = (ab)c$, by Theorem 6 of
[1]. A reducer $r \in \mathcal{F}_r$ is the exception: a coefficient does not
float past it (Theorem 17 of [1]). ◻
:::

**Live proof.**

```mtron
mtron> 5.branch(id{2}(),id{3}())
==>{5}5
mtron> 5.id{2}().id{3}()
==>{6}5
mtron> 5.id{3}().id{2}()
==>{6}5
mtron> 5.id{3}().plus(1)
==>{3}6
mtron> 5.plus{3}(1)
==>{3}6
mtron> 5.plus(1).id{3}()
==>{3}6
```

::: theorem **Theorem 4** (A zero point annihilates). If $a \in \mathcal{F}$,
then $0a = 0$.
:::

::: proof *Proof.* The zero of $\mathcal{C}\mathcal{F}$ is
$(0,0) \in \mathcal{C}\times \mathcal{F}$ [1]. If $a (x) = y$, then
$$
\begin{equation*}
\begin{array}{lclll}
0a &=& 0 && \\
\langle x \rangle 0a &=& \langle x \rangle 0 && [\text{apply $1x$}] \\
\langle (1 \cdot 0)y \rangle &=& \langle 0\emptyset \rangle && [\text{apply axiom}] \\
\langle\rangle &=& \langle\rangle && [\langle 0x \rangle = \langle 0\emptyset \rangle = \langle\rangle].
\end{array}
\end{equation*}
$$
◻
:::

So a zero point ends the chain it is written in: $0a \cdot b = 0 \cdot b = 0$,
since no object reaches $b$. A reducer $r \in \mathcal{F}_r$ still runs, on the
empty stream, by the reduce apply axiom of [1]:
$\langle x \rangle 0a \cdot r = \langle\rangle r = \langle r (\langle\rangle) \rangle$.
For `count()`, $r (\langle\rangle) = 0$.

**Live proof.** A zero point leaves nothing for the rest of the chain. A branch
adds it as it adds any point, and a reducer after it counts nothing.

```mtron
mtron> 5.id{0}()
mtron> 5.plus{0}(1).plus(1)
mtron> 5.branch(id{0}(),id())
==>5
mtron> 5.id{0}().count()
==>0
```

### The Span Coefficient

::: theorem **Theorem 5** (Spans add along a chain). If $a \in \mathcal{F}$ and
$m,n \in \mathbb{N}$, then $a^m \cdot a^n = a^{m+n}$.
:::

::: proof *Proof.* By induction on $n$.
$$
\begin{equation*}
\begin{array}{lclll}
a^m \cdot a^0 &=& a^m \cdot 1 = a^m && [\text{Definition 2}] \\
a^m \cdot a^{n+1} &=& a^m \cdot (a^n \cdot a) && [\text{Definition 2}] \\
&=& (a^m \cdot a^n) \cdot a && [\text{$\cdot$ is associative}] \\
&=& a^{m+n} \cdot a && [\text{induction}] \\
&=& a^{m+n+1} && [\text{Definition 2}].
\end{array}
\end{equation*}
$$
◻
:::

::: theorem **Theorem 6** (Spans stay where they are written). In general,
$a^n \cdot b \neq a \cdot b^n$.
:::

::: proof *Proof.* A counterexample. If $a (x) = x + 1$ and $b (x) = 2x$, then
$$
\begin{equation*}
\langle 5 \rangle a^2 b = \langle 14 \rangle \neq \langle 24 \rangle = \langle 5 \rangle a b^2.
\end{equation*}
$$
A point floats (Theorem 3), but a span is an exponent on its own function, and
the monoid $\langle\mathcal{F}, \cdot \rangle$ is not commutative. ◻
:::

**Live proof.**

```mtron
mtron> 5.{2}plus(1).{3}plus(1)
==>10
mtron> 5.{0}plus(1)
==>5
mtron> 5.{2}plus(1).mult(2)
==>14
mtron> 5.plus(1).{2}mult(2)
==>24
```

::: corollary **Corollary 1** (Inductive generation). If $a (x) = x + 1$, then
$\langle 0 \rangle a^n = \langle n \rangle$.
:::

::: proof *Proof.* By induction on $n$:
$\langle 0 \rangle a^0 = \langle 0 \rangle 1 = \langle 0 \rangle$, and
$\langle 0 \rangle a^{n+1} = \langle 0 \rangle a^n \cdot a = \langle n \rangle a = \langle n + 1 \rangle$. ◻
:::

```mtron
mtron> 0.{5}plus(1)
==>5
```

::: corollary **Corollary 2** (A constant's run is the constant). If
$k (x) = v$ for every object $x$, then $k \cdot k = k$, so $k^n = k$ for every
$n \geq 1$, and $k^0 = 1$.
:::

::: proof *Proof.*
$$
\begin{equation*}
\begin{array}{lclll}
\langle x \rangle k \cdot k &=& \langle v \rangle k && [k (x) = v] \\
&=& \langle v \rangle && [k (v) = v] \\
&=& \langle x \rangle k && [k (x) = v].
\end{array}
\end{equation*}
$$
Then $k^{n+1} = k^n \cdot k = k \cdot k = k$ by induction, and $k^0 = 1$ by
Definition 2. ◻
:::

**Live proof.** A dereference reads its address, never its input, so `*a` is
such a $k$. Read left to right and greedy, `{2}*a` is `{2}from(a)`, the run
`from(a).from(a)`.

```mtron
mtron> a -> 7
==>7
mtron> {2}*a
==>7
mtron> *a.*a
==>7
```

A span on an object lays it out in space: $n$ copies of $x$ at the addresses
$0, 1, \ldots, n-1$, each copy keeping the point of $x$. In mtron a value
writes its span after it, `x{n}`, and until an address can be a branched uri
the layout is the list of the copies, whose slots are those addresses:
`2{3}` is `[2,2,2]`.

```mtron
mtron> 2{3}
==>[2,2,2]
mtron> {2}2{3}
==>[{2}2,{2}2,{2}2]
```

NOTES: spans on a value. Once branched uris read and write a layout, is
`2{3}` the one value `2@<{0,1,2}>` rather than the lst `[2,2,2]`? And is a laid
value one obj or $n$? A ranged value span, `2{?}`, has no layout yet and does
not parse. Would `2{2,3}` be the sum of its layouts, as a span range on an
instruction is?

## The Algebra Relating Point and Span Coefficients

::: theorem **Theorem 7** (The span exponentiates the point). If
$a \in \mathcal{F}$, $c \in \mathcal{C}$ and $n \in \mathbb{N}$, then
$(ca)^n = c^n a^n$.
:::

::: proof *Proof.* This is the third equality of Theorem 6 of [1]. By induction
on $n$:
$$
\begin{equation*}
\begin{array}{lclll} (ca)^0 &=& 1 = (1,1) = c^0 a^0 && [\text{Definition 2}] \\ (ca)^{n+1} &=& (ca)^n \cdot ca && [\text{Definition 2}] \\
&=& c^n a^n \cdot ca && [\text{induction}] \\
&=& (c^n \cdot c)(a^n \cdot a) && [\text{stream mult}] \\
&=& c^{n+1} a^{n+1} && [\text{Definition 2}].
\end{array}
\end{equation*}
$$
◻
:::

::: corollary **Corollary 3** (Order matters). A point on a run is not a run of
the point: $a^n \cdot c1 = c\,a^n$, while $(ca)^n = c^n a^n$.
:::

::: proof *Proof.* $a^n \cdot c1 = (1 \cdot c)(a^n \cdot 1) = c\,a^n$ by stream
multiplication, and $(ca)^n = c^n a^n$ by Theorem 7. The two agree only where
$c^n = c$. ◻
:::

::: corollary **Corollary 4** (Idempotents collapse). If $e \cdot e = e$, then
$(ce)^n = c^n e$ for every $n \geq 1$.
:::

::: proof *Proof.* $e^n = e$ for $n \geq 1$, by the induction of Corollary 2, so
$(ce)^n = c^n e^n = c^n e$ by Theorem 7. The unity is idempotent, so
$(c1)^n = c^n 1$. ◻
:::

::: corollary **Corollary 5** ($0^0 = 1$). $(0a)^0 = 1$: a zero run of the
annihilator is no step at all.
:::

::: proof *Proof.* $t^0 = 1$ for every term $t$ by Definition 2, and $0a = 0$ is
a term (Theorem 4). ◻
:::

**Live proof.**

```mtron
mtron> 5.{2}id{3}()
==>{9}5
mtron> 5.{2}id().id{3}()
==>{3}5
mtron> 5.{3}id{2}()
==>{8}5
mtron> 5.{0}id{0}()
==>5
```

::: theorem **Theorem 8** (A span does not distribute over a branch). If
$a,b \in \mathcal{F}$, then
$$
\begin{equation*} (a + b)^n = \sum_{w \in \{a,b\}^n} w,
\end{equation*}
$$
the sum of all $2^n$ words of length $n$ over $a$ and $b$. If $ab = ba$, then
$$
\begin{equation*} (a + b)^n = \sum_{k=0}^{n} \binom{n}{k} a^k b^{n-k},
\end{equation*}
$$
and each binomial coefficient is a point. In general,
$(a + b)^n \neq a^n + b^n$.
:::

::: proof *Proof.* By induction on $n$, expanding as [1] expands a multinomial:
$$
\begin{equation*}
\begin{array}{lclll} (a + b)^{n+1} &=& (a + b)^n \cdot (a + b) && [\text{Definition 2}] \\
&=& \sum_{w \in \{a,b\}^n} w \cdot (a + b) && [\text{induction}] \\
&=& \sum_{w \in \{a,b\}^n} (wa + wb) && [\text{$\cdot$ is left distributive}] \\
&=& \sum_{w \in \{a,b\}^{n+1}} w && [\text{$+$ is associative}].
\end{array}
\end{equation*}
$$
If $ab = ba$, then a word with $k$ copies of $a$ is $a^k b^{n-k}$, and there are
$\binom{n}{k}$ such words. Their unit points add to the point $\binom{n}{k}$ by
Theorem 1. ◻
:::

**Live proof.** If $a (x) = x + 1$, then the points of $(a + 1)^4$ are a row of
Pascal's triangle:
$\langle 0 \rangle (a + 1)^4 = \langle 4, (4)3, (6)2, (4)1, 0 \rangle$. If
$b (x) = 2x$, then $ab \neq ba$, and all four words of $(a + b)^2$ survive:
$\langle 5 \rangle (a + b)^2 = \langle 7, 12, 11, 20 \rangle$.

```mtron
mtron> 2.{2}branch(plus(1),id())
==>4
==>{2}3
==>2
mtron> 0.{4}branch(plus(1),id())
==>4
==>{4}3
==>{6}2
==>{4}1
==>0
mtron> 5.{2}branch(plus(1),mult(2))
==>7
==>12
==>11
==>20
```

::: theorem **Theorem 9** (A shared prefix factors). If $b,c \in \mathcal{F}$
and $a \in \mathcal{F}_m \cup \mathcal{F}_f \cup \mathcal{F}_{fm}$ is pure,
then $a \cdot b + a \cdot c = a \cdot (b + c)$, and for $m \leq n$,
$$
\begin{equation*}
a^m \cdot b + a^n \cdot c = a^m \cdot (b + a^{n-m} \cdot c).
\end{equation*}
$$
:::

::: proof *Proof.* The first equality is the left distributivity of the function
ring [1]. The second follows from it and Theorem 5, as $a^n = a^m \cdot a^{n-m}$.
The prefix must be pure: on the right it runs once for both members, so an
effectful $a$, a write or a print, would change what the expression does. It
must also take one object at a time, a map, filter or flatmap: an mtron branch
feeds its members one object at a time, so a reducer heading both members
reads one object in each, but the whole stream once it is shared. A common
suffix factors the other way, $b \cdot a + c \cdot a = (b + c) \cdot a$, by
right distributivity, but never a reducer $r \in \mathcal{F}_r$: the reduce
near-ring is not right distributive [1]. ◻
:::

**Live proof.** Two parallel expressions are one expression branched where
their instructions differ. Runs of `plus(1)` under the spans 2 and 3 share the
shorter run; a shared suffix merges after the branch; a shared reducer does
not merge after it, and does not factor ahead of it. The cat instruction set's
`branch_factor` rewrite applies the first equality as a normal form.

```mtron
mtron> 1.branch({2}plus(1).mult(3),{3}plus(1).mult(4))
==>9
==>16
mtron> 1.{2}plus(1).branch(mult(3),plus(1).mult(4))
==>9
==>16
mtron> 1.branch(plus(1).mult(2).plus(5),plus(1).mult(3).plus(5))
==>9
==>11
mtron> 1.plus(1).branch(mult(2),mult(3)).plus(5)
==>9
==>11
mtron> 1.branch(plus(1).count(),plus(2).count())
==>{2}1
mtron> 1.branch(plus(1),plus(2)).count()
==>2
mtron> {1,2}.branch(count().plus(1),count().plus(2))
==>{2}2
==>{2}3
mtron> {1,2}.count().branch(plus(1),plus(2))
==>3
==>4
```

**NOTES**:
`{1,2}.branch(plus(1).count(),plus(2).count())` is `{4}1` in mtron, each member
counting one object, where the split axiom of [1] applied to the whole stream
gives $\langle 2,3 \rangle r + \langle 3,4 \rangle r$, which is `{2}2`. A
reducer member is where the two readings part: is the per-object branch the
intended $+$? The rewrites agree with the branch as it runs: `branch_factor`
shares only a map, filter or flatmap.

## Coefficent Ranges

A coefficient may be a range $[m,n]$ with $m \leq n$. In mtron it is written
`{m,n}`, and `{?}` is $[0,1]$, `{+}` is $[1,\infty)$ and `{*}` is $[0,\infty)$.

::: definition **Definition 3** (Span range). If $m \leq n$, then
$$
\begin{equation*}
a^{[m,n]} = \sum_{k=m}^{n} a^k = a^m + a^{m+1} + \cdots + a^n.
\end{equation*}
$$
:::

The unrolls $a^k$ are independent terms, and each yields its own results. By
the split axiom,
$$
\begin{equation*}
\langle x \rangle a^{[m,n]} = \sum_{k=m}^{n} \langle x \rangle a^k,
\end{equation*}
$$
so paths of different lengths meet only in that sum, at the end of every path.
A range is no uncertainty over its unrolls: every unroll is taken, and the
multiplicities are not weights that sum to one. The zero-step unroll is the
unity, $a^0 = 1$, which passes its object on.

::: corollary **Corollary 6** (Each unroll carries its own power of the point).
If $c \in \mathcal{C}$ and $m \leq n$, then
$(ca)^{[m,n]} = \sum_{k=m}^{n} c^k a^k$.
:::

::: proof *Proof.* By Definition 3, $(ca)^{[m,n]} = \sum_{k=m}^{n} (ca)^k$, and
$(ca)^k = c^k a^k$ by Theorem 7. ◻
:::

In mtron, `{2,3}a` answers what the branch of its unrolls, `branch(a.a,a.a.a)`,
answers, and `{?}a` what `branch(id(),a)` answers. The sum is computed when the
instruction applies and is never written into the code: a path ends where its
monad's instruction pointer reaches noobj, and the objects of the halted monads
sum there. A monad whose object is noobj is killed instead, the zero point of
Theorem 4. As the code keeps the span, code read as a pattern still matches
`{?}neg()` as a run of none or one `neg()`.

**Live proof.** If $a (x) = x + 1$, then
$\langle 1 \rangle a^{[2,3]} = \langle 1 \rangle (a^2 + a^3) = \langle 3, 4 \rangle$
and $\langle 1 \rangle a^{[0,1]} = \langle 1, 2 \rangle$. By Corollary 6,
$\langle 1 \rangle (3a)^{[2,3]} = \langle (9)3, (27)4 \rangle$, and a
multiplicity on the input counts once:
$\langle (2)1 \rangle (3a)^{[2,3]} = \langle (18)3, (54)4 \rangle$.

```mtron
mtron> 1.{2,3}plus(1)
==>3
==>4
mtron> 1.branch(plus(1).plus(1),plus(1).plus(1).plus(1))
==>3
==>4
mtron> 1.{?}plus(1)
==>1
==>2
mtron> 1.{2,3}plus{3}(1)
==>{9}3
==>{27}4
mtron> {2}1.{2,3}plus{3}(1)
==>{18}3
==>{54}4
```

A range on a point is a range of multiplicities: if $b (x) = x + 2$, then
$\langle 1 \rangle\, [0,1]\, b = \langle ([0,1])3 \rangle$, a three held zero
or one times.

```mtron
mtron> 1.plus{?}(2)
==>{?}3
```

**Generation.** If $a (x) = x + 1$, then a range from zero counts. By
Definition 3 and Corollary 1,
$$
\begin{equation*}
\langle 0 \rangle a^{[0,n]} = \sum_{k=0}^{n} \langle 0 \rangle a^k = \sum_{k=0}^{n} \langle k \rangle = \langle 0, 1, \ldots, n \rangle.
\end{equation*}
$$
A point on the step weighs the natural $k$ by its power, by Corollary 6:
$$
\begin{equation*}
\langle 0 \rangle (2a)^{[0,n]} = \sum_{k=0}^{n} 2^k \langle k \rangle = \langle 0, (2)1, (4)2, \ldots, (2^n)n \rangle,
\end{equation*}
$$
a stream of $\sum_{k=0}^{n} 2^k = 2^{n+1} - 1$ objects. The weights count
paths: $2a = a + a$ by Definition 1, so $(2a)^k = (a + a)^k$ is the sum of the
$2^k$ words of length $k$ over two copies of $a$ (Theorem 8), and every one of
them lands on $k$.

**Live proof.** With $n = 6$: the naturals to six, then the same seven values
weighted $1, 2, 4, \ldots, 64$, which is $2^7 - 1 = 127$ objects.

```mtron
mtron> 0.{0,6}plus(1)
==>0
==>1
==>2
==>3
==>4
==>5
==>6
mtron> 0.{0,6}plus{2}(1)
==>0
==>{2}1
==>{4}2
==>{8}3
==>{16}4
==>{32}5
==>{64}6
```

An open range is the Kleene star [3],
$a^{[0,\infty)} = a^* = 1 + a + a^2 + \cdots$. If $a (x) = x + 1$, then
$\langle 0 \rangle a^* = \langle 0, 1, 2, 3, \ldots \rangle$: every natural at
once, the bounded generations above being its prefixes. A span range is a
generator. In mtron it is `0.{*}plus(1)`, which is
`start(0).branch(id(),plus(1),plus(1).plus(1),...)`.

**NOTES**: an open range has no last unroll, so `0.{*}plus(1)` (now `{*}1`)
needs its unrolls on demand, $a^* = 1 + a \cdot a^*$; `repeat` already runs
the bounded form. To settle here: how a point range such as `{?}3` reads now
that a range is no uncertainty, and the type of a generator.

## Point and Span Coefficients Type Systems

If $a (x) = y$, then $\langle x \rangle (ca)^n = \langle c^n a^n (x) \rangle$ by
Theorem 7 and the apply axiom. So the coefficient of the inferred type is
$c^n$: `type()` reads the point of a term off its run, and `cc()` reads the
coefficient of that type. In the console, `5.{2}plus{3}(1).type()` is
`int{9}::T`.

```mtron
mtron> 5.{2}plus{3}(1).type().cc()
==>9
mtron> 5.plus(1).id{3}().type().cc()
==>3
```

[To be written: the type of a range, and spans of slots in a type (`lst[{n}T]`).]

## Conclusion

**NOTES**:

- Two coefficients, one per ring operation: the point iterates $+$, the span
  iterates $\cdot$, and stream ring theory's Theorem 6, $(ca)^n = c^n a^n$,
  binds them.
- Every law here is pinned in metatron's `AbstractAlgebraTest` on nine carriers
  and proved live in this article.
- The rewriter applies two of them as normal forms: like terms sum their points (`branch_collapse`), and shared prefixes
  factor (`branch_factor`).
- Where it leads: a span range as the branch of its unrolls, merging at noobj,
  so `{*}` is a generator; layouts in space once a vid can be branched; and
  code as paths that sum like branched uris.]

# References

1. Rodriguez, M.A., "[Stream Ring Theory](./stream-ring-theory.md)," S/V Red
   Herring's Ship's Log: Chronicles in the Sea of Cortez, pages 10–40, Mulegé,
   Baja California Sur, México, February 2019.
2. Rodriguez, M.A., Claude, "[Parametric Identities in Ring
   Theory](./parametric-identities-in-ring-theory.html)."
3. Kleene, S.C., "Representation of Events in Nerve Nets and Finite Automata,"
   Automata Studies, Princeton University Press, 1956.