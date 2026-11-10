---
name: A Span and Point Coefficient Algebra for Functional Rings
description: two coefficients on every instruction, the point counting copies and the span counting steps, and the laws that bind them
author: |
  Marko A. Rodriguez
  Dr. Stynx
---

# Introduction

Stream ring theory [[1]](./stream-ring-theory.md) gave every function a
coefficient. A term $ca$ of the stream ring
$\langle\mathcal{C}\mathcal{F}, +, \cdot \rangle$ pairs a coefficient
$c \in \mathcal{C}$ with a function $a \in \mathcal{F}$, and the coefficient
floats: $ca = a \cdot c$. In mtron that coefficient is written after its
instruction, `plus{3}(1)`, and it is called the *point*: three copies of the
output at one address.

The position in front of the instruction holds a second coefficient, the
*span*: `{3}plus(1)` is `plus(1).plus(1).plus(1)`, three steps laid one after
another. The two coefficients are the two ring operations, iterated. For a
natural number $n$, the point counts additions, $na = a + \cdots + a$, and the
span counts multiplications, $a^n = a \cdot \cdots \cdot a$. A point is mass at
one address. A span is extent along a line of addresses.

Stream ring theory already holds the bridge between them, the third equality of
its Theorem 6: $(ca)^n = c^n a^n$. In mtron, `{n}a{c}` is $(ca)^n = c^n a^n$:
the span exponentiates the point.

**Live proof.** Two steps of `plus{3}(34)` from `1`: the value climbs to `69`,
and the point squares to `9`.

```mtron_pre
1.{2}plus{3}(34)
1.plus{3}(34).plus{3}(34)
```

This article states the laws of each coefficient alone and of the two together,
proves each one from the stream ring axioms of [1] and live, and asks what a
range in either position means.

# Two Coefficients

The objects, coefficients and functions are those of [1]: $x, y, z \in X$ are
objects, $c, d, e \in \mathcal{C}$ are coefficients of a ring with unity, and
$a, b, f \in \mathcal{F}$ are stream functions. The function ring has the zero
$0 (x) = \emptyset$ and the unity $1 (x) = x$, and a stream is written
$\langle x, y, z \rangle$, with $\langle x \rangle a$ the stream $a$ outputs on
$x$. The proofs below cite the stream ring axioms of [1] by name: the *apply*,
*split*, *merge* and *bulk* axioms, $\langle 0x \rangle = \langle\rangle$, and
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

| mtron | stream ring |
|----------------------|-------------------------------|
| `x.a` | $\langle x \rangle a$ |
| `a.b` | $a \cdot b$ |
| `branch(a,b)` | $a + b$ |
| `id()` | $1$ |
| `noobj` | $0$ |
| `{c}x` | $cx$ |
| `a{c}` | $ca$ |
| `{n}a` | $a^n$ |
| `{n}a{c}` | $(ca)^n$ |

Where a coefficient is written depends on what it is written on:

| written on    | prefix                         | postfix                          |
|---------------|--------------------------------|----------------------------------|
| an instruction | span: `{n}a` is $a^n$          | point: `a{c}` is $ca$            |
| a type        | span: `{n}T` is $n$ slots of `T` | point: `T{c}` is $c$ copies of `T` |
| a value       | point: `{c}x` is $cx$          | span: `x{n}` lays `x` along $n$ slots |

[To be written: the geometric reading. A chain is sequential extension in space,
not application: `plus(1).plus(2)` is `plus(1)@<0>.plus(2)@<1>`, and a span lays
one instruction along the index axis. Open: why values mirror instructions and
types. Value spans such as `2{3}` do not parse today.]

# The Point

::: theorem **Theorem 1** (Points add across a branch). If $a \in \mathcal{F}$
and $c,d \in \mathcal{C}$, then $ca + da = (c+d)a$.
:::

::: proof *Proof.* If $a (x) = y$, then
$$
\begin{equation*}
\begin{array}{lclll}
ca + da &=& (c+d)a && \\
\langle x \rangle (ca + da) &=& \langle x \rangle (c+d)a && [\text{apply $1x$}] \\
(\langle x \rangle ca) + (\langle x \rangle da) &=& \langle (c+d)y \rangle && [\text{split, apply axioms}] \\
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

```mtron_pre
5.branch(id{2}(),id{3}())
5.id{2}().id{3}()
5.id{3}().id{2}()
5.id{3}().plus(1)
5.plus{3}(1)
5.plus(1).id{3}()
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

```mtron_pre
5.id{0}()
5.plus{0}(1).plus(1)
5.branch(id{0}(),id())
5.id{0}().count()
```

# The Span

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

```mtron_pre
5.{2}plus(1).{3}plus(1)
5.{0}plus(1)
5.{2}plus(1).mult(2)
5.plus(1).{2}mult(2)
```

::: corollary **Corollary 1** (Inductive generation). If $a (x) = x + 1$, then
$\langle 0 \rangle a^n = \langle n \rangle$.
:::

::: proof *Proof.* By induction on $n$:
$\langle 0 \rangle a^0 = \langle 0 \rangle 1 = \langle 0 \rangle$, and
$\langle 0 \rangle a^{n+1} = \langle 0 \rangle a^n \cdot a = \langle n \rangle a = \langle n + 1 \rangle$. ◻
:::

```mtron_pre
0.{5}plus(1)
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

