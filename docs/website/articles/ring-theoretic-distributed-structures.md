---
name: Ring Theoretic Distributed Structures
description: A data structure is a term of a ring of references. Its layouts are the factorizations of the term, and the laws that distribute a program decide where its data may be split.
author: |
  Dr. Marko A. Rodriguez
  Dr. Stynx
---

**DRAFT**: This article is a formal scaffold. Its definitions are settled. Its claims are stated precisely but not yet
proven or demonstrated, and the notes marked *Directions* point to where the work goes next. As of November 23, 2026,
metatron implements the reference ring, record merge, and the layout invariance of reads.

# Introduction

In Ring Theoretic Distributed Computing [[4]](./ring-theoretic-distributed-computing.md), a program is a term of the
stream ring. Its $+$ branches a stream into computations that share nothing, its $\cdot$ composes them in series, and
distribution stops only where a reduce function contracts a stream. This article reads data the same way. A data
structure is a term of a ring of references, whose $+$ branches an address into independent references and whose
$\cdot$ extends an address. A layout of the structure, whether nested, flat, or anything between, is a factorization
of the term.

Both readings answer one question: where may a term be split? For a program, the answer lies in the distributive laws
of its functions; for data, in the distributive laws of its references. Factoring a program shares work, and
factoring data shares an address. Expanding a program parallelizes it, and expanding data partitions it. A reduce is
where a program must synchronize, and an overwrite is where data must.

The sections define the reference ring, structures and their reads, layouts, placements across machines, and writes
and deletes. They then read the containers (records, lists, and relations) as branches of arrows, and the last
section sets the two articles side by side. The definitions are settled. The claims are numbered and stated, and
proving them is the work ahead.

# The Reference Ring

::: **Definition 1** (Paths). Let $\Sigma$ be a set of *segments*. A *path* is a word $u = \sigma_1 \cdots \sigma_n$
over $\Sigma$, and the empty word $\varepsilon$ is the empty path. Paths multiply by concatenation, $u \cdot v = uv$,
so the paths form the free monoid $\Sigma^*$.
:::

::: **Definition 2** (Reference ring). Let $\mathcal{C}$ be the coefficients of [[1]](./stream-ring-theory.md), a
commutative ring with unity. A *reference* is a finite sum $a = \sum_i c_i\,u_i$ of paths with coefficients, and the
empty sum is $0$. References add coefficientwise and multiply path by path,
$$
\begin{equation*}
\Big(\sum_i c_i\,u_i\Big) \cdot \Big(\sum_j d_j\,v_j\Big) = \sum_{i,j} (c_i d_j)\,(u_i v_j),
\end{equation*}
$$
and they form the set $\mathcal{U} = \mathcal{C}\langle\Sigma\rangle$.
:::

$\mathcal{U}$ is the free algebra on $\Sigma$: polynomials whose variables are segments that do not commute. Its $+$
is the *branch*, $a + b = \{a, b\}$, and its $\cdot$ is *extension*, $a \cdot b = a/b$, as in the tractatus
[[5]](../tractatus/uri.html).

::: **Proposition 1** (The reference ring). $\langle\mathcal{U}, +, \cdot\rangle$ is a ring with zero $0$ and unity
$\varepsilon$. Its $\cdot$ distributes over $+$ on both sides and is not commutative when $\Sigma$ has two or more
segments.
:::

::: *Proof.* $\mathcal{U}$ is the monoid ring of the free monoid $\Sigma^*$ [8]. ◻
:::

**Live proof.** Paths that share a prefix and a suffix factor around what differs, $abd + acd = a\,(b + c)\,d$.
Paths that share nothing stay whole, and extension distributes over a branch.

```mtron
mtron> </a/b/d> + </a/c/d>
==></a/{b,c}/d>
mtron> </a/b> + </c/d>
==></{a/b,c/d}>
mtron> </a/b>.mult(</c/d>)
==>/a/b/c/d
mtron> </a/{b,c}/d>.mult(</e>)
==></a/{b,c}/d/e>
```
In mtron, a uri is written in angle brackets, and a branch `{b,c}` inside a path is a factored sum. `plus` and `mult`
are $+$ and $\cdot$, the empty uri `<>` is $\varepsilon$, and `noobj` is $0$.

