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
of [metatron](https://metatron.phaseshift.studio)). In mtron the *point* coefficient is written after its corresponding
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

```mtron_pre
1.{2}plus{3}(34)
```

In _branch normal form_, the point expanded, $3a = a + a + a$, so $(3a)^2 = (a + a + a)^2$:

```mtron_pre
1.{2}{plus(34),plus(34),plus(34)}
```

In _compose normal form_, the span expanded, $(3a)^2 = 3a \cdot 3a$:

```mtron_pre
1.plus{3}(34).plus{3}(34)
```

Finally, in both compose and branch normal form, the sum of the $3^2$ words $a \cdot a$:

```mtron_pre
1.{plus(34).plus(34), /
   plus(34).plus(34), /
   plus(34).plus(34), /
   plus(34).plus(34), /
   plus(34).plus(34), /
   plus(34).plus(34), /
   plus(34).plus(34), /
   plus(34).plus(34), /
   plus(34).plus(34)}
```

The purpose of this article is to prove that the laws of each coefficient alone and of the two together,
can be derived solely from the stream ring axioms of [1] as well as to demonstrate practical, utilitarian
consequences of the interplay of these two coefficients with respect to data compression,
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

::: **Definition 1** (Point). The *point* of a term
$ca \in \mathcal{C}\mathcal{F}$ is its coefficient $c \in \mathcal{C}$. In mtron
it is written after the instruction, `a{c}`. If $n \in \mathbb{N}$, then by the
stream ring's addition
$$
\begin{equation*}
na = \underbrace{1a + \cdots + 1a}_{n}.
\end{equation*}
$$
:::

::: **Definition 2** (Span). The *span* of a term is the exponent
$n \in \mathbb{N}$ of its run, where
$$
\begin{equation*}
a^0 = 1 \quad \text{and} \quad a^{n+1} = a^n \cdot a.
\end{equation*}
$$
In mtron it is written before the instruction, `{n}a`, and stays on it: `{3}a` is
one instruction whose run `a.a.a` is walked when it applies, never written out
into the code, and `{0}a` is $a^0 = 1$, the identity `id()`, whatever its point.
A term with both coefficients, `{n}a{c}`, is $(ca)^n$.
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
| `{-n}a`       | $(a^{\smile})^n$      |

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
that composition is not application. A span lays one instruction along the axis of sequential addresses, so that
`{3}plus(1)` means `plus(1)@<0>.plus(1)@<1>.plus(1)@<2>`, and it compresses that axis as the point compresses copies:
the compiled code holds the one instruction `{3}plus(1)@<0>`, whose run is walked when it applies (Theorem 10).

**NOTES**:

- The mirror: instructions and types write `{span}x{point}`, values write
  `{point}value{span}`. `{3}2` is three 2s at one address; `2{3}` is 2 laid at
  the uris 0, 1 and 2. Why does the value side flip? (My guess, not settled:
  diagrammatic order, a value being where a chain starts.)
- Caution: the stream notation $\langle x \rangle$ above is a stream, so the
  positions `<0>`, `<1>` stay in code font here, never in math.]

### The Point Coefficient

::: **Theorem 1** (Points add across a branch). If $a \in \mathcal{F}$
and $c,d \in \mathcal{C}$, then $ca + da = (c+d)a$.
:::

::: *Proof.* If $a (x) = y$, then
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

::: **Theorem 2** (Points multiply along a chain). If
$a,b \in \mathcal{F}$ and $c,d \in \mathcal{C}$, then
$ca \cdot db = (c \cdot d)(a \cdot b)$.
:::

::: *Proof.* If $a (x) = y$ and $b (y) = z$, then
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

::: **Theorem 3** (Points are central). If
$a,b \in \mathcal{F}\setminus \mathcal{F}_r$ and $c \in \mathcal{C}$, then
$ca \cdot b = a \cdot cb = (ab)c$.
:::

::: *Proof.*
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

```mtron_pre
5.branch(id{2}(),id{3}())
5.id{2}().id{3}()
5.id{3}().id{2}()
5.id{3}().plus(1)
5.plus{3}(1)
5.plus(1).id{3}()
```

::: **Theorem 4** (A zero point annihilates). If $a \in \mathcal{F}$,
then $0a = 0$.
:::

::: *Proof.* The zero of $\mathcal{C}\mathcal{F}$ is
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

```mtron_pre
5.id{0}()
5.plus{0}(1).plus(1)
5.branch(id{0}(),id())
5.id{0}().count()
```

### The Span Coefficient

::: **Theorem 5** (Spans add along a chain). If $a \in \mathcal{F}$ and
$m,n \in \mathbb{N}$, then $a^m \cdot a^n = a^{m+n}$.
:::

::: *Proof.* By induction on $n$.
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

::: **Theorem 6** (Spans stay where they are written). In general,
$a^n \cdot b \neq a \cdot b^n$.
:::

::: *Proof.* A counterexample. If $a (x) = x + 1$ and $b (x) = 2x$, then
$$
\begin{equation*}
\langle 5 \rangle a^2 b = \langle 14 \rangle \neq \langle 24 \rangle = \langle 5 \rangle a b^2.
\end{equation*}
$$
A point floats (Theorem 3), but a span is an exponent on its own function, and
the monoid $\langle\mathcal{F}, \cdot \rangle$ is not commutative. ◻
:::