```mtron_pre
a -> 7
{2}*a
*a.*a
```

[To be written: spans on a value.]

# The Point and the Span

::: theorem **Theorem 7** (The span exponentiates the point). If
$a \in \mathcal{F}$, $c \in \mathcal{C}$ and $n \in \mathbb{N}$, then
$(ca)^n = c^n a^n$.
:::

::: proof *Proof.* This is the third equality of Theorem 6 of [1]. By induction
on $n$:
$$
\begin{equation*}
\begin{array}{lclll}
(ca)^0 &=& 1 = (1,1) = c^0 a^0 && [\text{Definition 2}] \\
(ca)^{n+1} &=& (ca)^n \cdot ca && [\text{Definition 2}] \\
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

```mtron_pre
5.{2}id{3}()
5.{2}id().id{3}()
5.{3}id{2}()
5.{0}id{0}()
```

::: theorem **Theorem 8** (A span does not distribute over a branch). If
$a,b \in \mathcal{F}$, then
$$
\begin{equation*}
(a + b)^n = \sum_{w \in \{a,b\}^n} w,
\end{equation*}
$$
the sum of all $2^n$ words of length $n$ over $a$ and $b$. If $ab = ba$, then
$$
\begin{equation*}
(a + b)^n = \sum_{k=0}^{n} \binom{n}{k} a^k b^{n-k},
\end{equation*}
$$
and each binomial coefficient is a point. In general,
$(a + b)^n \neq a^n + b^n$.
:::

::: proof *Proof.* By induction on $n$, expanding as [1] expands a multinomial:
$$
\begin{equation*}
\begin{array}{lclll}
(a + b)^{n+1} &=& (a + b)^n \cdot (a + b) && [\text{Definition 2}] \\
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

# Ranges

A coefficient may be a range $[m,n]$ with $m \leq n$, written `{m,n}`. The range
`{?}` is $[0,1]$, `{+}` is $[1,\infty)$, and `{*}` is $[0,\infty)$.

On a point, a range is a superposition of multiplicities: `1.plus{?}(2)` is
`{?}3`, three held in a point that is zero or one, and it stays in
superposition.

On a span, a range is a choice of layouts, not a branch:

::: definition **Definition 3** (Span range). If $m \leq n$, then
$$
\begin{equation*}
a^{[m,n]} = a^m \mid a^{m+1} \mid \cdots \mid a^n,
\end{equation*}
$$
where $\mid$ is choice: $\langle x \rangle (a \mid b)$ is one of
$\langle x \rangle a$ and $\langle x \rangle b$, while
$\langle x \rangle (a + b)$ is both. So $a^{[0,1]} = 1 \mid a$, and
$a^{[0,\infty)} = 1 \mid a \mid a^2 \mid \cdots$ is the Kleene star $a^*$ [3].
mtron reads $[0,\infty)$ greedily, as a regular expression reads its quantifier:
the last layout.
:::

The span range turns the ring's $\cdot$ into a choice, a $+$ from another
algebra: the stream ring's $+$ keeps every branch, and $\mid$ keeps one.

[To be written, scope to be decided. Live proofs pending: today `1.{?}plus(1)`
answers `{?}2`, where Definition 3 gives $\langle 1 \rangle (1 \mid a)$, one of
$\langle 1 \rangle$ and $\langle 2 \rangle$, and `1.{2,3}plus(1)` answers
`{2,3}2`, where it gives one of $\langle 3 \rangle$ and $\langle 4 \rangle$.]

# Types

If $a (x) = y$, then $\langle x \rangle (ca)^n = \langle c^n a^n (x) \rangle$ by
Theorem 7 and the apply axiom. So the coefficient of the inferred type is
$c^n$: `type()` reads the point of a term off its run.

```mtron_pre
5.{2}plus{3}(1).type()
5.plus(1).id{3}().type()
```

[To be written: the type of a range, and spans of slots in a type (`lst[{n}T]`).]

# Conclusion

[To be written.]

# References

1. Rodriguez, M.A., "[Stream Ring Theory](./stream-ring-theory.md)," S/V Red
   Herring's Ship's Log: Chronicles in the Sea of Cortez, pages 10–40, Mulegé,
   Baja California Sur, México, February 2019.
2. Rodriguez, M.A., Claude, "[Parametric Identities in Ring
   Theory](./parametric-identities-in-ring-theory.html)."
3. Kleene, S.C., "Representation of Events in Nerve Nets and Finite Automata,"
   Automata Studies, Princeton University Press, 1956.