::: **Claim 1** (Points multiply along a path). $(c\,u)(d\,v) = (cd)\,uv$, so a coefficient on any segment of a path
is a coefficient on the whole path.
:::

<!-- quarry: Proposition 1, with its live proof -->

::: **Claim 2** (Rectangular factors). Distinct paths $u_1, \ldots, u_n$ of length $m$ sum to a product of branches
$\big(\sum_{\sigma \in S_1} \sigma\big) \cdots \big(\sum_{\sigma \in S_m} \sigma\big)$ if and only if
$\{u_1, \ldots, u_n\} = S_1 \times \cdots \times S_m$.
:::

<!-- quarry: Proposition 2, with its live proof -->

**Directions.**

- *Rank.* A product of branches is a rank-one tensor, and Claim 2 says when a sum of paths is one. The fewest
  products that sum to a structure is its tensor rank, which is NP-hard to compute [12]. Is the most compact layout of
  a structure a rank decomposition?
- *Sharing.* mtron factors out a common prefix and suffix. Factoring out every shared subtree instead gives a directed
  acyclic graph: the minimal automaton of the structure's paths, whose states are its distinct subtrees
  (the Myhill–Nerode theorem [13]). Is the minimal automaton the most shared layout, and how far is the sound sum from
  it?

# Structures and Reads

::: **Definition 3** (Structure). Let $X$ be the objects of [[1]](./stream-ring-theory.md). A *structure* is a finite
sum of arrows from paths to objects,
$$
\begin{equation*}
D = \sum_i c_i\,(u_i \Rightarrow x_i), \qquad u_i \in \Sigma^*,\quad x_i \in X,
\end{equation*}
$$
where an arrow into the zero is no arrow, $u \Rightarrow 0 = 0$. A reference extends a structure on the left,
$p \cdot (u \Rightarrow x) = (pu \Rightarrow x)$, bilinearly, and the structures form a left $\mathcal{U}$-module
$\mathcal{D}$.
:::

An arrow $u \Rightarrow x$ stores the object $x$ at the address $u$. Its coefficient is the number of copies stored
there, the *point* of [[2]](./span-and-point-coefficient-algebra.md).

::: **Definition 4** (Read). The *read* of $D$ at a path $p$, the *subtree* of $D$ at $p$, and the *part* of $D$ below
$p$ are
$$
\begin{equation*}
D(p) = \sum_{u_i = p} c_i\,x_i,
\qquad
p^{-1}D = \sum_{u_i = p\,v_i} c_i\,(v_i \Rightarrow x_i),
\qquad
\pi_p(D) = \sum_{u_i = p\,v_i} c_i\,(u_i \Rightarrow x_i).
\end{equation*}
$$
A *wildcard* reads every child, $D(p \ast q) = \sum_{\sigma \in \Sigma} D(p\,\sigma\,q)$.
:::

::: **Claim 3** (Reads are linear). For structures $D, E$ and paths $p, q$: $(D + E)(p) = D(p) + E(p)$,
$p^{-1}(D + E) = p^{-1}D + p^{-1}E$, $(pq)^{-1}D = q^{-1}(p^{-1}D)$, and $p^{-1}(p \cdot D) = D$.
:::

<!-- quarry: Theorem 2, with its live proof (referential distributivity through recs) -->

**Directions.**

- *Derivatives.* The subtree $p^{-1}D$ is a left quotient, Brzozowski's derivative [6] taken by a whole path. His
  identity splits a language into its empty word plus each letter followed by its derivative. A structure splits the
  same way, $D = D_\varepsilon + \sum_{\sigma \in \Sigma} \sigma \cdot \sigma^{-1}D$, where $D_\varepsilon$ holds the
  arrows stored at the empty path, so a structure is determined by its reads. Is a layout a representation of a
  coalgebra, and is the equivalence of layouts bisimulation [14]?
