---
name: Ring Theoretic Distributed Structures
description: A data structure is a term of a ring of references. Its layouts are the factorizations of the term, every read is invariant across them, and the laws that distribute a program decide where its data may be split.
author: |
  Dr. Marko A. Rodriguez
  Dr. Stynx
---

**DRAFT**: This article is currently being developed. As of November 22, 2026, the reference ring, record merge and
the layout invariance of reads are implemented in metatron. The data-local shard, record composition, and a single
algebra for lists are entailed by the theory and not yet built; each is marked where it occurs.

# Introduction

In Ring Theoretic Distributed Computing [[4]](./ring-theoretic-distributed-computing.md), a program is a term of the
stream ring. The $+$ branches a stream into computations that share nothing, so they may run on any machine; the
$\cdot$ composes them in series; and distribution stops only where a reduce function contracts a stream. This article
reads *data* the same way. A data structure is a term of a ring of references. Its $+$ branches an address into
independent references, which may be stored on any machine, and its $\cdot$ extends an address, which keeps what it
extends together. A layout of the structure (nested, flat, or anything between) is a factorization of its term, and
every read answers the same in every layout, because a read is linear.

The ring that decides where a program may be split therefore decides where its data may be split, and the two compile
alike. Factoring a program shares work and factoring data shares an address. Expanding a program parallelizes it and
expanding data partitions it. A reduce is where a program must synchronize, and an overwrite is where data must.

The first section defines the reference ring on paths. The second defines a structure as a sum of arrows from paths to
objects, defines a read as a left quotient, and proves reads linear. The third reads a layout as a factorization and
proves reads invariant across layouts. The fourth places a structure across machines and derives the data shard
identity. The fifth treats writes and deletes. The sixth reads the containers, records, lists and relations, as
branches of arrows, and finds two rings for lists and a theorem that their operations do not mix. The seventh sets
the two articles side by side.

# The Reference Ring

::: **Definition 1** (Paths). Let $\Sigma$ be a set of *segments*. A *path* is a word $u = \sigma_1 \cdots \sigma_n$
over $\Sigma$, and the empty word $\varepsilon$ is the empty path. Paths multiply by concatenation, $u \cdot v = uv$,
which is associative with unit $\varepsilon$, so the paths form the free monoid $\Sigma^*$.
:::

::: **Definition 2** (Reference ring). Let $\mathcal{C}$ be the coefficients of [[1]](./stream-ring-theory.md), a
commutative ring with unity (the integers, in metatron). A *reference* is a finite sum $a = \sum_i c_i\,u_i$ of
paths with coefficients, and the empty sum is $0$. References add coefficientwise, $c\,u + d\,u = (c + d)\,u$, and
multiply path by path:
$$
\begin{equation*}
\Big(\sum_i c_i\,u_i\Big) \cdot \Big(\sum_j d_j\,v_j\Big) = \sum_{i,j} (c_i d_j)\,(u_i v_j).
\end{equation*}
$$
The references form the set $\mathcal{U} = \mathcal{C}\langle\Sigma\rangle$.
:::

$\mathcal{U}$ is the free algebra on $\Sigma$: the polynomials whose variables are segments and do not commute [8].
Its $+$ is the *branch*, $a + b = \{a, b\}$, and its $\cdot$ is *extension*, $a \cdot b = a/b$, as in the uri
reference ring of the tractatus [[5]](../tractatus/uri.html).

::: **Theorem 1** (The reference ring). $\langle\mathcal{U}, +, \cdot\rangle$ is a ring with zero $0$ and unity
$\varepsilon$. Its $+$ is commutative, its $\cdot$ is associative, and $\cdot$ distributes over $+$ on both sides. Its
$\cdot$ is not commutative when $\Sigma$ has two segments or more.
:::

::: *Proof.* $\mathcal{U}$ is the monoid ring $\mathcal{C}[\Sigma^*]$, the free $\mathcal{C}$-module on the free
monoid with the monoid's product extended bilinearly. Bilinearity is the two distributive laws, associativity and the
unit are the monoid's, and $0 \cdot a = a \cdot 0 = 0$ follows from distributivity. If $\sigma \neq \tau$ are
segments, then $\sigma\tau \neq \tau\sigma$ as words. ◻
:::

**Live proof.** A sum of paths that share a prefix and a suffix factors around what differs,
$abd + acd = a\,(b + c)\,d$. Paths that share nothing stay whole, in either order, and like paths add their
coefficients, $ab + ab = 2\,ab$.

```mtron_pre
</a/b/d> + </a/c/d>
</a/b> + </c/d>
</c/d> + </a/b>
</a/b> + </a/b>
```

Extension distributes over a branch on either side, $a(b + c)d \cdot e = a(b + c)de$ and
$e \cdot a(b + c)d = ea(b + c)d$, and the empty path is the unity on both sides.

```mtron_pre
</a/b>.mult(</c/d>)
</a/{b,c}/d>.mult(</e>)
</e>.mult(</a/{b,c}/d>)
</a/b>.mult(<>)
<>.mult(</a/b>)
```

The zero adds nothing and annihilates, $ab + 0 = ab$ and $ab \cdot 0 = 0$. The unity is not the zero:
$ab + \varepsilon$ is the branch $\{\varepsilon, ab\}$.

```mtron_pre
</a/b> + noobj
</a/b>.mult(noobj).count()
</a/b> + <>
{noobj,</a/b>}
```