**Live proof.**

```mtron_pre
5.{2}plus(1).{3}plus(1)
5.{0}plus(1)
5.{2}plus(1).mult(2)
5.plus(1).{2}mult(2)
```

::: **Corollary 1** (Inductive generation). If $a (x) = x + 1$, then
$\langle 0 \rangle a^n = \langle n \rangle$.
:::

::: *Proof.* By induction on $n$:
$\langle 0 \rangle a^0 = \langle 0 \rangle 1 = \langle 0 \rangle$, and
$\langle 0 \rangle a^{n+1} = \langle 0 \rangle a^n \cdot a = \langle n \rangle a = \langle n + 1 \rangle$. ◻
:::

```mtron_pre
0.{5}plus(1)
```

::: **Corollary 2** (A constant's run is the constant). If
$k (x) = v$ for every object $x$, then $k \cdot k = k$, so $k^n = k$ for every
$n \geq 1$, and $k^0 = 1$.
:::

::: *Proof.*
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
such a $k$. Read left to right and greedy, `{2}*a` is `{2}from(a)`, one
instruction whose run is `from(a).from(a)`.

```mtron_pre
a -> 7
{2}*a
*a.*a
```

A span on an object lays it out in space: $n$ copies of $x$ at the addresses
$0, 1, \ldots, n-1$, each copy keeping the point of $x$, so $(c)x$ along $n$
slots counts $c \cdot n$ objects. In mtron a value writes its span after it,
`x{n}`, and keeps it curled on its type: `2{3}` is one 2 that counts three, and
`{3}5{3}` counts nine and sums to 45. A space with an axis is what lays it out.

```mtron_pre
2{3}
{2}2{3}
2{3}.count()
{3}5{3}.count()
{3}5{3}.sum()
```

NOTES: spans on a value. A value's span is curled on its tid (Marko, 2026-11-11): `2{3}` is one value, not the lst
`[2,2,2]`, and only a space with an axis unrolls it (`5{3}@xyz` at `xyz/0..2`, still to come). A ranged value span,
`2{?}`, has no layout yet and does not parse. Would `2{2,3}` be the sum of its layouts, as a span range on an
instruction is?

## The Algebra Relating Point and Span Coefficients

::: **Theorem 7** (The span exponentiates the point). If
$a \in \mathcal{F}$, $c \in \mathcal{C}$ and $n \in \mathbb{N}$, then
$(ca)^n = c^n a^n$.
:::

::: *Proof.* This is the third equality of Theorem 6 of [1]. By induction
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

::: **Corollary 3** (Order matters). A point on a run is not a run of
the point: $a^n \cdot c1 = c\,a^n$, while $(ca)^n = c^n a^n$.
:::

::: *Proof.* $a^n \cdot c1 = (1 \cdot c)(a^n \cdot 1) = c\,a^n$ by stream
multiplication, and $(ca)^n = c^n a^n$ by Theorem 7. The two agree only where
$c^n = c$. ◻
:::

::: **Corollary 4** (Idempotents collapse). If $e \cdot e = e$, then
$(ce)^n = c^n e$ for every $n \geq 1$.
:::

::: *Proof.* $e^n = e$ for $n \geq 1$, by the induction of Corollary 2, so
$(ce)^n = c^n e^n = c^n e$ by Theorem 7. The unity is idempotent, so
$(c1)^n = c^n 1$. ◻
:::

::: **Corollary 5** ($0^0 = 1$). $(0a)^0 = 1$: a zero run of the
annihilator is the unity, not the zero.
:::

::: *Proof.* $t^0 = 1$ for every term $t$ by Definition 2, and $0a = 0$ is
a term (Theorem 4). ◻
:::

So a zero point annihilates only a step that runs: $(0a)^n = 0^n a^n$ is $0$ for
$n \geq 1$ and $1$ for $n = 0$ (Theorem 7), and a span range, a sum of such runs (Definition 3, below), is $0$ only if
it holds no zero-step run. In mtron, `{0}a{c}` is written
`id()` whatever $c$, and an instruction is `noobj` only when its point is zero
and its span holds no zero-step run. Tested by the product $c \cdot n$, which is
the count of a value's copies, the zero span read as the zero: a branch member
`{0}a` dropped its object, $\langle 5 \rangle (a^0 + b)$ answering
$\langle 5 \rangle b$ alone.

**Live proof.**

```mtron_pre
5.{2}id{3}()
5.{2}id().id{3}()
5.{3}id{2}()
5.{0}id{0}()
5.{0}plus{0}(1)
5.plus{0}(1)
5.branch({0}plus(1),plus(2))
```

::: **Theorem 8** (A span does not distribute over a branch). If
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

::: *Proof.* By induction on $n$, expanding as [1] expands a multinomial:
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

```mtron_pre
2.{2}branch(plus(1),id())
0.{4}branch(plus(1),id())
5.{2}branch(plus(1),mult(2))
```

::: **Theorem 9** (A shared prefix factors). If $b,c \in \mathcal{F}$
and $a \in \mathcal{F}_m \cup \mathcal{F}_f \cup \mathcal{F}_{fm}$ is pure,
then $a \cdot b + a \cdot c = a \cdot (b + c)$, and for $m \leq n$,
$$
\begin{equation*}
a^m \cdot b + a^n \cdot c = a^m \cdot (b + a^{n-m} \cdot c).
\end{equation*}
$$
:::

::: *Proof.* The first equality is the left distributivity of the function
ring [1]. The second follows from it and Theorem 5, as $a^n = a^m \cdot a^{n-m}$.
The prefix must be pure: on the right it runs once for both members, so an
effectful $a$, a write or a print, would change what the expression does. It
must also take one object at a time, a map, filter or flatmap: an mtron branch
feeds its members one object at a time, so a reducer heading both members
reads one object in each, but the whole stream once it is shared. (A
distribution rewrite may state `{*}` on the branch it emits, making the
shipment whole; that is the plan's optimization, not the instruction's
default.) A common
suffix factors the other way, $b \cdot a + c \cdot a = (b + c) \cdot a$, by
right distributivity, but never a reducer $r \in \mathcal{F}_r$: the reduce
near-ring is not right distributive [1]. ◻
:::

**Live proof.** Two parallel expressions are one expression branched where
their instructions differ. Runs of `plus(1)` under the spans 2 and 3 share the
shorter run; a shared suffix merges after the branch; a shared reducer does
not merge after it, and does not factor ahead of it. The `branch_factor` rewrite
of the `/m` instruction set applies both equalities as normal forms: runs of one
instruction under different exact spans share the shortest, so
`branch({2}plus(1).mult(3),{3}plus(1).mult(4))` rewrites to
`{2}plus(1).branch(mult(3),plus(1).mult(4))`.

```mtron_pre
1.branch({2}plus(1).mult(3),{3}plus(1).mult(4))
1.{2}plus(1).branch(mult(3),plus(1).mult(4))
1.branch(plus(1).mult(2).plus(5),plus(1).mult(3).plus(5))
1.plus(1).branch(mult(2),mult(3)).plus(5)
1.branch(plus(1).count(),plus(2).count())
1.branch(plus(1),plus(2)).count()
{1,2}.branch(count().plus(1),count().plus(2))
{1,2}.count().branch(plus(1),plus(2))
```

**NOTES**:
`{1,2}.branch(plus(1).count(),plus(2).count())` is `{4}1` in mtron, each member
counting one object, where the split axiom of [1] applied to the whole stream
gives $\langle 2,3 \rangle r + \langle 3,4 \rangle r$, which is `{2}2`. A
reducer member is where the two readings part: is the per-object branch the
intended $+$? The rewrites agree with the branch as it runs: `branch_factor`
shares only a map, filter or flatmap. A distribution rewrite may state `{*}` on
the branch it emits (the whole stream per member); the instruction's own
default stays per-object.

## Coefficient Ranges

A coefficient may be a range $[m,n]$ with $m \leq n$. In mtron it is written
`{m,n}`, and `{?}` is $[0,1]$, `{+}` is $[1,\infty)$ and `{*}` is $[0,\infty)$.

::: **Definition 3** (Span range). If $m \leq n$, then
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
unity, $a^0 = 1$, which passes its object on. Independent as terms, the unrolls
need not be computed apart: they share their prefixes, and Theorem 10 takes them
all in one walk.

::: **Corollary 6** (Each unroll carries its own power of the point).
If $c \in \mathcal{C}$ and $m \leq n$, then
$(ca)^{[m,n]} = \sum_{k=m}^{n} c^k a^k$.
:::

::: *Proof.* By Definition 3, $(ca)^{[m,n]} = \sum_{k=m}^{n} (ca)^k$, and
$(ca)^k = c^k a^k$ by Theorem 7. ◻
:::

In mtron, `{2,3}a` answers what the branch of its unrolls, `branch(a.a,a.a.a)`,
answers when `a` takes one object at a time, and `{?}a` what `branch(id(),a)`
answers. The sum is computed when the instruction applies and is never written
into the code (see *Evaluating a Span*). As the code keeps the span, code read
as a pattern still matches `{?}neg()` as a run of none or one `neg()`.

**Live proof.** If $a (x) = x + 1$, then
$\langle 1 \rangle a^{[2,3]} = \langle 1 \rangle (a^2 + a^3) = \langle 3, 4 \rangle$
and $\langle 1 \rangle a^{[0,1]} = \langle 1, 2 \rangle$. By Corollary 6,
$\langle 1 \rangle (3a)^{[2,3]} = \langle (9)3, (27)4 \rangle$, and a
multiplicity on the input counts once:
$\langle (2)1 \rangle (3a)^{[2,3]} = \langle (18)3, (54)4 \rangle$.

```mtron_pre
1.{2,3}plus(1)
1.branch(plus(1).plus(1),plus(1).plus(1).plus(1))
1.{?}plus(1)
1.{2,3}plus{3}(1)
{2}1.{2,3}plus{3}(1)
```

A range on a point is a range of multiplicities: if $b (x) = x + 2$, then
$\langle 1 \rangle\, [0,1]\, b = \langle ([0,1])3 \rangle$, a three held zero
or one times.

```mtron_pre
1.plus{?}(2)
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

```mtron_pre
0.{0,6}plus(1)
0.{0,6}plus{2}(1)
```

An open range is the Kleene star [3],
$a^{[0,\infty)} = a^* = 1 + a + a^2 + \cdots$. If $a (x) = x + 1$, then
$\langle 0 \rangle a^* = \langle 0, 1, 2, 3, \ldots \rangle$: every natural at
once, the bounded generations above being its prefixes. A span range is a
generator. In mtron it is `0.{*}plus(1)`, which is
`start(0).branch(id(),plus(1),plus(1).plus(1),...)`. An open range has no last
unroll, so it is taken as $a^* = 1 + a \cdot a^*$, its unrolls on demand, each
one step past the last (Theorem 10), and it ends only where a run leaves the
domain of $a$ (Corollary 8). `0.{*}plus(1)` never does.

**NOTES**: to settle here: how a point range such as `{?}3` reads now that a
range is no uncertainty, the type of a generator, and a lazy reader that takes
a prefix of `0.{*}plus(1)` without walking all of it.

## Evaluating a Span

Definition 3 sums independent unrolls, and the plain way to compute the sum takes them one at a time: the unroll
$a^k$ applies $a$ to the input $k$ times, so $a^{[m,n]}$ costs $\sum_{k=m}^{n} k$ applications, and an exact span
written out as its compose chain $a \cdot \cdots \cdot a$ is a term $n$ functions long. The unrolls share their
prefixes, and a shared prefix factors. Factored all at once, the sum is Horner's rule.

::: **Theorem 10** (A span range is one walk). If $a \in \mathcal{F}$ is pure and $m \leq n$, then
$$
\begin{equation*}
a^{[m,n]} = a^m \cdot (1 + a \cdot (1 + a \cdot (\cdots (1 + a)))),
\end{equation*}
$$
with $n - m$ factors of $a$ after $a^m$. So if $\langle x_0 \rangle = \langle x \rangle$ and
$\langle x_{k+1} \rangle = \langle x_k \rangle a$, then
$\langle x \rangle a^{[m,n]} = \sum_{k=m}^{n} \langle x_k \rangle$: each unroll is one step past the last.
:::

::: *Proof.* A pure prefix factors on a stream whatever it does to the stream, by the split axiom: if
$\langle x \rangle a = \langle y \rangle$, then
$\langle x \rangle (a \cdot b + a \cdot c) = \langle y \rangle b + \langle y \rangle c = \langle y \rangle (b + c) = \langle x \rangle a \cdot (b + c)$.
So
$$
\begin{equation*}
\begin{array}{lclll}
a^{[m,n]} &=& \sum_{k=m}^{n} a^m \cdot a^{k-m} && [\text{Definition 3, Theorem 5}] \\
&=& a^m \cdot \sum_{j=0}^{n-m} a^j = a^m \cdot a^{[0,n-m]} && [\text{shared prefix}] \\
a^{[0,j]} &=& 1 + \sum_{i=1}^{j} a \cdot a^{i-1} = 1 + a \cdot a^{[0,j-1]} && [\text{Definition 3, Theorem 5, shared prefix}],
\end{array}
\end{equation*}
$$
and unfolding $a^{[0,j]}$ down to $a^{[0,0]} = 1$ nests the $n - m$ factors. ◻
:::

Purity is the one condition, as in Theorem 9: the factored prefix runs once for every unroll it heads. The other
condition of Theorem 9, that the prefix take one object at a time, does not arise. The sum of Definition 3 is the split
axiom applied to the whole stream $\langle x \rangle$, so a reducer heads its unrolls as any function does. It is
mtron's branch, feeding its members one object at a time, that is not this sum.

::: **Corollary 7** (The cost of a walk). A walk of $a^{[m,n]}$ applies $a$ $n$ times, where the unrolls taken one at a
time apply it $\sum_{k=m}^{n} k = \frac{(n-m+1)(m+n)}{2}$ times.
:::

::: *Proof.* Each $\langle x_k \rangle$ is one application past $\langle x_{k-1} \rangle$, for $k = 1, \ldots, n$, and
the unroll $a^k$ alone applies $a$ $k$ times. ◻
:::

::: **Corollary 8** (A walk halts where its run leaves the domain). Read $a$ as partial on its domain $D$, with
$\langle x \rangle a = \langle\rangle$ for $x \notin D$. If a run $\langle x_k \rangle$ holds no object of $D$, then
$\langle x_{k+1} \rangle = \langle\rangle$, and $\langle\rangle a^j = \langle\rangle$ for every $j$, so every later
unroll adds $0$: the walk halts there. A reducer is the exception, as its domain is the streams themselves: it runs on
the empty stream by the reduce apply axiom of [1], $\langle\rangle r = \langle r (\langle\rangle) \rangle$, and its walk
steps on.
:::

::: *Proof.* A function that is no reducer applies to a run object by object (the split axiom), each object outside
$D$ adding $\langle\rangle$, and it maps the empty stream to the empty stream. ◻
:::

**Live proof.** If $a (x) = x + 1$, then the unroll $a^k$ from $0$ holds $k$ (Corollary 1), which is also what it costs
taken alone: the thousand runs of $a^{[1,1000]}$ sum to the $\sum_{k=1}^{1000} k = 500{,}500$ applications of its
unrolls one at a time. The walk takes $1{,}000$.

```mtron_pre
0.{1,1000}plus(1).count()
0.{1,1000}plus(1).sum()
```

If $s$ sums a stream, then $\langle 1, 2, 3 \rangle s^{[0,1]} = \langle 1, 2, 3 \rangle + \langle 6 \rangle$, the split
over the whole stream, while a branch that feeds $s$ one object at a time sums each alone,
$\langle (2)1, (2)2, (2)3 \rangle$. If $c$ counts a stream, then
$\langle 1, 2, 3 \rangle c^{[1,2]} = \langle 3 \rangle + \langle 1 \rangle$. A step that takes one object at a time
walks object by
object: $\langle 1, 2 \rangle a^{[0,2]} = \langle 1, 2 \rangle + \langle 2, 3 \rangle + \langle 3, 4 \rangle$.

```mtron_pre
{1,2,3}.{0,1}sum()
{1,2,3}.branch(id(),sum())
{1,2,3}.{1,2}count()
{1,2}.{0,2}plus(1)
```

If $f$ reads the field $a$ of a record, then
$\langle [a \Rightarrow [a \Rightarrow 5]] \rangle f^{[0,5]} = \langle [a \Rightarrow [a \Rightarrow 5]], [a \Rightarrow 5], 5 \rangle$:
$5$ is no record, so the walk halts there, three runs short of five (Corollary 8). The open walk halts the same way at a
record without the field, and a count steps on from nothing:
$\langle\rangle c^{[1,2]} = \langle 0 \rangle + \langle 1 \rangle$.

```mtron_pre
[a=>[a=>5]].{0,5}rshift(a)
[a=>[a=>[b=>5]]].{*}rshift(a)
{1,2,3}.is(gt(5)).{1,2}count()
```

In mtron, `{n}a` and `{m,n}a` are each one instruction, and the processor walks it: the input, then each run one step
past the last, the runs from $m$ on kept, until the run $n$ or a run that leaves the domain of `a`. How much of a run a
step takes is the coefficient of its domain: `sum?int<=int{*}()` takes a run whole, and `plus?int<=int()` one object at
a time, as the swarm feeds them. A step compiled for the type of its first run is resolved again for a run of another
type, as each copy of an unrolled chain was compiled against its own input: `1.{2}as(str::T)` casts an `int` to the
`str`
`'1'`, and its second run casts that `str`, which the cast compiled for an `int` never was.

```mtron_pre
1.{2}as(str::T)
1.as(str::T).as(str::T)
```

The walk replaced two naive evaluations of a span. The parser wrote an exact span out as its compose chain, so
`0.{10}plus(4)` compiled to eleven instructions, where it now compiles to two. A closed range recomputed every unroll
from its input, so `{1,1000}` took 500,500 steps, an effectful step ran once per unroll it headed rather than once per
step, and a range on a reducer that started at one ran the reducer one object at a time: `{1,2,3}.{1,2}count()`
answered $\langle (6)1 \rangle$. The compilation tables of the span and of the chain the parser used to write out:

```mtron_pre
0.{10}plus(4).explain()>>format
0.plus(4).plus(4).plus(4).plus(4).plus(4).plus(4).plus(4).plus(4).plus(4).plus(4).explain()>>format
```

A span is mtron's own `repeat` written as a coefficient. `repeat(code=>a,emit=>p,until=>q)` applies `a` to its object
again and again. Before each application it emits the object if `p` holds, and once `q` holds it stops and passes the
object on; `loop()` reads how many times `a` has applied. The emit predicate is the lower bound of the sum of
Definition 3, and the until predicate is its upper bound:

| span     | repeat                                                            | the sum                      |
|----------|-------------------------------------------------------------------|------------------------------|
| `{n}a`   | `repeat(code=>a,until=>loop().is(eq(n)))`                         | $a^n$                        |
| `{0,n}a` | `repeat(code=>a,emit=>true,until=>loop().is(eq(n)))`              | $1 + a + \cdots + a^n$       |
| `{m,n}a` | `repeat(code=>a,emit=>loop().is(gte(m)),until=>loop().is(eq(n)))` | $a^m + \cdots + a^n$         |
| `{m,}a`  | `repeat(code=>a,emit=>loop().is(gte(m)))`                         | $a^m + a^{m+1} + \cdots$     |
| `{*}a`   | `repeat(code=>a,emit=>true)`                                      | $a^* = 1 + a + a^2 + \cdots$ |

The until predicate passes on the object it stops at, so `{n}a` needs no emit. An open span has no until: its repeat
ends where a run is empty, which is where the walk halts (Corollary 8). The walk is this repeat with its loop count kept
on the instruction rather than in a loop frame, and an engine that loops natively runs the same repeat: a span range on
a graph step is pushed down as the graph's own repeat.

```mtron_pre
0.{2,4}plus(1)
0.repeat(code=>plus(1),emit=>loop().is(gte(2)),until=>loop().is(eq(4)))
1.{4}mult(2)
1.repeat(code=>mult(2),until=>loop().is(eq(4)))
[a=>[a=>[b=>5]]].{*}rshift(a)
[a=>[a=>[b=>5]]].repeat(code=>rshift(a),emit=>true)
```

An engine that does not loop takes no span. A rewrite matches a pattern of single applications, and a spanned step is a
run: `{2}count()` is $c^2$, the count of a count, and a native count that took it for $c$ answered the count of the
rows.
So a spanned step stays in the plan and walks over the native answer, as the rewriter's own laws leave a run where it
is: the unit law drops the identity `plus(0)`, a single application, and leaves the run `{2}plus(0)` to the walk.

```mtron_pre
5.plus(0).explain()>>desc>>insts
5.{2}plus(0).explain()>>desc>>insts
5.{2}plus(0)
```

## Negative Spans: the Converse

A span counts the steps of a run, and a negative span counts them backward. The backward step is not an inverse that
cancels the forward one, as a function that sends two objects to one place cannot be undone. It is the function's
*converse*: the relation the function states, read from its other end [4].

Read a function as that relation, with multiplicities. If $a \in \mathcal{F}$, let $\mu_a (x,y)$ be the coefficient of
$y$ in $\langle x \rangle a$, so that $\langle x \rangle a = \sum_y \mu_a (x,y)\, y$, each output once by the bulk axiom.
By the apply, split and bulk axioms, composition multiplies these relations as matrices,
$\mu_{a \cdot b} (x,z) = \sum_y \mu_a (x,y)\, \mu_b (y,z)$; by the split and merge axioms, a branch adds them,
$\mu_{a + b} = \mu_a + \mu_b$; and a point scales them, $\mu_{ca} = c\, \mu_a$.

::: **Definition 4** (Converse). The *converse* $a^{\smile}$ of $a \in \mathcal{F}$ is the relation of $a$ read from its
other end, $\mu_{a^{\smile}} (y,x) = \mu_a (x,y)$:
$$
\begin{equation*}
\langle y \rangle a^{\smile} = \sum_x \mu_a (x,y)\, x,
\end{equation*}
$$
every object that $a$ sends to $y$, as often as $a$ sends it there.
:::

The converse is a stream function only where each object has finitely many preimages. A constant has every object as a
preimage of its value, and a count every stream of its size, so neither converse can be listed.

::: **Definition 5** (Negative span). If $n \in \mathbb{N}$, then $a^{-n} = (a^{\smile})^n$, and a span range
$a^{[m,n]} = \sum_{k=m}^{n} a^k$ (Definition 3) may cross zero. In mtron a negative span is written `{-n}a`, and a range
`{-m,n}a`.
:::

::: **Theorem 11** (The converse is an involution). If $a,b \in \mathcal{F}$ and $c \in \mathcal{C}$, with
$\mathcal{C}$ commutative, then
$$
\begin{equation*}
(a + b)^{\smile} = a^{\smile} + b^{\smile}, \quad (a \cdot b)^{\smile} = b^{\smile} \cdot a^{\smile}, \quad
(ca)^{\smile} = c\, a^{\smile}, \quad (a^{\smile})^{\smile} = a, \quad 1^{\smile} = 1, \quad 0^{\smile} = 0.
\end{equation*}
$$
:::

::: *Proof.* Each equality is one of the relations read from its other end:
$$
\begin{equation*}
\begin{array}{lclll}
\mu_{(a + b)^{\smile}} (y,x) &=& \mu_a (x,y) + \mu_b (x,y) = \mu_{a^{\smile} + b^{\smile}} (y,x) && [\mu_{a+b} = \mu_a + \mu_b] \\
\mu_{(a \cdot b)^{\smile}} (z,x) &=& \sum_y \mu_a (x,y)\, \mu_b (y,z) && [\text{composition}] \\
&=& \sum_y \mu_{b^{\smile}} (z,y)\, \mu_{a^{\smile}} (y,x) = \mu_{b^{\smile} \cdot a^{\smile}} (z,x) && [\text{$\mathcal{C}$ commutes, Definition 4}] \\
\mu_{(ca)^{\smile}} (y,x) &=& c\, \mu_a (x,y) = \mu_{c\, a^{\smile}} (y,x) && [\mu_{ca} = c\, \mu_a].
\end{array}
\end{equation*}
$$
Read twice from the other end, a relation reads from its first end again, and $\mu_1 (x,y)$, which is $1$ where
$x = y$ and $0$ elsewhere, reads the same from both ends, as $\mu_0 = 0$ does. ◻
:::

So the stream ring with its converse is a ring with an involution, a $*$-ring. Over the complex numbers the involution
conjugates the coefficient as well, $(ca)^{\smile} = \bar{c}\, a^{\smile}$, and the converse of a quantum walk is its
adjoint: the conjugate transpose with which [1] inverts a wave. The adjoint of a unitary is its inverse.

::: **Corollary 9** (A converse is no inverse). If $a$ is a bijection, then $a \cdot a^{\smile} = a^{\smile} \cdot a = 1$,
and $a^{-1}$ is the inverse of $a$. In general $a \cdot a^{\smile} \neq 1$.
:::

::: *Proof.* If $a$ is a bijection, then $\mu_a (x,y)$ is $1$ where $y = a (x)$ and $0$ elsewhere, so
$\mu_{a \cdot a^{\smile}} (x,x') = \sum_y \mu_a (x,y)\, \mu_a (x',y)$ is $1$ where $a (x) = a (x')$, which is where
$x = x'$, as $a$ is injective. That is $\mu_1$, and the other order is the same with the bijection $a^{\smile}$. If
instead $a$ sends two objects to one, $a (x) = a (x') = y$ with $x \neq x'$, then
$\langle x \rangle a \cdot a^{\smile} = \langle y \rangle a^{\smile} = \langle x, x' \rangle \neq \langle x \rangle$. ◻
:::

The forward run and the backward run cancel only at a bijection, where the converse is the inverse. In a graph, `out()`
read backward is `in()`, and `out().in()` reaches the siblings of a vertex, not the vertex alone.

::: **Theorem 12** (Signed spans add when their signs agree). If $a \in \mathcal{F}$ and $m,n \in \mathbb{Z}$ with
$mn \geq 0$, then $a^m \cdot a^n = a^{m+n}$.
:::

::: *Proof.* If $m,n \geq 0$, this is Theorem 5. If $m,n \leq 0$, then
$a^m \cdot a^n = (a^{\smile})^{-m} \cdot (a^{\smile})^{-n} = (a^{\smile})^{-m-n} = a^{m+n}$ by Definition 5 and Theorem 5
for $a^{\smile}$. If $m > 0 > n$, the law holds for a bijection and fails in general, as $a \cdot a^{-1} \neq a^0$
(Corollary 9). ◻
:::

::: **Theorem 13** (A negative span keeps the power of the point). If $a \in \mathcal{F}$, $c \in \mathcal{C}$, with
$\mathcal{C}$ commutative, and $n \in \mathbb{N}$, then $(ca)^{-n} = c^n a^{-n}$.
:::

::: *Proof.*
$$
\begin{equation*}
\begin{array}{lclll}
(ca)^{-n} &=& ((ca)^{\smile})^n && [\text{Definition 5}] \\
&=& (c\, a^{\smile})^n && [\text{Theorem 11}] \\
&=& c^n (a^{\smile})^n = c^n a^{-n} && [\text{Theorem 7, Definition 5}].
\end{array}
\end{equation*}
$$
◻
:::

The point does not invert: a run backward follows as many paths as the run forward, so Theorem 7 holds for every
integer span, with the point to the power $|n|$.

::: **Corollary 10** (A range across zero walks both ways). If $m,n \in \mathbb{N}$, then
$a^{[-m,n]} = (a^{\smile})^{[1,m]} + a^{[0,n]}$: from its input, the walk of Theorem 10 runs backward through
$a^{\smile}$ and forward through $a$.
:::

::: *Proof.* Definition 3, with the sum split at zero, and Definition 5. ◻
:::

::: **Theorem 14** (A filter is its own converse). If $f \in \mathcal{F}$ is a filter,
$\langle x \rangle f \in \{\langle x \rangle, \langle\rangle\}$, then $f^{\smile} = f$.
:::

::: *Proof.* $\mu_f (x,y) = 0$ wherever $y \neq x$, and a relation that holds only where $x = y$ reads the same from both
ends. ◻
:::

Each coefficient carries its own sign. A negative point is the additive inverse: $a + (-1)a = (1 - 1)a = 0$ by
Theorems 1 and 4, so copies of opposite sign cancel. A negative span is the converse, which reverses and cancels only at
a bijection. The point's sign belongs to $+$ and the span's to $\cdot$, as the coefficients themselves do.

**Live proof.** If $a (x) = x + 2$, then $a^{\smile} (x) = x - 2$, a bijection's converse being its inverse, so
$\langle 4 \rangle a^{-1} = \langle 2 \rangle$, and if $b (x) = x + 1$, then $\langle 5 \rangle b^{-1} \cdot b =
\langle 5 \rangle$ (Corollary 9). Runs of one sign add, $b^{-1} \cdot b^{-1} = b^{-2}$ (Theorem 12). Negation is its own
converse, and a real doubling's converse is a halving.

```mtron_pre
4.{-1}plus(2)
5.{-1}plus(1).{1}plus(1)
5.{-2}plus(1)
5.{-1}plus(1).{-1}plus(1)
5.{-1}neg()
5.0.{-1}mult(2.0)
5.0.{-1}mult(2.0).mult(2.0)
```

A range across zero walks both ways (Corollary 10): $\langle 5 \rangle b^{[-1,1]} = \langle 4, 5, 6 \rangle$ and
$\langle 0 \rangle b^{[-3,3]} = \langle -3, \ldots, 3 \rangle$. In a graph, `{-1,1}out()` is `in()`, `id()` and `out()`
at once.

```mtron_pre
5.{-1,1}plus(1)
0.{-3,3}plus(1)
```

A negative point cancels and a negative span reverses: $3 + (-1)3 = 0$, and
$\langle 5 \rangle (b + (-1)b) = \langle 6, (-1)6 \rangle = \langle\rangle$, while $\langle 5 \rangle b^{-1} = \langle 4 \rangle$.

```mtron_pre
{3,{-1}3}.count()
5.branch(plus(1),plus{-1}(1)).count()
5.{-1}plus(1)
```

In mtron, an instruction declares its converse in its `?decq` claim, as the contract it runs backward:
`plus?int<=int(int)` names `minus?int<=int(int)`, and `neg` names itself. The walk of Theorem 10 splits a span at zero
and runs a negative length as that many steps of the converse. An instruction that declares none has no backward run to
compute, and that part of its span stays as code on its input, `start(x).{-n}inst`: in limbo. Integer `mult` declares
none, as division truncates, and the converse of a count cannot be listed. A claim's *inverse* is another thing, a
one-way retraction that the rewriter cancels, $f \cdot g = 1$, where a converse never cancels. A value is a constant
(Corollary 2), whose converse relates its value to every object, so a value takes no negative span: `3{-1}` does not
parse. A type used as a step is a filter, and so its own converse (Theorem 14).

**NOTES**:

- `is()` declares no converse, so `5.{-1}is(gt(3))` stays in limbo where Theorem 14 gives `5.is(gt(3))`. A rule by
  form, every filter its own converse, would cover `is()` and every type used as a step.
- A lst read as a word: is its converse its reversal, so that `[1,2,3]{-1}` is `[3,2,1]`? `reverse()` is declared an
  involution, as a converse is. Today `[1,2,3]{-1}` does not parse.
- On a rel, mtron's `neg()` swaps the ends, so `(1=>2).neg()` is `2=>1`. The arrow $x \Rightarrow y$ is the matrix
  with a $1$ at row $x$ and column $y$ [2], so the swap is its transpose, the converse
  $(x \Rightarrow y)^{\smile} = y \Rightarrow x$, and not its additive inverse, which is the point `{-1}(1=>2)`.
- A lst under test lays an entry's span times its point as slots. A negative count lays none and is skipped, so
  `[{-1}3,4]` passes for `[int::T]`. A run of like slots read backward is the same run, so a negative span could lay its
  length, while a negative point, an anti-entry, should neither lay nor be skipped.
- By Theorem 13, `5.{-1}plus{3}(1)` is `{3}4` and `5.{-2}plus{3}(1)` is `{9}3`, as `5.{2}plus{3}(1)` is `{9}7`. Today
  mtron answers `4` and `3`: the walk builds the converse step from its declared contract (`Inst.Helper.runs`) and
  leaves the step's point behind.

```mtron_pre
5.{2}plus{3}(1)
5.{-1}plus{3}(1)
5.{-2}plus{3}(1)
```

## Point and Span Coefficients Type Systems

If $a (x) = y$, then $\langle x \rangle (ca)^n = \langle c^n a^n (x) \rangle$ by
Theorem 7 and the apply axiom. So the coefficient of the inferred type is
$c^n$: `type()` reads the point of a term off its run, and `cc()` reads the
coefficient of that type. In the console, `5.{2}plus{3}(1).type()` is
`int{9}::T`. The compiler types a span by its runs, never by its span times its
point, which is the count of a value's copies (`{3}5{3}` counts nine):
`0.{10}plus(4)` is typed one `int`, not ten.

```mtron_pre
5.{2}plus{3}(1).type().cc()
5.plus(1).id{3}().type().cc()
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
  factor (`branch_factor`). The processor applies the second to every span: its walk is the factored form of its
  unrolls (Theorem 10).
- A zero point annihilates, while a zero span is the unity (Corollary 5): `{0}a` is `id()`. A span is a `repeat` whose
  emit is its lower bound and whose until is its upper, so an engine that loops runs it, and no rewrite takes a run for
  a single application.
- Each coefficient has its own sign. A negative point is the additive inverse, which cancels; a negative span is the
  converse, which reverses and cancels only at a bijection (Corollary 9). With its converse the stream ring is a
  $*$-ring (Theorem 11), whose involution over the complex numbers is the adjoint that inverts a wave in [1].
- Where it leads: a span range as one walk of its unrolls, so `{*}` is a
  generator that halts where its runs leave the domain (Corollary 8); layouts
  in space once a vid can be branched; and code as paths that sum like branched
  uris.]

# References

1. Rodriguez, M.A., "[Stream Ring Theory](./stream-ring-theory.md)," S/V Red Herring's Ship's Log: Chronicles in the Sea
   of Cortez, pages 10–40, Mulegé, Baja California Sur, México, February 2019.
2. Rodriguez, M.A., Stynx, D.R., "[Parametric Identities in Ring Theory](./parametric-identities-in-ring-theory.md)."
3. Kleene, S.C., "Representation of Events in Nerve Nets and Finite Automata,"
   Automata Studies, Princeton University Press, 1956.
4. Tarski, A., "On the Calculus of Relations," The Journal of Symbolic Logic, 6(3), pages 73–89, 1941.