- *A fundamental theorem.* Extension undoes a read, $p^{-1}(p \cdot D) = D$. A read undoes extension up to the rest of
  the structure, $p \cdot p^{-1}D = \pi_p(D)$. Is there a calculus of data in which writes integrate what reads
  differentiate?

# Layouts

::: **Definition 5** (Layout). A *layout* of a structure $D$ is a sum $D = O_1 + \cdots + O_m$ of *objects*, each an
expression stored as one unit. Two layouts are *equivalent* when they denote the same structure.
:::

A matrix $m$ with entries $a_{ij}$ has an entry-wise, a row-wise, and a nested layout, which are equivalent by
distributivity:
$$
\begin{equation*}
\sum_{i,j} \big(m\,i\,j \Rightarrow a_{ij}\big)
\;=\; \sum_{i} m\,i \cdot \Big(\sum_{j} \big(j \Rightarrow a_{ij}\big)\Big)
\;=\; m \cdot \Big(\sum_{i} i \cdot \sum_{j} \big(j \Rightarrow a_{ij}\big)\Big).
\end{equation*}
$$

::: **Claim 4** (Layout invariance). Equivalent layouts give the same answer to every read, subtree, and wildcard
read.
:::

<!-- quarry: Theorem 3; the tractatus runs the three matrix layouts live -->

::: **Claim 5** (What a layout shows). The number of objects in a layout is not a function of the structure it
denotes.
:::

<!-- quarry: Proposition 3, with its live proof (counts 1 and 2; the sum of the expanded layout is the nested one) -->

**Directions.**

- *Layout-safe instructions.* An operation survives every change of layout exactly when it factors through the
  structure. Reads do. Counting objects, ordering them, and measuring a container's length do not. Is this the same
  partition of the instruction set as the floatable runs and the cuts of
  [[4]](./ring-theoretic-distributed-computing.md)?
- *Cost.* A read's cost accumulates along a path, and a planner keeps the cheapest layout. A factorization is priced
  by evaluating it in the tropical semiring $\langle \mathbb{R} \cup \{\infty\}, \min, + \rangle$. Choosing the
  cheapest factorization of a term generalizes choosing the cheapest parenthesization of a matrix chain, which dynamic
  programming solves [15].

# Placement

::: **Definition 6** (Placement). A *placement* of a structure $D$ over places $1, \ldots, k$ assigns each object of a
layout to one place. Place $j$ holds $D_j$, so $D = D_1 + \cdots + D_k$. The maps $\pi_j$ that keep each place's
arrows form a complete set of orthogonal idempotents [8]:
$$
\begin{equation*}
\pi_1 + \cdots + \pi_k = 1, \qquad \pi_i\,\pi_j = 0 \;\; (i \neq j), \qquad \pi_j^2 = \pi_j.
\end{equation*}
$$
:::

::: **Claim 6** (The data shard identity). For a placement, a path $p$, and a cut $r$ of
[[4]](./ring-theoretic-distributed-computing.md) with combiner $C(r)$,
$D(p)\,r = \big(D_1(p)\,r + \cdots + D_k(p)\,r\big)\;C(r)$.
:::

<!-- quarry: Theorem 4 -->

::: **Claim 7** (Factoring colocates). A placement holds an object $p \cdot (E + F)$ at a single place. The
equivalent layout with the two objects $p \cdot E$ and $p \cdot F$ admits every placement of the first, and also
those that hold the two apart.
:::

<!-- quarry: Theorem 5 -->

::: **Claim 8** (Replication needs idempotence). Suppose two places hold a common part $R$. A commutative monoid fold
over the sum of their reads answers as it would over $D$ alone, for every $R$, if and only if it is idempotent,
$x \oplus x = x$.
:::

<!-- quarry: Theorem 6, with its live proof ({1,1} is {2}1; sup answers 1) -->

::: **Claim 9** (A point is a share). For $n = n_1 + n_2$,
$n\,(p \Rightarrow x) = n_1\,(p \Rightarrow x) + n_2\,(p \Rightarrow x)$.
:::

<!-- quarry: Proposition 4 -->

**Directions.**