In mtron, a uri is written in angle brackets, `<a/b>`, and a branch `{b,c}` inside a path is the factored sum.
`plus` and `mult` are $+$ and $\cdot$, written infix as ` + ` and ` * ` with a space on each side, since a `*` with no
space is a dereference. The empty uri `<>` is $\varepsilon$ and `noobj` is $0$, which outputs nothing, so a zero is
shown by its count. A coefficient prints after its path, `/a/b{2}`. With `noobj` on the left, `noobj + a` outputs
nothing, because an instruction is never applied to the empty stream. The left identity holds of the stream itself,
which is the last line above.

::: **Proposition 1** (Points multiply along a path). For coefficients $c, d$ and paths $u, v$,
$(c\,u)(d\,v) = (cd)\,uv$. A coefficient on any segment of a path is a coefficient on the whole path,
$\sigma_1 \cdots (c\,\sigma_i) \cdots \sigma_n = c\,(\sigma_1 \cdots \sigma_n)$, so negating $k$ segments
multiplies the path by $(-1)^k$.
:::

::: *Proof.* The product is bilinear, and $\mathcal{C}$ is commutative, so it is central in $\mathcal{U}$. ◻
:::

Coefficients add across a branch and multiply along a path. This is the point law of
[[2]](./span-and-point-coefficient-algebra.md), here a theorem of the reference ring.

**Live proof.** Two negated segments multiply to a positive path, so $ab + (-a)(-b) = 2\,ab$. Three negated segments
give a negative path, which cancels $abc$, and so does the negated path itself.

```mtron_pre
</a/b> + </{{-1}a}/{{-1}b}>
</a/b/c>.plus(</{{-1}a}/{{-1}b}/{{-1}c}>).count()
</a/b>.plus({-1}</a/b>).count()
```

An expression built from segments, $+$, $\cdot$ and coefficients *denotes* the reference it expands to, and two
expressions that denote one reference are two *factorizations* of it. mtron writes a sum in the factorization that
takes out its longest common prefix and suffix and branches the rest, `a/{b,c}/d`. That factorization is always
exact, because factoring out a common prefix or suffix is distributivity. A product of branches is a stronger claim.

::: **Proposition 2** (Rectangular factors). Let $u_1, \ldots, u_n$ be distinct paths of length $m$. Then
$$
\begin{equation*}
u_1 + \cdots + u_n = \Big(\sum_{\sigma \in S_1} \sigma\Big) \cdots \Big(\sum_{\sigma \in S_m} \sigma\Big)
\end{equation*}
$$
for sets of segments $S_1, \ldots, S_m$ if and only if $\{u_1, \ldots, u_n\} = S_1 \times \cdots \times S_m$.
:::

::: *Proof.* The product expands to $\sum \sigma_1 \cdots \sigma_m$ over $S_1 \times \cdots \times S_m$. Distinct
tuples are distinct words, so each word has coefficient $1$. Two references are equal exactly when their coefficients
agree path by path. ◻
:::

A product of branches stands for every combination of its members, so a sum of paths is a product of branches only
when it holds every combination. $ab + cd$ is not $(a + c)(b + d)$, whose expansion adds $ad$ and $cb$.

**Live proof.** The four paths $ab + ad + cb + cd$ are the rectangle $(a + c)(b + d)$. mtron writes their sum as a
branch of four paths, the product as a product of branches, and the two are one reference: subtracting $ab$ from the
product leaves the other three.

```mtron_pre
</a/b>.plus(</a/d>).plus(</c/b>).plus(</c/d>)
</{a,c}/{b,d}>
</{a,c}/{b,d}>.plus({-1}</a/b>)
```

# Structures and Reads

::: **Definition 3** (Structure). Let $X$ be the objects of [[1]](./stream-ring-theory.md). A *structure* is a finite
sum of arrows from paths to objects, with coefficients,
$$
\begin{equation*}
D = \sum_i c_i\,(u_i \Rightarrow x_i), \qquad u_i \in \Sigma^*,\quad x_i \in X,
\end{equation*}
$$
and the empty sum is $0$. A reference extends a structure on the left, path by path,
$p \cdot (u \Rightarrow x) = (pu \Rightarrow x)$, extended bilinearly. The structures form a left $\mathcal{U}$-module
$\mathcal{D}$.
:::

An arrow $u \Rightarrow x$ stores the object $x$ at the address $u$, and its coefficient is the number of copies stored
there, the *point* of [[2]](./span-and-point-coefficient-algebra.md). An arrow into the zero is no arrow,
$u \Rightarrow 0 = 0$: storing nothing stores nothing.

::: **Definition 4** (Read). The *read* of $D$ at a path $p$ is the stream of the objects stored at $p$. The *subtree*
of $D$ at $p$ is what $D$ stores below $p$, addressed from $p$:
$$
\begin{equation*}
D(p) = \sum_{i \,:\, u_i = p} c_i\,x_i,
\qquad
p^{-1}D = \sum_{i \,:\, u_i = p\,v_i} c_i\,(v_i \Rightarrow x_i).
\end{equation*}
$$
A *wildcard* reads every child, $D(p \ast q) = \sum_{\sigma \in \Sigma} D(p\,\sigma\,q)$, a finite sum because $D$ is
finite.
:::

$p^{-1}D$ is the left quotient of $D$ by $p$, Brzozowski's derivative [6] taken by a path instead of a letter. It is how
a read cascades into a container: an interior node reads as its subtree.

