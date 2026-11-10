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
its Theorem 6: $(ca)^n = c^n a^n$. In mtron, `{n}a{c}` is `a`$^n$ with point $c^n$: the span
exponentiates the point.

**Live proof.** Two steps of `plus{3}(34)` from `1`: the value climbs to `69`,
and the point squares to `9`.

```mtron_pre
1.{2}plus{3}(34)
1.plus{3}(34).plus{3}(34)
```

This article states the laws of each coefficient alone and of the two together,
proves each one live, and asks what a range in either position means.

# Two Coefficients

[To be written: the definitions of the point and the span over the stream ring
of [1]; the geometric reading (the point is mass at one address, the span is
extent along a line of addresses: `plus(1).plus(2)` is `plus(1)@<0>.plus(2)@<1>`,
sequential extension in space, not application); and the notation, below.]

| written on    | prefix                         | postfix                          |
|---------------|--------------------------------|----------------------------------|
| an instruction | span: `{n}a` is $a^n$          | point: `a{c}` is $ca$            |
| a type        | span: `{n}T` is $n$ slots of `T` | point: `T{c}` is $c$ copies of `T` |
| a value       | point: `{c}x` is $cx$          | span: `x{n}` lays `x` along $n$ slots |

[Open: why values mirror instructions and types. Value spans such as `2{3}` do
not parse today.]

# The Point

::: theorem **Theorem 1** (Points add across a branch). If $a \in \mathcal{F}$
and $c,d \in \mathcal{C}$, then $ca + da = (c+d)a$.
:::

::: theorem **Theorem 2** (Points multiply along a chain). If
$a,b \in \mathcal{F}$ and $c,d \in \mathcal{C}$, then
$ca \cdot db = (cd)(ab)$.
:::

::: theorem **Theorem 3** (Points are central). If $a,b \in \mathcal{F}$ and
$c \in \mathcal{C}$, then $ca \cdot b = a \cdot cb = (ab)c$.
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

**Live proof.** A zero point leaves nothing for the rest of the chain. A branch
adds it as it adds any point, and a reducer after it counts nothing.

```mtron_pre
5.id{0}()
5.plus{0}(1).plus(1)
5.branch(id{0}(),id())
5.id{0}().count()
```

[To be written: the proofs from the stream ring axioms.]

# The Span

::: theorem **Theorem 5** (Spans add along a chain). If $a \in \mathcal{F}$ and
$m,n \in \mathbb{N}$, then $a^m \cdot a^n = a^{m+n}$, and $a^0 = 1$.
:::

::: theorem **Theorem 6** (Spans stay where they are written). In general,
$a^n \cdot b \neq a \cdot b^n$.
:::

**Live proof.**

```mtron_pre
5.{2}plus(1).{3}plus(1)
5.{0}plus(1)
5.{2}plus(1).mult(2)
5.plus(1).{2}mult(2)
```

::: corollary **Corollary 1** (Inductive generation).
$\langle 0 \rangle\,\mathtt{plus}(1)^n = \langle n \rangle$.
:::

```mtron_pre
0.{5}plus(1)
```

::: corollary **Corollary 2** (A constant's run is the constant). If
$k \in \mathcal{F}$ ignores its input, then $k \cdot k = k$, so $k^n = k$ for
every $n \geq 1$, and $k^0 = 1$.
:::

**Live proof.** A dereference reads its address, never its input. Read left to
right and greedy, `{2}*a` is `{2}from(a)`, the run `from(a).from(a)`.

```mtron_pre
a -> 7
{2}*a
*a.*a
```

[To be written: why a span is not central while a point is (a span is a run of
functions, and the function ring is not commutative); spans on a value.]

# The Point and the Span

::: theorem **Theorem 7** (The span exponentiates the point). If
$a \in \mathcal{F}$, $c \in \mathcal{C}$ and $n \in \mathbb{N}$, then
$(ca)^n = c^n a^n$.
:::

::: corollary **Corollary 3** (Order matters). $a^n \cdot c1 = c\,a^n$, while
$(ca)^n = c^n a^n$.
:::

::: corollary **Corollary 4** (Idempotents collapse). If $e \cdot e = e$, then
$(ce)^n = c^n e$.
:::

::: corollary **Corollary 5** ($0^0 = 1$). $(0a)^0 = 1$: a zero run of the
annihilator is no step at all.
:::

**Live proof.**

```mtron_pre
5.{2}id{3}()
5.{2}id().id{3}()
5.{3}id{2}()
5.{0}id{0}()
5.{2}plus{3}(1).type()
```

::: theorem **Theorem 8** (A span does not distribute over a branch). If
$a,b \in \mathcal{F}$, then $(a+b)^n$ is the sum of all $2^n$ words of length
$n$ over $a$ and $b$. When $ab = ba$, it is
$\sum_{k=0}^{n} \binom{n}{k} a^k b^{n-k}$, and each binomial coefficient is a
point.
:::

**Live proof.** With `plus(1)` and the unit, the points of a run are a row of
Pascal's triangle. With `plus(1)` and `mult(2)`, which do not commute, all four
words of $(a+b)^2$ survive.

```mtron_pre
2.{2}branch(plus(1),id())
0.{4}branch(plus(1),id())
5.{2}branch(plus(1),mult(2))
```

# Ranges

[To be written, scope to be decided. A point range is a superposition of
multiplicities: `1.plus{?}(2)` answers `{?}3` and stays in superposition. A span
range is a choice of layouts, not a branch: `1.{?}plus(1)` is `1` xor `2`, and
`{*}a` is $1 \mid a \mid a^2 \mid \cdots$, the Kleene star [3], read greedily as a
regular expression's quantifier is. The span range turns the ring's $\cdot$
into a choice, a $+$ from another algebra. Live proofs pending: today
`1.{?}plus(1)` answers `{?}2` and `1.{2,3}plus(1)` answers `{2,3}2`.]

# Types

[To be written: `type()` infers the same algebra. The point of an inferred type
is the product of the points along the chain, exponentiated by its spans:
`5.{2}plus{3}(1).type()` is `int{9}::T`.]

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