- *Peirce blocks.* A complete set of orthogonal idempotents cuts a ring into blocks $e_i R\, e_j$, the way a block
  matrix is cut [8]. When the objects of a structure are themselves addresses, as references are, the structure is a
  relation between addresses, and its blocks $\pi_i D \pi_j$ with $i \neq j$ hold the references from place $i$ to
  place $j$. Is the communication cost of a placement the mass of its off-diagonal blocks? Is the best placement then
  a minimum cut of the reference graph [16], approximated from its spectrum [17]?
- *Overlapping shares.* The deal of [[4]](./ring-theoretic-distributed-computing.md) needs only
  $\sum_j \pi_j = 1$, while a placement needs orthogonality too. Shares that overlap are replication, and Claim 8
  states what it costs. What does a deal with overlapping shares compute?
- *Entry B.* Read from right to left, Claim 6 sends the program to the data: each place folds what it holds. This is
  entry B of the menu in [[4]](./ring-theoretic-distributed-computing.md), the data-local shard, which `law_shard`
  does not yet lower.

# Writes and Deletes

::: **Definition 7** (Writes). A *write* adds, $D \mapsto D + E$. A *delete* at a path $p$ removes the part below it,
$\delta_p = 1 - \pi_p$. An *overwrite* stores $x$ at $p$ alone,
$\omega_{p,x}(D) = \delta_p(D) + (p \Rightarrow x)$.
:::

::: **Claim 10** (Sums commute, overwrites do not). Writes commute. For overwrites at one path,
$\omega_{p,y} \circ \omega_{p,x} = \omega_{p,y}$, so the order of two overwrites decides the result.
:::

<!-- quarry: Theorem 7, with its live proof (plus in either order; update keeps the last) -->

::: **Claim 11** (Two ways to delete). A delete distributes over a placement,
$\delta_p(D_1 + \cdots + D_k) = \delta_p(D_1) + \cdots + \delta_p(D_k)$, and needs to know nothing about what it
removes. A subtraction $D - c\,(p \Rightarrow x)$ removes an arrow only when it knows the arrow.
:::

<!-- quarry: Theorem 8, with the two live deletes: a sum with the anti-object, and >>= [b=>none] -->

::: **Claim 12** (The zero names no delete). Since $k \Rightarrow 0 = 0$, the record $[k \Rightarrow 0]$ is the empty
record, and writing it changes nothing.
:::

<!-- quarry: Proposition 5, with its live proof ([b=>noobj] is [=>]; >>= [b=>none] deletes) -->

`none` is a scar: a mark outside the ring that an update reads as $\delta_k$, because the ring's own zero cannot say
*delete*. The ring may have a delete of its own.

::: **Claim 13** (A delete is an idempotent). Read records as relations (Definition 8), and take the parametric unity
$1 \Rightarrow 1$ of [[3]](./parametric-identities-in-ring-theory.md). Then
$\big((1 \Rightarrow 1) - (k \Rightarrow k)\big) \cdot D = \delta_k(D)$, and
$(1 \Rightarrow 1) - (k \Rightarrow k)$ is idempotent.
:::

<!-- quarry: Theorem 9. Live today: [(b=>b)].mult([(a=>1),(b=>2)]) keeps b. The difference waits on an arrow's coefficient being placed once ([{-1}(b=>b)] prints [{-1}b=>{-1}b]). -->

**Directions.**

- *Traces.* Two writes commute when they touch disjoint paths, or when both are sums. A log of writes, taken up to
  swapping independent neighbours, is a Mazurkiewicz trace [18]. Do replicas converge exactly when they hold the same
  trace? Is the coordination-free fragment exactly the additive one? That would be a data analogue of the CALM
  theorem [19], with conflict-free replicated data types [9] as its sums.
- *Relational algebra.* A type is a predicate, and a predicate $P$ is a diagonal idempotent,
  $\Delta_P = \sum_{x \in P} (x \Rightarrow x)$. Composing with $\Delta_P$ selects, composing with $1 - \Delta_P$
  deletes, composing with a bijection renames, and composing with a relation joins. Are Codd's operators [7] the
  relation ring acting on structures?