::: **Theorem 2** (Reads are linear). For structures $D, E$ and paths $p, q$,
$$
\begin{equation*}
(D + E)(p) = D(p) + E(p), \qquad
p^{-1}(D + E) = p^{-1}D + p^{-1}E, \qquad
(pq)^{-1}D = q^{-1}\big(p^{-1}D\big), \qquad
p^{-1}(p \cdot D) = D.
\end{equation*}
$$
:::

::: *Proof.* The first two equations hold because both sides select the same arrows from the same sum. The third holds
because $u = pqv$ exactly when $u = pw$ with $w = qv$. The fourth is left cancellation in the free monoid: $pu = pv$
only if $u = v$. ◻
:::

**Live proof.** Two structures that share an address merge below it,
$(ab \Rightarrow 1) + (ac \Rightarrow 2) = a \cdot \big((b \Rightarrow 1) + (c \Rightarrow 2)\big)$, and both forms
read $2$ at $ac$.

```mtron_pre
[a=>[b=>1]] + [a=>[c=>2]]
[a=>[b=>1,c=>2]]>>a>>c
{[a=>[b=>1]],[a=>[c=>2]]}>>a>>c
```

In mtron, a rec `[k=>v]` writes arrows out of its keys, and a rec nested at a key extends the key (Definition 8), so
`[a=>[b=>1]]` is $(ab \Rightarrow 1)$. `>>` reads a child, so `>>a>>c` is the read at $ac$. A stream `{…}` of recs is
their sum, and an instruction applied to a stream applies to each member.

# Layouts

::: **Definition 5** (Layout). A *layout* of a structure $D$ is a sum $D = O_1 + \cdots + O_m$ of *objects*, each an
expression stored as one unit: a value at an address, or a container. Two layouts are *equivalent* when they denote
the same structure.
:::

::: **Theorem 3** (Layout invariance). Equivalent layouts answer every read alike: every read, every subtree, and every
wildcard read.
:::

::: *Proof.* A read is a function of the structure a layout denotes (Definition 4), and equivalent layouts denote the
same structure. ◻
:::

The proof is one line, because the substance is in Definition 5: layouts are equivalent exactly when the ring's laws
carry one to the other. A matrix $m$ with entries $a_{ij}$ is, entry-wise, row-wise and nested,
$$
\begin{equation*}
\sum_{i,j} \big(m\,i\,j \Rightarrow a_{ij}\big)
\;=\; \sum_{i} m\,i \cdot \Big(\sum_{j} \big(j \Rightarrow a_{ij}\big)\Big)
\;=\; m \cdot \Big(\sum_{i} i \cdot \sum_{j} \big(j \Rightarrow a_{ij}\big)\Big),
\end{equation*}
$$
so the row read $D(m\,0 \ast)$ and the column read $D(m \ast 0)$ answer the same in all three. The tractatus runs all
three embeddings live [[5]](../tractatus/uri.html). The layouts differ only in cost, as a read touches what its layout
stores together, so a planner may choose a layout by cost alone, as [[4]](./ring-theoretic-distributed-computing.md)
chooses a plan.

**Live proof.** The nested layout of a two-row matrix reads $6$ at row $1$, column $2$.

```mtron_pre
[[0,1,2,3],[4,5,6,7]]>>1>>2
```

::: **Proposition 3** (What a layout shows). The number of objects in a layout is not a function of the structure it
denotes.
:::

::: *Proof.* $[a \Rightarrow [b \Rightarrow 1, c \Rightarrow 2]]$ is one object and
$\{[a \Rightarrow [b \Rightarrow 1]], [a \Rightarrow [c \Rightarrow 2]]\}$ is two, and both denote
$(ab \Rightarrow 1) + (ac \Rightarrow 2)$. ◻
:::

**Live proof.** The two layouts count differently, and the sum of the expanded layout is the nested one.

```mtron_pre
[a=>[b=>1,c=>2]].count()
{[a=>[b=>1]],[a=>[c=>2]]}.count()
{[a=>[b=>1]],[a=>[c=>2]]}.sum()
```

An operation is therefore invariant across layouts exactly when it factors through the structure. Reads do
(Theorem 3), and so does any fold of reads. Counting objects, ordering them, or measuring a container's length does
not, so none of these may be moved across a change of layout. These are the operations a rewriter cannot distribute
over a branch either: a count of a merged stream is one object, while the counts of its parts are two.

# Placement

::: **Definition 6** (Placement). A *placement* of a structure $D$ over places $1, \ldots, k$ assigns each object of a
layout of $D$ to one place. Place $j$ holds $D_j$, the sum of the objects assigned to it, so
$D = D_1 + \cdots + D_k$. Write $\pi_j$ for the map that keeps the arrows held at place $j$. Then
$$
\begin{equation*}
\pi_1 + \cdots + \pi_k = 1,
\qquad
\pi_i\,\pi_j =
\begin{cases}
\pi_j & \text{if $i = j$}, \\
0 & \text{otherwise},
\end{cases}
\end{equation*}
$$
so the $\pi_j$ are a complete set of orthogonal idempotents [8].
:::

The deal of [[4]](./ring-theoretic-distributed-computing.md) is the same construction on a stream. Its shares sum to
the identity, which is all the tree of supersteps needs, and orthogonality, $\pi_i\,\pi_j = 0$, is what makes the
shares disjoint.

::: **Theorem 4** (The data shard identity). Let $D = D_1 + \cdots + D_k$ be a placement, $p$ a path, and $r$ a cut of
[[4]](./ring-theoretic-distributed-computing.md) with combiner $C(r)$. Then
$$
\begin{equation*}
D(p) = D_1(p) + \cdots + D_k(p),
\qquad
D(p)\,r = \big(D_1(p)\,r + \cdots + D_k(p)\,r\big)\;C(r).
\end{equation*}
$$
:::

::: *Proof.* The first equation is Theorem 2, by induction on $k$. The second is the shard identity of
[[4]](./ring-theoretic-distributed-computing.md) applied to the seed $D_1(p) + \cdots + D_k(p)$ with no floatable run. ◻
:::

Read from right to left, the identity sends the program to the data: each place reads and folds what it holds, and
only the partials travel. It is entry B of the menu of [[4]](./ring-theoretic-distributed-computing.md), the
data-local shard, whose seed the placement partitions rather than a deal.

[To be written: entry B lowered by `law_shard`, with each lane reading the part its place holds.]

::: **Theorem 5** (Factoring colocates). If $O = p \cdot (E + F)$ is one object of a layout, every placement of that
layout holds $p \cdot E$ and $p \cdot F$ at one place. The layout with $O$ replaced by the two objects $p \cdot E$ and
$p \cdot F$ is equivalent, and it admits every placement of the first together with those that hold the two apart.
:::

::: *Proof.* A placement assigns each object to one place, and $p \cdot (E + F) = p \cdot E + p \cdot F$ by
distributivity. ◻
:::

The placements a layout admits are ordered by factoring: expanding refines them and factoring coarsens them. The fully
expanded layout, one arrow per object, admits every placement, and a single object admits one. A product colocates
and a sum frees, which makes exact the tractatus's reading of the multiplicative form as dependent and the additive
form as independent [[5]](../tractatus/uri.html).

::: **Theorem 6** (Replication needs idempotence). Let $D = D_1 + R + D_2$, and let two places hold $D_1 + R$ and
$D_2 + R$, so that $R$ is held twice. The two reads sum to $D(p) + R(p)$. A commutative monoid fold $r$ answers
$D(p)\,r$ over that sum, whatever $R$ is, if and only if it is idempotent, $x \oplus x = x$.
:::

::: *Proof.* By Theorem 2 the reads sum to $D(p) + R(p)$. If $x \oplus x = x$, then
$r\big(D(p) + R(p)\big) = r\big(D_1(p) + D_2(p)\big) \oplus r\big(R(p)\big) \oplus r\big(R(p)\big)
= r\big(D_1(p) + D_2(p)\big) \oplus r\big(R(p)\big) = r\big(D(p)\big)$. Conversely, take $D = R$ a single object $x$:
the condition reads $r(x + x) = r(x)$, which is $x \oplus x = x$. ◻
:::

**Live proof.** A replicated object arrives twice and counts twice, $1 + 1 = 2 \cdot 1$, while an idempotent fold sees
it once.

```mtron_pre
{1,1}
{1,1}.sup()
```

This is entry H of [[4]](./ring-theoretic-distributed-computing.md) read on data: a replica may serve a read only to a
fold that does not count. The copies at one address split like any sum, which is the point form of entry V.

::: **Proposition 4** (A point is a share). For $n = n_1 + n_2$,
$n\,(p \Rightarrow x) = n_1\,(p \Rightarrow x) + n_2\,(p \Rightarrow x)$, so the copies stored at one address may be
held at two places.
:::

**Live proof.** Two copies and one copy are three copies.

```mtron_pre
{{2}5,5}
```

# Writes and Deletes

::: **Definition 7** (Writes). A *write* adds, $D \mapsto D + E$. For a path $p$, let $\pi_p$ keep the arrows whose
paths begin with $p$. A *delete* at $p$ is $\delta_p = 1 - \pi_p$, and an *overwrite* stores $x$ at $p$ alone,
$\omega_{p,x}(D) = \delta_p(D) + (p \Rightarrow x)$.
:::

::: **Theorem 7** (Sums commute, overwrites do not). Writes commute and associate, $(D + E) + F = (D + F) + E$.
Overwrites at one path do not commute: $\omega_{p,y}\big(\omega_{p,x}(D)\big) = \delta_p(D) + (p \Rightarrow y)$,
which differs from $\omega_{p,x}\big(\omega_{p,y}(D)\big)$ whenever $x \neq y$.
:::

::: *Proof.* $+$ is commutative and associative. $\delta_p$ is idempotent and $\delta_p(p \Rightarrow x) = 0$, so
$\omega_{p,y}\big(\omega_{p,x}(D)\big) = \delta_p\big(\delta_p(D) + (p \Rightarrow x)\big) + (p \Rightarrow y)
= \delta_p(D) + (p \Rightarrow y)$. ◻
:::

Replicas that only add converge whatever order their writes arrive in, which is the property of a conflict-free
replicated data type [9]. Replicas that overwrite must agree on an order. An overwrite is to data what a cut is to a
program [[4]](./ring-theoretic-distributed-computing.md): the one place where order matters.

**Live proof.** Adds in either order store one bag, $\{1, 2, 3\}$ at $a$, while overwrites keep whichever came last.