- *Complement.* Tarski's algebra of relations [20] has union, composition, converse, identity, and complement. The
  relation ring has union, composition, and identity, and [[2]](./span-and-point-coefficient-algebra.md) reaches the
  converse through negative spans. The complement of a key, $(1 \Rightarrow 1) - (k \Rightarrow k)$, exists
  because the unity is parametric. Is the scar the missing complement?

# Containers as Branches of Arrows

A container is a branch of arrows. A record is the *named* branch, whose arrows leave keys. A list read by its
positions is the *indexed* branch, whose arrows leave positions. Both are relations, so the relation ring of
[[3]](./parametric-identities-in-ring-theory.md) is their common algebra.

## Records

::: **Definition 8** (Record). A record is the sum of the arrows out of its keys,
$[k_1 \Rightarrow v_1, \ldots, k_n \Rightarrow v_n] = \sum_i (k_i \Rightarrow v_i)$. A record nested at a key extends
the key, $\big(k \Rightarrow [j \Rightarrow v]\big) = (kj \Rightarrow v)$.
:::

::: **Claim 14** (Merge is the sum). Records merge the way structures add. Below a shared key they merge,
$(k \Rightarrow E) + (k \Rightarrow F) = k \cdot (E + F)$; at a shared address their values branch; and a key into the
zero vanishes. Merge is a commutative monoid whose identity is the empty record.
:::

<!-- quarry: Theorem 10, with its live proof -->

**Directions.**

- *Join.* As relations, records compose, $[a \Rightarrow 1] \cdot [1 \Rightarrow x] = [a \Rightarrow x]$: a lookup
  through two records, the one-step join. mtron does not yet define the product of two records.

## Lists

::: **Definition 9** (Indexed list). Read by its positions, a list is the sum of the arrows out of them,
$[x_0, \ldots, x_{n-1}] = \sum_{i < n} (i \Rightarrow x_i)$.
:::

::: **Definition 10** (Word list). Read as a word, a list is the product of its slots,
$[x_1, \ldots, x_n] = x_1 \cdots x_n \in \mathcal{C}\langle X\rangle$.
:::

::: **Claim 15** (The indexed ring). Indexed lists add slot by slot and multiply by composition,
$(u \cdot v)_i = v_{u_i}$. Their zero is the empty list, and a slot holding the zero is no slot. Their unity is the
parametric unity $1 \Rightarrow 1$, which no list is.
:::

<!-- quarry: Theorem 11, with its live proof (composition through lists of arrows) -->

::: **Claim 16** (The word ring). Word lists multiply by concatenation, with the empty list as unity. They add as paths
add, factoring out a common prefix and suffix, and a branch in a slot is a sum of words.
:::

<!-- quarry: Theorem 12; its live proof runs on uris, since a list adds by concatenation today -->

::: **Claim 17** (Linear and multilinear). An indexed list is linear in its slots and a word list is multilinear:
$[c\,x_1, \ldots, c\,x_n]$ is $c\,[x_1, \ldots, x_n]$ in the indexed ring and $c^{n}\,[x_1, \ldots, x_n]$ in the
word ring.
:::

<!-- quarry: Proposition 6, with the live proof of how mtron lays out a negated span -->

::: **Claim 18** (No hybrid ring). On lists that keep their empty slots, let $\oplus$ be the slot sum of the indexed
ring and $\otimes$ the concatenation of the word ring. The two share the empty list as their identity, so together
they form no ring, and $\otimes$ distributes over $\oplus$ on neither side. They satisfy the interchange law
$(a \oplus b) \otimes (c \oplus d) = (a \otimes c) \oplus (b \otimes d)$ whenever $|a| = |b|$.
:::

<!-- quarry: Theorem 13, with the Clifford-monoid and well-definedness clauses -->

**Directions.**

- *Positions as exponents.* A third reading sends position $i$ to the monomial $t^{i}$, so a list becomes a
  polynomial in $t$: lists add slot by slot and multiply by convolution. mtron's slot product is this convolution with
  its terms ungrouped.