```mtron_pre
[a=>1].plus([a=>2]).plus([a=>3])
[a=>1].plus([a=>3]).plus([a=>2])
[a=>1].update([a=>2]).update([a=>3])
[a=>1].update([a=>3]).update([a=>2])
```

::: **Theorem 8** (Two ways to delete). A delete distributes over a placement,
$\delta_p(D_1 + \cdots + D_k) = \delta_p(D_1) + \cdots + \delta_p(D_k)$, so each place deletes what it holds without
reading anything. Subtracting an arrow, $D - c\,(p \Rightarrow x)$, removes it only when $c\,(p \Rightarrow x)$ is
what $D$ stores at $p$.
:::

::: *Proof.* $\pi_p$ selects arrows, so it is additive, and so is $\delta_p = 1 - \pi_p$. Subtraction removes
$c\,(p \Rightarrow x)$ exactly when that term is a term of $D$. ◻
:::

The ring offers two ways to delete. The additive way subtracts what is stored and must know it, coefficient and
object, before it writes. The multiplicative way projects and needs to know nothing.

::: **Proposition 5** (The zero names no delete). No structure holds an arrow into the zero, since
$k \Rightarrow 0 = 0$. A container therefore cannot carry a delete as an arrow to the zero: the record
$[b \Rightarrow 0]$ is the empty record, and a write of it changes nothing.
:::

**Live proof.** Subtracting what is stored deletes it, $[a \Rightarrow 1, b \Rightarrow 2] + [b \Rightarrow -2]
= [a \Rightarrow 1]$, the additive way, whether as a sum or as an update by a sum. An arrow into `noobj` is no arrow,
so updating with one changes nothing, and the mark `none` deletes without knowing what is stored.

```mtron_pre
[a=>1,b=>2] + [b=>{-1}2]
[a=>1,b=>2] >>= +[b=>{-1}2]
[b=>noobj]
[a=>1,b=>2] >>= [b=>noobj]
[a=>1,b=>2] >>= [b=>none]
```

In mtron, `>>=` is update, `{-1}2` is the anti-object of `2`, and `none` is the mark that removes a key.

`none` is a scar. It is a mark outside the ring, which the update interprets as $\delta_k$, because the ring's own zero
cannot say *delete* (Proposition 5). The ring has a delete of its own, however, and it needs no mark and no
knowledge of what is stored. Read a record as a relation from its keys (Definition 8), let relations compose
(Definition 11), and take the parametric unity $1 \Rightarrow 1$ of [[3]](./parametric-identities-in-ring-theory.md).

::: **Theorem 9** (A delete is an idempotent). For a key $k$, the relation $(1 \Rightarrow 1) - (k \Rightarrow k)$ is
idempotent, and composing it on the left of a record deletes the key:
$$
\begin{equation*}
\big((1 \Rightarrow 1) - (k \Rightarrow k)\big) \cdot D = \delta_k(D).
\end{equation*}
$$
:::