- *Interleavings.* The free algebra carries a second product, the shuffle, which sums every interleaving of two words
  that keeps each word's own order [21]. A sorted merge picks the one sorted interleaving of two sorted runs. Does
  `kmerge`, and with it the causal law of top-k in [[4]](./ring-theoretic-distributed-computing.md), live in the
  shuffle algebra?
- *Lockstep.* The hybrid's slot sum advances two lists slot against slot, the way the synchronous product of SCCS
  advances two processes step against step [10], and a slot cancelled by its inverse is an idle step. Its sums form a
  Clifford monoid [11]. Does lockstep composition deserve an operation of its own, outside the ring?
- *Naming.* Which ring's operations a list names $+$ and $\cdot$ is still open. Today a list adds by concatenation,
  which is the word ring's product.

## Relations

::: **Definition 11** (Relation ring). The relations of [[3]](./parametric-identities-in-ring-theory.md) are the
finite sums of arrows between objects. They add as sums and multiply by composition, with the parametric unity
$1 \Rightarrow 1$.
:::

Records and indexed lists are the relations whose arrows leave keys and positions.

**Directions.**

- *Matrices.* The relation ring is the ring of finitely supported matrices, so the matrix multiplication of entry M
  in [[4]](./ring-theoretic-distributed-computing.md) is composition in this ring, and that entry's addressed union is
  the indexed sum.
- *A zero inside the type.* Today a sum of relations is a stream of relations, which leaves the type. The code ring
  names its zero as an instruction that outputs nothing. A relation's zero could be named the same way, keeping its
  sum inside the type.

# The Mirror

| | Distributed Computing [[4]](./ring-theoretic-distributed-computing.md) | Distributed Structures |
|---|---|---|
| term | a program in the stream ring | a structure in the reference ring |
| $+$ | branch: placement is free | branch: placement is free |
| $\cdot$ | composition in series | extension, which colocates (Claim 7) |
| linear part | the floatable run | the read (Claim 3) |
| synchronization | the cut, a reduce | the overwrite (Claim 10) |
| identity | the shard identity | the data shard identity (Claim 6) |
| partition | the deal, $\sum_j \pi_j = 1$ | the placement, orthogonal idempotents |
| redundancy | entry H | replication needs idempotence (Claim 8) |
| invariance | the answer at every hop and schedule | every read in every layout (Claim 4) |

**Directions.**

- *A module.* Programs act on data: a read, a fold, and a write are each a function applied to a structure. If
  structures form a module over the code ring, then the data shard identity is the computing shard identity acting
  through the module. "Data and code compile alike" would then be a theorem rather than an analogy.

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
12. Håstad, J., "Tensor Rank is NP-complete," Journal of Algorithms 11(4), pages 644–654, 1990.
13. Nerode, A., "Linear Automaton Transformations," Proceedings of the American Mathematical Society 9(4), pages
    541–544, 1958.
14. Rutten, J.J.M.M., "Universal Coalgebra: a Theory of Systems," Theoretical Computer Science 249(1), pages 3–80,
    2000.
15. Cormen, T.H., Leiserson, C.E., Rivest, R.L., Stein, C., "Introduction to Algorithms," third edition, MIT Press,
    2009.
16. Kernighan, B.W., Lin, S., "An Efficient Heuristic Procedure for Partitioning Graphs," Bell System Technical Journal
    49(2), pages 291–307, 1970.
17. Fiedler, M., "Algebraic Connectivity of Graphs," Czechoslovak Mathematical Journal 23(2), pages 298–305, 1973.
18. Mazurkiewicz, A., "Concurrent Program Schemes and their Interpretations," DAIMI Report PB-78, Aarhus University,
    1977.
19. Hellerstein, J.M., Alvaro, P., "Keeping CALM: When Distributed Consistency Is Easy," Communications of the ACM
    63(9), pages 72–81, 2020.
20. Tarski, A., "On the Calculus of Relations," Journal of Symbolic Logic 6(3), pages 73–89, 1941.
21. Ree, R., "Lie Elements and an Algebra Associated with Shuffles," Annals of Mathematics 68(2), pages 210–220, 1958.