::: *Proof.* $(1 \Rightarrow 1) \cdot D = D$ by Theorem 2 of [[3]](./parametric-identities-in-ring-theory.md), and
$(k \Rightarrow k) \cdot (k' \Rightarrow v)$ is $k \Rightarrow v$ when $k' = k$ and $0$ otherwise, so
$(k \Rightarrow k) \cdot D = \pi_k(D)$. Idempotence:
$\big((1 \Rightarrow 1) - (k \Rightarrow k)\big)^2 = (1 \Rightarrow 1) - 2\,(k \Rightarrow k) + (k \Rightarrow k)
= (1 \Rightarrow 1) - (k \Rightarrow k)$. ◻
:::

**Live proof.** Written as a list of arrows (Definition 9), a record composes today. The unity keeps every arrow, and
$k \Rightarrow k$ keeps the arrows out of $k$, which is $\pi_k$.

```mtron_pre
[(id()=>id())].mult([(a=>1),(b=>2)])
[(b=>b)].mult([(a=>1),(b=>2)])
```

[To be written: the difference itself. Today a coefficient on an arrow is placed on both of its ends, so
`[{-1}(b=>b)]` prints `[{-1}b=>{-1}b]`, which a bilinear arrow reads as $(-1)^2 = +1$, and records do not define
`mult`. With the coefficient placed once and record composition defined, the scar closes.]

# Containers as Branches of Arrows

A container is a branch of arrows. A record is the *named* branch, whose arrows leave keys, and a list read by its
indices is the *indexed* branch. Both are relations, so the relation ring of
[[3]](./parametric-identities-in-ring-theory.md) is their common algebra.

## Records

::: **Definition 8** (Record). A record is the sum of the arrows out of its keys,
$[k_1 \Rightarrow v_1, \ldots, k_n \Rightarrow v_n] = \sum_i (k_i \Rightarrow v_i)$. A record nested at a key extends
the key, $\big(k \Rightarrow [j \Rightarrow v]\big) = k \cdot (j \Rightarrow v) = (kj \Rightarrow v)$, so a record is
a structure and its key paths are its addresses.
:::

::: **Theorem 10** (Merge is the sum). Records add as structures:
$$
\begin{equation*}
(k \Rightarrow E) + (k \Rightarrow F) = k \cdot (E + F),
\qquad
(k \Rightarrow x) + (k \Rightarrow y) = \big(k \Rightarrow \{x, y\}\big),
\qquad
(k \Rightarrow 0) = 0.
\end{equation*}
$$
Under this merge the records form a commutative monoid whose identity is the empty record.
:::

::: *Proof.* The first equation is distributivity of the module action, and the second writes the same sum with the
branch of its objects at the shared address. The third is $k \cdot 0 = 0$. $+$ is commutative and associative, and
the empty record is the empty sum. ◻
:::

**Live proof.** Records merge below a shared key, branch their values at a shared address, and drop an arrow into the
zero.

```mtron_pre
[a=>[b=>1]] + [a=>[c=>2]]
[a=>1] + [a=>2]
[a=>noobj,b=>2]
```

As relations, records also compose. An arrow out of a key meets an arrow out of its value,
$[a \Rightarrow 1] \cdot [1 \Rightarrow x] = [a \Rightarrow x]$: a lookup through two records, the one-step join.

[To be written: record composition in mtron. `[a=>1].mult([1=>10])` finds no `mult` for records today.]

## Lists: Two Rings

A list $[x_0, \ldots, x_{n-1}]$ admits two readings as a ring term, and each reading is a ring.

::: **Definition 9** (Indexed list). Read by its indices, a list is the sum of the arrows out of them,
$[x_0, \ldots, x_{n-1}] = \sum_{i < n} (i \Rightarrow x_i)$.
:::

::: **Theorem 11** (The indexed ring). Indexed lists add slot by slot and multiply by composition:
$$
\begin{equation*}
(i \Rightarrow x) + (i \Rightarrow y) = \big(i \Rightarrow \{x, y\}\big),
\qquad
\Big(\sum_i (i \Rightarrow u_i)\Big) \cdot \Big(\sum_j (j \Rightarrow v_j)\Big) = \sum_i \big(i \Rightarrow v_{u_i}\big),
\end{equation*}
$$
where $v_{u_i} = 0$ when $u_i$ is not an index of $v$. The zero is the empty list, and a slot holding the zero is no
arrow, so $x + (-x) = [\,]$. The unity is the parametric unity $1 \Rightarrow 1$, and no list is the unity. With
these operations the indexed lists are a ring: the relation ring restricted to arrows out of indices.
:::

::: *Proof.* The sum is Theorem 10 at an index. The product is composition in the relation ring
(Definition 1 of [[3]](./parametric-identities-in-ring-theory.md)): $(i \Rightarrow a) \cdot (j \Rightarrow b)$ is
$i \Rightarrow b$ when $a = j$ and $0$ otherwise. $i \Rightarrow 0 = 0$ removes empty slots. The unity is
Theorem 2 of [[3]](./parametric-identities-in-ring-theory.md), and that no list is the unity is its Theorem 1,
as the indices are infinitely many. The ring axioms are those of the relation ring. ◻
:::

So $[1,2,3] + [4,5] = [\{1,4\}, \{2,5\}, 3]$ in the indexed ring, and $(u \cdot v)_i = v_{u_i}$ looks up $v$ at the
values of $u$. Concatenation is not the product here. It is a shifted sum, $u \mathbin{+\!\!+} v = u + \tau^{|u|}(v)$,
where $\tau^{n}$ adds $n$ to every index and $|u|$ is one more than the greatest index of $u$.

**Live proof.** Written as a list of arrows, a list composes as the indexed ring says. A list that sends
$0 \mapsto 2$, $1 \mapsto 0$, $2 \mapsto 1$ permutes the values of the other, and a slot holding two values looks up
both.

```mtron_pre
[(0=>2),(1=>0),(2=>1)].mult([(0=>10),(1=>20),(2=>30)])
[(0=>1),(0=>4),(1=>2)].mult([(0=>9),(1=>8),(2=>7),(4=>6)])
```

::: **Definition 10** (Word list). Read as a word, a list is the product of its slots,
$[x_1, \ldots, x_n] = x_1 \cdots x_n$, a monomial of the free algebra $\mathcal{C}\langle X\rangle$ whose segments are
objects.
:::

::: **Theorem 12** (The word ring). Word lists multiply by concatenation with unity the empty list, and add as the
reference ring adds paths, factoring a common prefix and suffix: $[1,2,3] + [1,5,3] = [1, \{2,5\}, 3]$, while
$[1,2,3] + [4,5]$ is the branch of the two whole words. A branch in a slot is a sum of words, the zero is the empty sum,
and the additive inverse negates one slot, $-[x_1, \ldots, x_n] = [-x_1, x_2, \ldots, x_n]$.
:::

::: *Proof.* $\mathcal{C}\langle X\rangle$ is Theorem 1 with objects for segments, and the inverse is Proposition 1. ◻
:::

**Live proof.** In mtron a list adds by concatenation today, so the word ring's sum is shown on the reference ring,
whose paths are words.

```mtron_pre
</1/2/3> + </1/5/3>
</1/2/3> + </4/5>
```

The two readings differ most visibly in how a coefficient enters a list.

::: **Proposition 6** (Linear and multilinear). An indexed list is linear in its slots and a word list is multilinear:
$$
\begin{equation*}
\big[c\,x_1, \ldots, c\,x_n\big] = c\,[x_1, \ldots, x_n] \;\text{(indexed)},
\qquad
\big[c\,x_1, \ldots, c\,x_n\big] = c^{n}\,[x_1, \ldots, x_n] \;\text{(word)}.
\end{equation*}
$$
:::

::: *Proof.* An indexed list is a sum, and $\sum_i (i \Rightarrow c\,x_i) = c \sum_i (i \Rightarrow x_i)$. A word list
is a product, and $(c\,x_1) \cdots (c\,x_n) = c^{n}\,x_1 \cdots x_n$ by Proposition 1. ◻
:::

Coefficients add across a branch and multiply along a chain: an indexed list is a branch and a word list is a chain.
Negating every slot negates an indexed list, but negates a word list only when its length is odd. A value with
coefficient $c$ and span $n$, laid out along an axis, is $c\,[v, \ldots, v]$ with the coefficient written once, and it
counts $c \cdot n$ objects. Writing $c$ on every slot keeps that count in the indexed ring, and in the word ring it
raises the coefficient of the whole word to $c^{n}$.

**Live proof.** mtron lays out a negated span by writing the coefficient on every slot, and a branch in a slot bags as
objects at one index. Both are the indexed reading.

```mtron_pre
[{-1}5{4}]
[1,{2,3},4]>-
```

In the word ring, the first list would be $(-1)^4\,[5,5,5,5] = [5,5,5,5]$, and the second would be the sum of the
words $[1,2,4]$ and $[1,3,4]$, whose bag is $\{2 \cdot 1, 2, 3, 2 \cdot 4\}$.

## The Hybrid Is No Ring

Each ring is coherent alone. Their operations do not mix.

::: **Theorem 13** (No hybrid ring). On lists that keep their empty slots, let $\oplus$ be the slot sum of the indexed
ring and $\otimes$ the concatenation of the word ring. Then:

1. The empty list is the identity of both $\oplus$ and $\otimes$, so $\langle L, \oplus, \otimes\rangle$ is not a ring.
2. $\otimes$ distributes over $\oplus$ on neither side.
3. $(a \oplus b) \otimes (c \oplus d) = (a \otimes c) \oplus (b \otimes d)$ whenever $a$ and $b$ have the same length.
4. $x \oplus (-x)$ is the list of $|x|$ empty slots, not the empty list. $\langle L, \oplus\rangle$ is not a group but
   a Clifford monoid: for each length $n$, the lists of length $n$ form a group, and these groups are glued along
   $\langle \mathbb{N}, \max \rangle$ by padding with empty slots.
5. If an empty slot is identified with no slot, as the indexed ring requires, $\otimes$ is not well defined.
:::

::: *Proof.* (1) $x \oplus [\,] = x$, as the shorter list is padded, and $x \otimes [\,] = x$. In a ring with $0 = 1$,
every $a = a \cdot 1 = a \cdot 0 = 0$. (2) On the left, $[7] \otimes ([1] \oplus [2]) = [7, \{1,2\}]$ while
$([7] \otimes [1]) \oplus ([7] \otimes [2]) = [2 \cdot 7, \{1,2\}]$. On the right,
$([1] \oplus [2,3]) \otimes [9] = [\{1,2\}, 3, 9]$ while
$([1] \otimes [9]) \oplus ([2,3] \otimes [9]) = [\{1,2\}, \{3,9\}, 9]$. (3) Both sides hold $a \oplus b$ in the first
$|a|$ slots and $c \oplus d$ after them. (4) Slot by slot, $x_i + (-x_i) = 0$ leaves the slot empty, and the length
of $x$ remains. Lists of one length $n$ add slot by slot, which is a group with identity the $n$ empty slots, and a
sum of lengths $m$ and $n$ has length $\max(m, n)$. A semigroup that is a strong semilattice of groups in this way is a
Clifford semigroup [11]. (5) $[1] = [1, 0]$ under the identification, yet $[1] \otimes [2] = [1, 2]$ while
$[1, 0] \otimes [2] = [1, 0, 2]$. ◻
:::

Clause 3 is the interchange law of a direct sum, not the distributive law of a product. Under $\oplus$, two lists
advance in lockstep, slot against slot, as two processes advance step against step under the synchronous product of
SCCS [10]. Clause 4 is the same calculus: a step cancelled by its inverse is an idle step, not an absent one.

**Live proof.** In mtron today, a list adds by concatenation, which does not commute, and multiplies by the product of
[[3]](./parametric-identities-in-ring-theory.md): every slot of the left times every slot of the right. On values,
that product is the Cauchy product of polynomials in a position variable, with its slots left ungrouped: grouping
its terms by $i + j$ gives $[4, \{5,8\}, \{10,12\}, 15]$. On lists of arrows it is the composition of Theorem 11. A
sum over a stream of lists depends on the order of the stream.

```mtron_pre
[1,2,3] + [4,5]
[4,5] + [1,2,3]
[1,2,3] * [4,5]
{[1,2],[3]}.sum()
{[3],[1,2]}.sum()
```

So today's list takes its $+$ from the word ring, where concatenation is the product, and its $\cdot$ from a
positional reading: one operation from each. A fold whose operation is a commutative monoid ships as a star, in the shapes of
[[4]](./ring-theoretic-distributed-computing.md). Concatenation is associative and not commutative, so a fold of lists
by concatenation ships at best as a pipeline. Concatenation is the factorization an ordered fold distributes over: a
fold $h$ that is a monoid homomorphism out of concatenation satisfies
$h(u \mathbin{+\!\!+} v) = h(u) \odot h(v)$, entry F of the menu. Both rings give lists a commutative $+$.

[Open: which ring's operations a list names `+` and `*`.]

## Relations

::: **Definition 11** (Relation ring). The relations of [[3]](./parametric-identities-in-ring-theory.md) are the
finite sums of arrows between objects. They add as sums and multiply by composition, with the parametric unity
$1 \Rightarrow 1$.
:::

Records and indexed lists are the relations whose arrows leave keys and indices. The relation ring is the ring of
finitely supported matrices [[3]](./parametric-identities-in-ring-theory.md), and composition is matrix
multiplication, so the matrix multiplication of entry M of [[4]](./ring-theoretic-distributed-computing.md) is
composition in this ring, and the "addressed union" of that entry is the slot sum of the indexed ring.

In mtron, a rel declares only its product today: a sum of rels is a stream of rels, which leaves the type, and its
zero, `noobj`, cannot be written in a record (Proposition 5). The code ring declares its zero as the instruction
`end` with range `code{0}` instead, an object that outputs nothing, and a relation's zero could be named the same way.

# The Mirror

| | Distributed Computing [[4]](./ring-theoretic-distributed-computing.md) | Distributed Structures |
|---|---|---|
| term | a program in the stream ring | a structure in the reference ring |
| $+$ | branch: computations share nothing, so placement is free (Theorem 5 of [[1]](./stream-ring-theory.md)) | branch: references share nothing, so placement is free |
| $\cdot$ | composition in series | extension, which colocates within an object (Theorem 5) |
| linear part | the floatable run, right distributive | the read (Theorem 2) |
| synchronization | the cut, a reduce function | the overwrite (Theorem 7) |
| the identity | $s\,f\,r = (s_1 f r + \cdots + s_k f r)\,C(r)$ | $D(p)\,r = (D_1(p)\,r + \cdots + D_k(p)\,r)\,C(r)$ (Theorem 4) |
| partition | the deal, shares summing to $1$ | the placement, orthogonal idempotents (Definition 6) |
| redundancy | entry H, an idempotent fold | replication needs an idempotent fold (Theorem 6) |
| index sets | a span splits by exponent, a point by copies | a point splits by copies (Proposition 4) |
| invariance | the answer at every height, arity and schedule | every read in every layout (Theorem 3) |
| cost | a planner descends the patterns | a planner descends the layouts |

# Conclusion

**NOTES**:

- A data structure is a term of the reference ring $\mathcal{C}\langle\Sigma\rangle$, whose $+$ is the branch and
  whose $\cdot$ is extension. Its layouts are its factorizations.
- A read is a left quotient, and reads are linear, so every read answers the same in every layout. Counting objects,
  ordering them, and a container's length are not reads, and they see the layout.
- A product of branches is exact only over a rectangle of paths (Proposition 2). The sound sum factors only prefixes
  and suffixes.
- A placement is a complete set of orthogonal idempotents. The data shard identity is entry B: the program goes to
  the data.
- A product colocates and a sum frees. Expanding a layout refines its placements, and replication needs an
  idempotent fold.
- Sums commute and overwrites do not: an overwrite is the data's cut. A delete projects and distributes, and a
  subtraction must know what it removes. The ring's zero cannot name a delete, which is the scar `none` covers, and
  the parametric unity heals it: $(1 \Rightarrow 1) - (k \Rightarrow k)$ deletes $k$.
- A record is the named branch: its merge is the sum and its composition the one-step join.
- A list has two rings. The indexed ring has the slot sum and composition; the word ring has the branch and
  concatenation. Their operations do not mix (Theorem 13), and today's list takes one of each.
- [To be written: entry B lowered; record composition; the coefficient of an arrow placed once; a relation's zero
  named as an instruction; the choice of ring for lists.]

# References

1. Rodriguez, M.A., "[Stream Ring Theory](./stream-ring-theory.md)," S/V Red Herring's Ship's Log: Chronicles in the Sea
   of Cortez, pages 10–40, Mulegé, Baja California Sur, México, February 2019.
2. Rodriguez, M.A., Dr. Stynx,
   "[A Span and Point Coefficient Algebra for Stream Rings](./span-and-point-coefficient-algebra.md)."
3. Rodriguez, M.A., Dr. Stynx, "[Parametric Identities in Ring Theory](./parametric-identities-in-ring-theory.md)."
4. Rodriguez, M.A., Dr. Stynx, "[Ring Theoretic Distributed Computing](./ring-theoretic-distributed-computing.md)."
5. "[Uniform Resource Identifiers](../tractatus/uri.html)," the metatron tractatus.
6. Brzozowski, J.A., "Derivatives of Regular Expressions," Journal of the ACM 11(4), pages 481–494, 1964.
7. Codd, E.F., "A Relational Model of Data for Large Shared Data Banks," Communications of the ACM 13(6), pages
   377–387, 1970.
8. Lam, T.Y., "A First Course in Noncommutative Rings," Graduate Texts in Mathematics 131, Springer, 1991.
9. Shapiro, M., Preguiça, N., Baquero, C., Zawirski, M., "Conflict-free Replicated Data Types," Stabilization, Safety,
   and Security of Distributed Systems (SSS 2011), Lecture Notes in Computer Science 6976, pages 386–400, 2011.
10. Milner, R., "Calculi for Synchrony and Asynchrony," Theoretical Computer Science 25(3), pages 267–310, 1983.
11. Howie, J.M., "Fundamentals of Semigroup Theory," London Mathematical Society Monographs, Oxford University Press,
    1995.
