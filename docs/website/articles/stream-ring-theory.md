# Stream Ring Theory

Marko A. Rodriguez  
Captain, S/V Red Herring

```
Rodriguez, M.A., “Stream Ring Theory,” S/V Red Herring’s Ship’s Log: Chronicles in the Sea of Cortez, pages 10–40,
Mulegé, Baja California Sur, México, February 2019.
```

A stream is an ever expanding and contracting list of objects. Stream functions consume objects from an incoming stream
and produce objects for an outgoing stream. The presented stream algebra enables the composition of functional
structures that respect the axioms and entailed theorems of algebraic ring theory. The algebra can be used to write
expressions that are computationally equivalent to any Turing machine and as such, can be leveraged as the theoretical
foundation for all stream-based processing languages and systems.

## I. INTRODUCTION

A stream is an unordered list of objects. A stream can have objects inserted into it and it can have objects removed
from it. A stream function  $f$  has both an incoming stream and an outgoing stream. The incoming stream is the stream
of objects that have yet to be processed (removed) by  $f$  and the outgoing stream is the stream of objects that have
already been generated (inserted) by  $f$ . The type of objects incoming to  $f$  can be different from the type of
objects outgoing from  $f$ . For instance, the stream function  $f : X \rightarrow Y^*$  maps one  $X$ -object from the
incoming stream to zero or more  $Y$ -objects in the outgoing stream.<sup>1</sup> This simple foundation is developed
into an algebraic structure called a stream ring. A stream ring is a set of coefficients and functions along with
additive and multiplicative operators used for writing expressions that are isomorphic to an acyclic, directed graph of
coefficient-prefixed functions connected by streams.

This article will discuss ring theory, establish the presented stream structure as a ring, demonstrate common stream
ring patterns, and then use the axioms and theorems of stream ring theory to prove that the developed algebra is Turing
Complete.

## II. THE DEFINITION OF A RING

A ring is a set  $A$  with two binary operators  $+$  and  $\cdot$  called “addition” and “multiplication” respectively
and is denoted  $\langle A, +, \cdot \rangle$  [1]. The substructure  $\langle A, + \rangle$  is an abelian
(commutative) group with additive inverses and an additive identity element denoted  $0 \in A$ . If  $a, b, c \in A$ ,
then according to the axioms of ring theory

$$(a + b) + c = a + (b + c),$$

$$0 + a = a + 0 = a,$$

$$a - a = a + (-a) = 0,$$

and, due to commutativity,

$$a + b = b + a.$$

The substructure  $\langle A, \cdot \rangle$  is a monoid with a multiplicative identity element denoted  $1 \in A$
such that

$$(a \cdot b) \cdot c = a \cdot (b \cdot c),$$

and

$$1 \cdot a = a \cdot 1 = a.$$

In the aggregate ring structure  $\langle A, +, \cdot \rangle$ , multiplication is both right and left distributive over
addition such that

$$(a + b) \cdot c = (a \cdot c) + (b \cdot c)$$

and

$$a \cdot (b + c) = (a \cdot b) + (a \cdot c).$$

The standard term for the structure  $\langle A, +, \cdot \rangle$  is a *ring with unity*.<sup>2</sup> If a ring is
closed under addition and multiplication, then for every  $a, b \in A$ ,  $a + b \in A$  and  $a \cdot b \in A$ .
Studied ring extensions include a multiplicative operator that is both idempotent (for all  $n > 0$ ,  $a^n = a$ ) and
commutative ( $a \cdot b = b \cdot a$ ).<sup>3</sup> The commonly used implications and equalities in Theorem 1 can be
directly deduced from the aforementioned ring theory axioms.

**Theorem 1.** If  $\langle A, +, \cdot \rangle$  is a ring and  $a, b, c \in A$ , then

1. $a + b = a + c \implies b = c$
2. $a + b = 0 \implies a = -b$  and  $b = -a$
3. $- (a + b) = (-a) + (-b)$
4. $- (-a) = a$
5. $a0 = 0 = 0a$
6. $a (-b) = -a (b) = - (ab)$
7. $(-a)(-b) = ab$

<sup>1</sup> The set  $X^*$  refers to the Kleene star closure on the elements of the set  $X$  and is equivalent to the
multi-set of all possible combinations of the elements in  $X$  with repetition of elements allowed. Thus,
if  $|X| > 0$ ,  $|X^*| = \infty$ .

<sup>2</sup> The identity element  $1 \in A$  in the multiplicative monoid  $\langle A, \cdot \rangle$  is oftentimes
referred to as “unity.”

<sup>3</sup> When multiplication is clear from context,  $a \cdot b \cdot c$  will be written as  $abc$ .

*Proof.* The theorem's implications and equalities will be rigorously deduced from the ring axioms.

$$1. a + b = a + c \implies b = c$$

$$\begin{aligned} a + b &= a + c \\ -a + a + b &= -a + a + c & [\text{add } -a] \\ (-a + a) + b &= (-a + a) + c & [+ \text{ is associative}] \\ (a - a) + b &= (a - a) + c & [+ \text{ is commutative}] \\ 0 + b &= 0 + c & [a - a = 0] \\ b &= c & [0 + a = a] \end{aligned}$$

This is the *additive cancellation law*.

$$2. a + b = 0 \implies a = -b \text{ and } b = -a$$

$$\begin{aligned} a + b &= 0 \\ a + b - b &= -b & [\text{add } -b] \\ a + (b - b) &= -b & [+ \text{ is associative}] \\ a + 0 &= -b & [b - b = 0] \\ a &= -b & [a + 0 = a] \end{aligned}$$

A similar deduction proves  $b = -a$ .

$$3. - (a + b) = (-a) + (-b)$$

$$\begin{aligned} 0 + 0 &= 0 & [a + 0 = a] \\ (a - a) + (b - b) &= 0 & [a - a = 0] \\ a + (-a) + b + (-b) &= 0 & [+ \text{ is associative}] \\ a + b + (-a) + (-b) &= 0 & [+ \text{ is commutative}] \\ (a + b) + (-a) + (-b) &= 0 & [+ \text{ is associative}] \\ (-a) + (-b) &= - (a + b) & [\text{add } - (a + b)] \end{aligned}$$

$$4. - (-a) = a$$

$$\begin{aligned} a + (-a) &= 0 & [a - a = 0] \\ a + (-a) - (-a) &= - (-a) & [\text{add } - (-a)] \\ a + ((-a) - (-a)) &= - (-a) & [+ \text{ is associative}] \\ a + 0 &= - (-a) & [a - a = 0] \\ a &= - (-a) & [a + 0 = a] \end{aligned}$$

$$5. a0 = 0 = 0a$$

$$\begin{aligned} aa + 0 &= aa & [a + 0 = a] \\ aa + 0 &= a (a + 0) & [a = a + 0] \\ aa + 0 &= aa + a0 & [\cdot \text{ is left distributive}] \\ 0 &= a0 & [\text{add } -aa] \end{aligned}$$

A similar deduction using the right distributive ring axiom proves  $0a = 0$ .

$$6. a (-b) = -a (b) = - (ab)$$

$$\begin{aligned} a (-b) + ab &= a (-b + b) & [\cdot \text{ is left distributive}] \\ a (-b) + ab &= a (b - b) & [+ \text{ is commutative}] \\ a (-b) + ab &= a0 & [a - a = 0] \\ a (-b) + ab &= 0 & [a0 = 0] \\ a (-b) &= - (ab) & [\text{add } -ab] \end{aligned}$$

A similar deduction yields  $-a (b) = - (ab)$ .

$$7. (-a)(-b) = ab$$

$$\begin{aligned} (-a)(-b) &= (-a)(-b) & [a = a] \\ (-a)(-b) &= - (a (-b)) & [-a (b) = - (ab)] \\ (-a)(-b) &= - (- (ab)) & [a (-b) = - (ab)] \\ (-a)(-b) &= ab & [- (-a) = a] \end{aligned}$$

□

## III. THE DEFINITION OF A STREAM RING

A stream expression is composed of streams, functions, and objects. An intuitive definition of these components will be
presented first and then a formal specification containing axioms and theorems will be provided in the subsequent
subsections.

**Definition 1** (Stream). The stream  $\mathbf{x} \in X^*$  is an unordered list of objects in  $X$ ,
where  $\mathbf{x} = \langle x_1, x_2, \dots, x_n \rangle$ . A stream is directed. There is a tail
function  $a : ? \rightarrow X$  which inserts objects into the stream and there is a head
function  $b : X \rightarrow ?$  which removes objects from the stream. The codomain type of the tail function always
equals the domain type of the head function.<sup>4</sup>

**Definition 2** (Stream Object). A stream object is produced by a tail function and consumed by a respective head
function. Every stream object  $x \in X$  has a corresponding coefficient  $c \in \mathcal{C}$  and when its coefficient
is considered, the stream object is denoted  $cx$ . Coefficients are elements from any algebraic ring with
unity  $\langle \mathcal{C}, +, \cdot \rangle$ .

**Definition 3** (Stream Function). A stream function consumes objects from its incoming stream and produces objects for
its outgoing stream. If the incoming stream is in  $X^*$  and the outgoing stream is in  $Y^*$ , then, in general, the
stream function  $a$  has the signature  $a : X \rightarrow Y^*$ . Every stream function has a corresponding
coefficient  $c \in \mathcal{C}$  such that a function along with its coefficient is denoted  $ca$ . All stream
functions form the ring with unity  $\langle \mathcal{F}, +, \cdot \rangle$ .

The above definitions introduce two algebraic rings with unity: the *coefficient ring* and the *function ring*. These
rings will be discussed, proved, and then unified into the ultimate focal structure of this article, namely, the stream
ring.

<sup>4</sup> The term “ (co)domain type” is used instead of simply “ (co)domain” because, in some situations, while the
head function's codomain is  $X^*$  and the tail function's domain is  $X$ , the *type* of objects in both is  $X$ .

### A. The Coefficient Ring

The coefficient ring  $\langle \mathcal{C}, +, \cdot \rangle$  is any ring with unity. For most of the examples to
follow,  $\mathcal{C}$  is the set of integers  $\mathbb{Z}$  with  $+$  being numeric addition and  $\cdot$  being
numeric multiplication. In order to prove that  $\langle \mathbb{Z}, +, \cdot \rangle$  is a ring with unity, it must be
demonstrated that the structure satisfies all the aforementioned ring axioms. The validity of these axioms
over  $\mathbb{Z}$  is readily apparent to those with rudimentary arithmetic knowledge.

**Theorem 2.** The structure  $\langle \mathbb{Z}, +, \cdot \rangle$  is a ring with unity.

*Proof.* Each operator of a ring defines an algebraic substructure. The substructure  $\langle \mathbb{Z}, + \rangle$
must be an abelian (commutative) group. The substructure  $\langle \mathbb{Z}, \cdot \rangle$  must be a monoid with
unity.

The substructure  $\langle \mathbb{Z}, + \rangle$  is associative because  $(a+b)+c = a+ (b+c)$  and commutative
because  $a+b = b+a$ . In words, the order in which a set of integers is added does not change the resultant summation.
The element  $0 \in \mathbb{Z}$  is the additive identity element, where for
every  $a \in \mathbb{Z}$ ,  $0+a = a+0 = a$ . Every integer has a negative inverse such that  $a-a = a+ (-a) = 0$ .

The substructure  $\langle \mathbb{Z}, \cdot \rangle$  is associative
because  $(a \cdot b) \cdot c = a \cdot (b \cdot c)$ . The element  $1 \in \mathbb{Z}$  is the multiplicative identity
element known as unity, where for every  $a \in \mathbb{Z}$ ,  $1 \cdot a = a \cdot 1 = a$ .

Finally, in order for these two substructures to form a ring in aggregate, it must be the case that multiplication is
both right and left distributive over addition such that  $(a+b) \cdot c = ac+bc$  and  $a \cdot (b+c) = ab+ac$ ,
respectively. These equalities are true for the integers  $\mathbb{Z}$ . Thus, the
structure  $\langle \mathbb{Z}, +, \cdot \rangle$  is a ring with unity.  $\square$

It is important to emphasize that any ring with unity can be used to construct a stream ring. Other ring examples
include the ring of real numbers  $\mathbb{R}$ , complex numbers  $\mathbb{C}$ , rational numbers  $\mathbb{Q}$ ,
respective numeric matrices, and even coordinate systems in two ( $\mathbb{R} \times \mathbb{R}$ ), three
( $\mathbb{R} \times \mathbb{R} \times \mathbb{R}$ ), or more dimensions. Ring theory is rife with example rings and
depending on the the domain of application of stream ring theory, a suitable coefficient ring can be chosen.

### B. The Function Ring

$\mathcal{F}$  is the set of all stream functions. The structure  $\langle \mathcal{F}, +, \cdot \rangle$  is a ring
with unity. The additive binary operator  $+: \mathcal{F} \times \mathcal{F} \rightarrow \mathcal{F}$  combines two
functions into a single parallel function, where each original function shares the same incoming and outgoing stream.
The multiplicative binary operator  $\cdot: \mathcal{F} \times \mathcal{F} \rightarrow \mathcal{F}$  composes two
functions into a single serial function. There is an additive identity element  $0 \in \mathcal{F}$  and a
multiplicative identity (unity) element  $1 \in \mathcal{F}$  defined as  $0 (x) = \emptyset$  and  $1 (x) = x$ ,
respectively. Every function  $a \in \mathcal{F}$  has an additive inverse  $-a \in \mathcal{F}$ .

Every symbol-based expression has a corresponding diagrammatic representation. Diagram vertices represent functions and
directed edges represent streams. For example,  $a+b$  has the form

![Diagram for a+b: Two parallel streams. The top stream is labeled 'a' and the bottom stream is labeled 'b'. Arrows point from left to right through both streams.](8239bdc2ad69faed67c2741625a1ca79_img.jpg)

Diagram for a+b: Two parallel streams. The top stream is labeled 'a' and the bottom stream is labeled 'b'. Arrows point
from left to right through both streams.

$a \cdot b \cdot c$  has the form

$$a \rightarrow b \rightarrow c,$$

and, to provide a complex example,  $a \cdot b \cdot (c + (d \cdot e)) \cdot f$  is diagrammed as

![Diagram for a complex expression: A serial chain of functions. Stream 'a' enters 'b'. From 'b', the stream splits into two parallel branches: the top branch is 'c' and the bottom branch is 'd'. The streams from 'c' and 'd' merge into 'e'. From 'e', the stream enters 'f'.](826227f3016572a1a1e791c12e2ed86e_img.jpg)

Diagram for a complex expression: A serial chain of functions. Stream 'a' enters 'b'. From 'b', the stream splits into
two parallel branches: the top branch is 'c' and the bottom branch is 'd'. The streams from 'c' and 'd' merge into 'e'.
From 'e', the stream enters 'f'.

An expression is an equation (program) without arguments (input). To evaluate an expression, objects must be inserted
into a stream.<sup>5</sup> For example, if  $\langle \rangle$  is an empty stream, then the expression

$$\langle x, y, z \rangle abc$$

places the objects  $x$ ,  $y$ , and  $z$  on the incoming stream of  $a$  and is diagrammed as

$$\xrightarrow{x, y, z} a \rightarrow b \rightarrow c.$$

The objects of a stream move from the tail of an arrow to the head of the arrow and are processed by the functions they
encounter along the way. When an object comes to a split, the object is copied to each branch. When two streams join,
their respective objects are merged in no required order. The following four diagrams demonstrate how  $x$  propagates
through the branching expression  $(1+1)$  so as to diagrammatically prove
that  $\langle x \rangle (1+1) = \langle x, x \rangle$ .

![Four diagrams showing the propagation of object 'x' through the expression (1+1). 1. An incoming stream with object 'x' enters a function '1'. 2. The stream splits into two parallel branches, each labeled '1'. 3. The two parallel streams merge back into a single stream. 4. The final stream contains two objects, 'x' and 'x', representing the result of the expression.](8b10a91eb4c2b5dfd1727054a5e44e1c_img.jpg)

Four diagrams showing the propagation of object 'x' through the expression (1+1). 1. An incoming stream with object 'x'
enters a function '1'. 2. The stream splits into two parallel branches, each labeled '1'. 3. The two parallel streams
merge back into a single stream. 4. The final stream contains two objects, 'x' and 'x', representing the result of the
expression.

<sup>5</sup> Over the course of this article, a *stream notation* is developed that is more aligned with stream
semantics than standard mathematical function notation. If  $a$  is a stream function, it can be
denoted  $\langle a \rangle$  showing that it has both an empty incoming and outgoing stream. If, in function
notation  $a (x) = y$ , then  $\langle x \rangle a \langle \rangle = \langle a (y) \rangle$ . Or more
conveniently,  $\langle x \rangle a = \langle y \rangle$ . The stream expression  $\langle x, y \rangle a$  is not
equivalent to the function expression  $a (x, y)$  as  $a$  maps one object at a time.
Instead,  $\langle x, y \rangle a = \langle a (x), a (y) \rangle$ . It is always the case
that  $\langle x \rangle a = a (x)$ . More specifically,  $\langle x \rangle a \equiv \langle a (x) \rangle$ .

![Diagram showing the addition of two streams. The first stream has two incoming arrows labeled 1 and 1, which are split by functions x and y respectively. The second stream has two incoming arrows labeled 1 and 1, which are split by functions x and x respectively. The outgoing arrows from both streams are labeled x, x.](2fa4a1bf91d0f34e87c689fbc1211fe3_img.jpg)

Diagram showing the addition of two streams. The first stream has two incoming arrows labeled 1 and 1, which are split
by functions x and y respectively. The second stream has two incoming arrows labeled 1 and 1, which are split by
functions x and x respectively. The outgoing arrows from both streams are labeled x, x.

**Theorem 3.** The structure  $\langle \mathcal{F}, +, \cdot \rangle$  is a ring with unity.

*Proof.* The aforementioned axioms of ring theory must be true if  $\langle \mathcal{F}, +, \cdot \rangle$  is a ring
with unity.

The abelian group  $\langle \mathcal{F}, + \rangle$  must be commutative such that  $a + b = b + a$ . Diagrammatically,

![Diagram showing the commutativity of addition. Two streams, one with function a and one with function b, are shown to be equivalent when their outgoing objects are merged.](f0bae10b54c4f3cf8d0e33f5e2fb7cfa_img.jpg)

Diagram showing the commutativity of addition. Two streams, one with function a and one with function b, are shown to be
equivalent when their outgoing objects are merged.

Addition creates two parallel streams. The incoming objects to  $a$  and  $b$  are identical because when an object
meets a split, it is copied onto each branch. The outgoing objects of  $a$  and  $b$  are merged into a single stream in
no defined order. Thus,  $a + b = b + a$ . The abelian group must also be associative such
that  $(a + b) + c = a + (b + c) = a + b + c$ . These equalities are diagrammed as

![Diagram showing the associativity of addition. Three streams, labeled a, b, and c, are shown to be equivalent when their outgoing objects are merged in different orders.](0c9723d1620cf51bc2b7a380ce7e23c0_img.jpg)

Diagram showing the associativity of addition. Three streams, labeled a, b, and c, are shown to be equivalent when their
outgoing objects are merged in different orders.

In the three diagrams above, the branch functions  $a$ ,  $b$ , and  $c$  each receive the same incoming objects as the
objects encountering the various splits are not altered prior to the first function of each respective branch in each
diagram. Moreover, the outgoing objects from  $a$ ,  $b$ , and  $c$  are merged in no defined order. Therefore, the
three diagrams above are equivalent and  $\langle \mathcal{F}, + \rangle$  is associative. The abelian group must have
an additive identity element  $0$ , where  $a + 0 = a$  and  $0 + a = a$ . If  $a (x) = y$ , then the
equality  $\langle x \rangle (a + 0) = \langle x \rangle a$  is proved diagrammatically as

![Diagram showing the additive identity property. A stream with function a is shown to be equivalent to a stream with function a followed by a 0 function, which is then shown to be equivalent to a stream with function a (x).](7a207f7bb5a20f849aee089439e24a84_img.jpg)

Diagram showing the additive identity property. A stream with function a is shown to be equivalent to a stream with
function a followed by a 0 function, which is then shown to be equivalent to a stream with function a (x).

Given that  $0 (x) = \emptyset$  or, in stream notation,  $\langle x \rangle 0 = \langle \rangle$ , if the only function
of a branch is  $0$ , then the incoming objects to  $0$  are never merged into the outgoing stream. Instead, only the
outgoing objects of  $a$  are merged. Thus,  $a + 0 = a$  and via the previously proved commutative
property,  $0 + a = a$ . Finally, every function in  $a \in \mathcal{F}$  must have an additive
inverse  $-a \in \mathcal{F}$  such that

$a - a = a + (-a) = 0$ . The validity of this axiom within a function ring requires the introduction of the concept of
*object orthogonality* which is captured by using two rudimentary object coefficients:  $1$  and  $-1$ . The
equality  $\langle x \rangle (a - a) = \langle x \rangle 0$  is demonstrated diagrammatically where if  $a (x) = y$
and  $-a (x) = -y$ , then

![Diagram showing the additive inverse property. A stream with function a is shown to be equivalent to a stream with function a followed by a -a function, which is then shown to be equivalent to a stream with function 0 (x).](f5e131a3fffe09aa98db055df84e4378_img.jpg)

Diagram showing the additive inverse property. A stream with function a is shown to be equivalent to a stream with
function a followed by a -a function, which is then shown to be equivalent to a stream with function 0 (x).

When two “equivalent” objects in a stream are orthogonal to each other (e.g. opposing signs), they annihilate each
other. Thus,  $\langle x \rangle (a - a) = \langle y, -y \rangle = \langle \rangle$ . This is equivalent
to  $\langle x \rangle 0$  and therefore,  $a - a = 0$ .<sup>6</sup>

For  $\langle \mathcal{F}, +, \cdot \rangle$  to be a ring, the monoid  $\langle \mathcal{F}, \cdot \rangle$  must be
associative such that  $(a \cdot b) \cdot c = a \cdot (b \cdot c) = a \cdot b \cdot c$ . Diagrammatically,

$$ab \rightarrow c = a \rightarrow bc = a \rightarrow b \rightarrow c.$$

Stream function composition makes implicit the explicit stream binding two functions. If  $a (x) = y$
and  $b (y) = z$ , then  $\langle x \rangle ab$  is equivalent to  $b (a (x))$ . Thus, whether the functions are
composed into a single function or not, the serial stream will produce the same result and
therefore,  $\langle \mathcal{F}, \cdot \rangle$  is associative. In a ring with unity, there must be a multiplicative
identity element  $1 \in \mathcal{F}$  which is defined as  $1 (x) = x$  or, in stream
notation,  $\langle x \rangle 1 = \langle x \rangle$ . The multiplicative identity element’s definition makes it clear
that  $a \cdot 1 = 1 \cdot a = a$ . If  $a (x) = y$ , then diagrammatically

$$\xrightarrow{x} a \rightarrow 1 \rightarrow \quad \rightarrow a \xrightarrow{y} 1 \rightarrow \quad \rightarrow a \rightarrow 1 \xrightarrow{y}.$$

While  $a \cdot 0 = 0 \cdot a = 0$  can be deduced from the ring axioms, note that in general, any serial
(multiplicative) chain of functions that contains the  $0$  element is equivalent to  $0$ . For instance,  $a0b = 0$
because if  $x$  is processed by  $a$  it will never reach  $b$  and therefore,  $b$  will never receive nor emit an
object. Algebraically,  $(a0)b = 0b = 0$  and  $a (0b) = a0 = 0$ . Diagrammatically,

$$\xrightarrow{x} a \rightarrow 0 \rightarrow b \quad \rightarrow a \xrightarrow{y} 0 \rightarrow b \quad \rightarrow a \rightarrow 0 \rightarrow b.$$

Multiplication must be right distributive over addition such that  $(a + b) \cdot c = (a \cdot c) + (b \cdot c)$ .
Diagrammatically,

![Diagram showing the right distributive property of multiplication over addition. A stream with functions a and b is shown to be equivalent to a stream with function a followed by function c, which is then shown to be equivalent to a stream with function b followed by function c.](2440a3682261cd143c10992cedd68d3a_img.jpg)

Diagram showing the right distributive property of multiplication over addition. A stream with functions a and b is
shown to be equivalent to a stream with function a followed by function c, which is then shown to be equivalent to a
stream with function b followed by function c.

<sup>6</sup> The concepts of equivalence, orthogonality, and annihilation are discussed in depth in the next section
which introduces stream rings.

The outgoing objects from  $a$  and  $b$  will be incoming objects to  $c$  regardless of whether the objects are merged
first before being processed by  $c$  or are processed by  $c$  on each respective branch and then merged. Finally,
multiplication must be left distributive over addition such that  $a \cdot (b + c) = (a \cdot b) + (a \cdot c)$ .
Diagrammatically,

![Diagram showing the distributive property of multiplication over addition. On the left, a 2-way branch from 'a' splits into two paths, each leading to a 2-way branch: the top path goes to 'b' and then to 'c', and the bottom path goes to 'c' and then to 'c'. On the right, a 2-way branch from 'a' splits into two paths, each leading to a 2-way branch: the top path goes to 'b' and then to 'c', and the bottom path goes to 'c' and then to 'c'. The two diagrams are separated by an equals sign.](e394c2b5c61344f6a12397f430086072_img.jpg)

Diagram showing the distributive property of multiplication over addition. On the left, a 2-way branch from 'a' splits
into two paths, each leading to a 2-way branch: the top path goes to 'b' and then to 'c', and the bottom path goes to
'c' and then to 'c'. On the right, a 2-way branch from 'a' splits into two paths, each leading to a 2-way branch: the
top path goes to 'b' and then to 'c', and the bottom path goes to 'c' and then to 'c'. The two diagrams are separated by
an equals sign.

The incoming objects to  $b$  and  $c$  will be outgoing from  $a$  regardless of whether the objects are first
processed by  $a$  and then split or whether the objects are first split and then processed by  $a$  on each branch.
Thus,  $\langle \mathcal{F}, +, \cdot \rangle$  is a ring with unity.  $\square$

*Binomials* and *multinomials* are important concepts in ring theory. A binomial is a two component sum raised to a
power. For instance,

$$(a + b)^n.$$

A multinomial generalizes a binomial, where any number of different two component summations are multiplied. Both
binomials and multinomials have *expansions*. An expansion transforms a multiplication of additions into an addition of
multiplications. For example, the following equality relates a multinomial to its expansion:

$$(a + b) \cdot (b + c) = ab + ac + b^2 + bc.$$

The validity of this equality can be deduced from the ring axioms (e.g. using the “foil method”).

$$\begin{aligned} (a + b)(b + c) & \\ a (b + c) + b (b + c) & \quad [\cdot \text{ is right distributive}] \\ (ab + ac) + (b^2 + bc) & \quad [\cdot \text{ is left distributive}] \\ ab + ac + b^2 + bc & \quad [+ \text{ is associative}] \end{aligned}$$

In the lexicon of the developed function ring, the concatenation of two 2-way branches can be expanded into an
equivalent single 4-way branch. Diagrammatically,

![Diagram showing the expansion of a concatenation of two 2-way branches into a single 4-way branch. On the left, a 2-way branch from 'a' splits into two paths, each leading to a 2-way branch: the top path goes to 'b' and then to 'c', and the bottom path goes to 'c' and then to 'c'. On the right, a single 4-way branch from 'a' splits into four paths: the top two paths go to 'b' and then to 'c', and the bottom two paths go to 'c' and then to 'c'. The two diagrams are separated by an equals sign.](4d7f667796a8cdcdd745e953ac11e289_img.jpg)

Diagram showing the expansion of a concatenation of two 2-way branches into a single 4-way branch. On the left, a 2-way
branch from 'a' splits into two paths, each leading to a 2-way branch: the top path goes to 'b' and then to 'c', and the
bottom path goes to 'c' and then to 'c'. On the right, a single 4-way branch from 'a' splits into four paths: the top
two paths go to 'b' and then to 'c', and the bottom two paths go to 'c' and then to 'c'. The two diagrams are separated
by an equals sign.

### C. The Stream Ring

The product of a coefficient ring  $\langle \mathcal{C}, +, \cdot \rangle$  and a function
ring  $\langle \mathcal{F}, +, \cdot \rangle$  forms a stream ring  $\langle \mathcal{CF}, +, \cdot \rangle$ .<sup>
7</sup> The product of the sets  $\mathcal{C}$  and  $\mathcal{F}$  is the cross product

$$\mathcal{C} \times \mathcal{F} = \{ (c, a) : c \in \mathcal{C} \wedge a \in \mathcal{F}\}.$$

For every tuple  $(c, a) \in \mathcal{C} \times \mathcal{F}$ , the first element is a coefficient and the second element
is a function. Tuples will be written  $ca \in \mathcal{CF}$ . If  $ca, db \in \mathcal{CF}$ , then the stream ring's
additive  $+$  operator is defined as a product of the coefficient and function rings' additive operators with

$$ca + db = \begin{cases} (c + d)a & \text{if } a = b, \\ ca + db & \text{otherwise,} \end{cases}$$

where on the right hand side of the equality,  $(c + d)$  uses the coefficient ring's addition and  $ca + db$  uses the
function ring's addition. The stream ring's multiplicative  $\cdot$  operator is defined as the product of the pairwise
multiplication of the coefficient and function rings with

$$ca \cdot db = (c \cdot d)(a \cdot b),$$

where  $(c \cdot d)$  uses the coefficient ring's multiplication operator and  $(a \cdot b)$  uses the function ring's
multiplication operator. In general, when coefficients are being added or multiplied, it is the coefficient ring's
respective operators. Likewise, when functions are being added or multiplied, it is the function ring's respective
operators.<sup>8,9</sup>

The stream ring's functions operate on stream objects. Every object  $x \in X$  has a corresponding
coefficient  $c \in \mathcal{C}$  where a stream object of type  $\mathcal{CX}$  is an element in the set

$$\mathcal{C} \times X = \{ (c, x) : c \in \mathcal{C} \wedge x \in X\}.$$

When a stream object's coefficient is referenced, the object is denoted  $cx \in \mathcal{CX}$ .<sup>10</sup> The
following equalities spec-

<sup>7</sup> The symbols  $+$  and  $\cdot$  are overloaded. In each ring,  $+$  and  $\cdot$  has a unique definition.
It will be clear from the context which operation is being referred to.

<sup>8</sup> The stream ring is similar to the *direct product* of the coefficient and function
rings  $\langle \mathcal{C}, +, \cdot \rangle \otimes \langle \mathcal{F}, +, \cdot \rangle$ , where both addition and
multiplication are defined pairwise. In abstract algebra, it has been proved that the direct product of any two rings
forms a ring. However, while multiplication is defined pairwise in the stream ring, addition is not and thus, a formal
proof is required to demonstrate that  $\langle \mathcal{CF}, +, \cdot \rangle$  is a ring with unity.

<sup>9</sup> The smallest coefficient ring possible for constructing a stream ring
is  $\langle \{-1, 0, 1\}, +, \cdot \rangle$ , where 1 is the additive identity, 0 is the multiplicative
identity,  $\cdot$  is numeric multiplication, and  $+$  is numeric addition save that  $1 + 1$  and  $-1 - 1$  are not
defined. The stream expression  $a + a \neq 2a$ , but instead remains  $a + a$  or  $(1 + 1)a$ . The object
stream  $\langle x, x \rangle$  can not be bulked to  $\langle 2x \rangle$ , but remains  $\langle x, x \rangle$ . A
similar pattern holds for  $-1 - 1$ .

<sup>10</sup> When an object (or function) is denoted  $x$  (or  $a$ ), it means that the coefficient is 1 and
thus,  $x = 1x$  (or  $a = 1a$ ), where  $1 \in \mathcal{C}$ . In a stream expression, when a coefficient  $c$  is
denoted without a function, then  $c = c1$ , where  $1 \in \mathcal{F}$ .

ify how a stream of coefficient-prefixed objects are manipulated by coefficient-prefixed functions.<sup>11</sup> These
equalities, along with the stream ring definitions of  $+$  and  $\cdot$  above, form the *stream ring axioms* which
will serve as the foundation for the forthcoming proof that  $\langle \mathcal{CF}, +, \cdot \rangle$  is a ring with
unity. If  $x, y, z \in X$  are objects,  $0, c, d, e \in \mathcal{C}$  are coefficients, and  $a, b \in \mathcal{F}$
are functions, then

1. $x \sim y \implies \langle cx \rangle = \langle cy \rangle$
2. $\langle cx, dy \rangle = \langle dy, cx \rangle$
3. $\langle cx, dx \rangle = \langle (c + d)x \rangle$
4. $\langle cx \rangle da = \langle (c \cdot d)a (x) \rangle$
5. $\langle cx \rangle (da + eb) = (\langle cx \rangle da) + (\langle cx \rangle eb)$
6. $\langle cx \rangle + \langle dy \rangle = \langle cx, dy \rangle$
7. $\langle 0x \rangle = \langle c\emptyset \rangle = \langle \rangle$ .

1. $x \sim y \implies \langle cx \rangle = \langle cy \rangle$ . Every object  $x \in X$  within the
   stream  $\mathbf{x} \in X^*$  is an element of the equivalence class

$$[x] = \{y \in \mathbf{x} : \forall f \in \mathcal{F}_{X \rightarrow ?} \; f (x) = f (y)\}.$$

If the objects  $x$  and  $y$  map to the same range for every applicable function in  $\mathcal{F}$ , then there exists
the equivalence relation  $x \sim y$  and the stream  $\langle cx \rangle = \langle cy \rangle$ . In essence, two
objects are “equal” if they are in the same stream and behave the same way for all  $\mathcal{F}$ .<sup>12</sup>

2. $\langle cx, dy \rangle = \langle dy, cx \rangle$ . Streams are unordered lists of stream objects and are considered
   equal if they contain the same objects with respective coefficients.
3. $\langle cx, dx \rangle = \langle (c + d)x \rangle$ . If a stream contains two equivalent objects, then they can be
   merged into a single stream object by summing their coefficients using the coefficient ring’s additive operator. This
   is called the *bulk axiom*.
4. $\langle cx \rangle da = \langle (c \cdot d)a (x) \rangle$ . The coefficients of a function’s outgoing objects are
   equal to the function’s incoming object’s coefficient multiplied by the function’s coefficient. This is called the
   *apply axiom*.<sup>13</sup>
5. $\langle cx \rangle (da + eb) = (\langle cx \rangle da) + (\langle cx \rangle eb)$ . The incoming stream object to
   two parallel functions is copied to

the incoming stream of each function. This is called the *split axiom*.

6. $\langle cx \rangle + \langle dy \rangle = \langle cx, dy \rangle$ . The sum of two streams is a stream containing
   the objects of the original streams. With respect to stream addition, the outgoing stream of two parallel functions
   is the merging of the individual functions’ outgoing streams. This is called the *merge axiom*.<sup>14</sup>
7. $\langle 0x \rangle = \langle c\emptyset \rangle = \langle \rangle$ . If an object has the
   coefficient  $0 \in \mathcal{C}$ , then it can be removed from the stream. If an object is the empty set, then it can
   be removed from the stream.

**Theorem 4.** The structure  $\langle \mathcal{CF}, +, \cdot \rangle$  is a ring with unity.

*Proof.* If  $\langle \mathcal{CF}, +, \cdot \rangle$  is a ring with unity, then  $\langle \mathcal{CF}, + \rangle$
must be an abelian group and  $\langle \mathcal{CF}, \cdot \rangle$  must be a monoid with unity. These two structures
must also interact where multiplication is both right and left distributive over addition.

The group  $\langle \mathcal{CF}, + \rangle$  must be commutative where  $ca + db = db + ca$ . If  $a (x) = y$
and  $b (x) = z$ , then

$$\begin{aligned}
ca + db &= db + ca \\
\langle x \rangle ca + db &= \langle x \rangle db + ca && [\text{apply } 1x] \\ ((\langle x \rangle ca) + (\langle x \rangle db) &= (\langle x \rangle db) + (\langle x \rangle ca) && [\text{split axiom}] \\
\langle cy \rangle + (\langle x \rangle db) &= (\langle x \rangle db) + \langle cy \rangle && [\langle x \rangle ca = \langle cy \rangle] \\
\langle cy \rangle + \langle dz \rangle &= \langle dz \rangle + \langle cy \rangle && [\langle x \rangle db = \langle dz \rangle] \\
\langle cy, dz \rangle &= \langle dz, cy \rangle && [\text{merge axiom}] \\
\langle cy, dz \rangle &= \langle cy, dz \rangle && [\langle x, y \rangle = \langle y, x \rangle].
\end{aligned}$$

For the case in stream addition when  $a = b$  and thus,  $ca + db = (c + d)a$ , it is only necessary to prove the
equality to prove commutativity as the right hand side

<sup>14</sup> The application of the merge axiom to the merging of the outgoing streams of two parallel functions is
made more apparent when using verbose stream notation. If  $a (x) = x$  and  $b (x) = y$ , then

$$\begin{aligned}
&\langle \rangle (\langle ca \rangle + \langle db \rangle) \langle \rangle && [ca + db] \\
&\langle 1x \rangle (\langle ca \rangle + \langle db \rangle) \langle \rangle && [(1x)(ca + db)] \\
&\langle (\langle 1x \rangle ca) + \langle 1x \rangle db \rangle \langle \rangle && [\text{split axiom}] \\
&\langle (\langle ca \rangle \langle cx \rangle + \langle db \rangle \langle dy \rangle) \rangle \langle \rangle && [\text{apply axiom}] \\
&\langle (\langle ca \rangle + \langle db \rangle) \langle cx, dy \rangle \rangle && [\text{merge axiom}].
\end{aligned}$$

<sup>11</sup> The element  $a \in \mathcal{F}$  is a function and the element  $ca \in \mathcal{CF}$  is a stream
function. Likewise, the element  $x \in X$  is an object and the element  $cx \in \mathcal{CX}$  is a stream object.
When the element class is obvious from context, this strict terminology is not adhered to.

<sup>12</sup> Every equivalence relation is reflexive, symmetric, and transitive such that  $x \sim x$  (reflexive);
if  $x \sim y$  then  $y \sim x$  and  $[x] = [y]$  (symmetric); and if  $x \sim y$  and  $y \sim z$ , then  $x \sim z$
(transitive).

<sup>13</sup> In the case when  $|a (x)| = n$  and  $n > 1$ ,

$$\langle (c \cdot d)a (x) \rangle = \langle (c \cdot d)a (x)_1, (c \cdot d)a (x)_2, \dots, (c \cdot d)a (x)_n \rangle.$$

of the equation only has one function. Therefore,

$$\begin{aligned}
ca + db &= (c + d)a \\
ca + da &= (c + d)a & [a = b] \\
\langle x \rangle ca + da &= \langle x \rangle (c + d)a & [\text{apply } 1x] \\
\langle x \rangle ca + da &= \langle (c + d)y \rangle & [\text{apply axiom}] \\ ((\langle x \rangle ca) + ((\langle x \rangle da) &= \langle (c + d)y \rangle & [\text{split axiom}] \\
\langle cy \rangle + ((\langle x \rangle da) &= \langle (c + d)y \rangle & [\langle x \rangle ca = \langle cy \rangle] \\
\langle cy \rangle + \langle dy \rangle &= \langle (c + d)y \rangle & [\langle x \rangle da = \langle dy \rangle] \\
\langle cy, dy \rangle &= \langle (c + d)y \rangle & [\text{merge axiom}] \\
\langle (c + d)y \rangle &= \langle (c + d)y \rangle & [\text{bulk axiom}].
\end{aligned}$$

The abelian group  $\langle \mathcal{CF}, + \rangle$  must also be associative such
that  $(ca + db) + ef = ca + (db + ef)$ . If  $f (x) = w$ , then

$$\begin{aligned} (ca + db) + ef &= ca + (db + ef) \\
\langle x \rangle ((ca + db) + ef) &= \langle x \rangle (ca + (db + ef)) & [\text{apply } 1x] \\ ((\langle cy \rangle + \langle dz \rangle) + \langle ew \rangle &= \langle cy \rangle + ((\langle dz \rangle + \langle ew \rangle) & [\text{split/apply}] \\
\langle cy, dz \rangle + \langle ew \rangle &= \langle cy \rangle + \langle dz, ew \rangle & [\text{merge}] \\
\langle cy, dz, ew \rangle &= \langle cy, dz, ew \rangle & [\text{merge}].
\end{aligned}$$

The zero element  $0 \in \mathcal{CF}$  is the element  $(0, 0) \in \mathcal{C} \times \mathcal{F}$ ,
where  $0 + ca = ca + 0 = ca$  as

$$\begin{aligned}
0 + ca &= ca \\
\langle x \rangle 0 + ca &= \langle x \rangle ca & [\text{apply } 1x] \\ ((\langle x \rangle 0) + ((\langle x \rangle ca) &= \langle x \rangle ca & [\text{split axiom}] \\ ((\langle x \rangle 0) + \langle cy \rangle &= \langle cy \rangle & [\langle x \rangle ca = \langle cy \rangle] \\
\langle 0\emptyset \rangle + \langle cy \rangle &= \langle cy \rangle & [(1x)00 = 0\emptyset] \\
\langle \rangle + \langle cy \rangle &= \langle cy \rangle & [\langle 0\emptyset \rangle = \langle \rangle] \\
\langle cy \rangle &= \langle cy \rangle & [\text{merge axiom}].
\end{aligned}$$

Given the previous proof of commutativity,  $0 + ca = ca + 0$ . Finally, the abelian
group  $\langle \mathcal{CF}, + \rangle$  must support additive inverses such that  $ca - ca = ca + (-ca) = 0$ .

$$\begin{aligned}
ca + (-ca) &= 0 \\
\langle x \rangle (ca + (-ca)) &= \langle x \rangle 0 & [\text{apply } 1x] \\
\langle x \rangle (ca + (-ca)) &= \langle \rangle & [\langle 0x \rangle = \langle \rangle] \\ ((\langle x \rangle ca) + ((\langle x \rangle (-ca)) &= \langle \rangle & [\text{split axiom}] \\
\langle cy \rangle + ((\langle x \rangle (-ca)) &= \langle \rangle & [\langle x \rangle ca = \langle cy \rangle] \\
\langle cy \rangle + \langle -cy \rangle &= \langle \rangle & [\langle x \rangle (-ca) = \langle -cy \rangle] \\
\langle cy, -cy \rangle &= \langle \rangle & [\text{merge axiom}] \\
\langle 0y \rangle &= \langle \rangle & [\text{bulk axiom}] \\
\langle \rangle &= \langle \rangle & [\langle 0x \rangle = \langle \rangle].
\end{aligned}$$

The multiplicative monoid  $\langle \mathcal{CF}, \cdot \rangle$  must be associative such
that  $(ca \cdot db) \cdot ef = ca \cdot (db \cdot ef)$ . If  $a (x) = y$ ,  $b (y) = z$ ,

and  $f (z) = w$ , then

$$\begin{aligned} (ca \cdot db) \cdot ef &= ca \cdot (db \cdot ef) \\ (cd)ab \cdot ef &= ca \cdot (de)bf & [\text{stream mult}] \\
\langle x \rangle (cd)ab \cdot ef &= \langle x \rangle ca \cdot (de)bf & [\text{apply } 1x] \\
\langle (c \cdot d)z \rangle ef &= \langle cy \rangle (de)bf & [\text{apply axiom}] \\
\langle ((c \cdot d) \cdot e)w \rangle &= \langle (c \cdot (d \cdot e))w \rangle & [\text{apply axiom}] \\
\langle (c \cdot d \cdot e)w \rangle &= \langle (c \cdot d \cdot e)w \rangle & [\cdot \text{ is associative in } \mathcal{C}].
\end{aligned}$$

A ring with unity requires that there exists a multiplicative identity  $1 \in \mathcal{CF}$ . This is the
element  $(1, 1) \in \mathcal{C} \times \mathcal{F}$ . The equality  $ca \cdot 1 = 1 \cdot ca = ca$  holds given that

$$\begin{aligned}
ca \cdot 1 &= ca \\
ca \cdot 11 &= ca & [1 \in \mathcal{CF} = (1, 1) \in \mathcal{C} \times \mathcal{F}] \\ (c \cdot 1)(a \cdot 1) &= ca & [\text{stream mult}] \\
c (a \cdot 1) &= ca & [c \cdot 1 = c] \\
ca &= ca & [a \cdot 1 = a].
\end{aligned}$$

A similar deduction can be used to prove that  $1 \cdot ca = ca$ .

The complete ring  $\langle \mathcal{CF}, +, \cdot \rangle$  must be both right and left distributive. With respect to
right distributivity, it must be the case that  $(ca + db)ef = (ca \cdot ef) + (db \cdot ef)$ .
If  $a (x) = y$ ,  $b (x) = z$ ,  $e (y) = w$  and  $e (z) = u$ , then

$$\begin{aligned} (ca + db)ef &= (ca \cdot ef) + (db \cdot ef) \\
\langle x \rangle (ca + db)ef &= \langle x \rangle (ca \cdot ef) + (db \cdot ef) & [\text{apply } 1x] \\
\langle cy, dz \rangle ef &= \langle x \rangle (ca \cdot ef) + (db \cdot ef) & [\text{split/merge}] \\
\langle cy, dz \rangle ef &= \langle (c \cdot e)w, (d \cdot e)u \rangle & [\text{split/merge}] \\
\langle (c \cdot e)w, (d \cdot e)u \rangle &= \langle (c \cdot e)w, (d \cdot e)u \rangle & [\text{apply}].
\end{aligned}$$

With respective updated function definitions, a similar deduction can be used to prove the left distributive
property  $ef (ca + db) = (ef \cdot ca) + (ef \cdot db)$ . Thus,  $\langle \mathcal{CF}, +, \cdot \rangle$  is a ring
with unity.  $\square$

An important feature of the stream ring is that function application, stream merging, and object bulking do not have to
occur in a lock-step fashion. The stream ring's axioms entail a *lazy evaluation* strategy that can evaluate an
expression using depth-first semantics (save space), breadth-first semantics (save time), or any arbitrary hybrid of the
two.

**Theorem 5.** Streams are atemporal. There are no requirements to the order in which functions are applied, streams are
merged, or objects are bulked.

*Proof.* With respect to function application and stream merging, the following derivation demonstrates that the objects
of the outgoing stream from branch  $b$  can be processed by the subsequent function  $c$  even before the in-

coming objects of  $a$  are processed.

$$\begin{aligned}
\langle x \rangle (a+b)c &= \langle ac (x), bc (x) \rangle & [\cdot \text{ is distributive}] \\ ((\langle x \rangle a) + (\langle x \rangle b))c &= \langle ac (x), bc (x) \rangle & [\text{split axiom}] \\ ((\langle x \rangle a) + \langle b (x) \rangle)c &= \langle ac (x), bc (x) \rangle & [\text{apply axiom}] \\ (\langle x \rangle a)c + \langle b (x) \rangle c &= \langle ac (x), bc (x) \rangle & [\cdot \text{ is distributive}] \\ (\langle x \rangle a)c + \langle bc (x) \rangle &= \langle ac (x), bc (x) \rangle & [\text{apply axiom}] \\
\langle a (x) \rangle c + \langle bc (x) \rangle &= \langle ac (x), bc (x) \rangle & [\text{apply axiom}] \\
\langle ac (x) \rangle + \langle bc (x) \rangle &= \langle ac (x), bc (x) \rangle & [\text{apply axiom}] \\
\langle ac (x), bc (x) \rangle &= \langle ac (x), bc (x) \rangle & [\text{merge axiom}].
\end{aligned}$$

The following derivation demonstrates that stream objects can be bulked prior to function application.

$$\begin{aligned}
\langle cx, dx \rangle a &= \langle (c+d)a (x) \rangle & [\text{apply axiom}] \\
\langle (c+d)x \rangle a &= \langle (c+d)a (x) \rangle & [\text{bulk axiom}] \\
\langle (c+d)a (x) \rangle &= \langle (c+d)a (x) \rangle & [\text{apply axiom}].
\end{aligned}$$

Finally, the next derivation demonstrates that stream objects can be bulked after function application.

$$\begin{aligned}
\langle cx, dx \rangle a &= \langle (c+d)a (x) \rangle & [\text{apply axiom}] \\
\langle ca (x), da (x) \rangle &= \langle (c+d)a (x) \rangle & [\text{apply axiom}] \\
\langle (c+d)a (x) \rangle &= \langle (c+d)a (x) \rangle & [\text{bulk axiom}].
\end{aligned}$$

The previous derivations prove that there is no required order to the application of the apply, merge, and bulk stream
axioms. These axioms can be leveraged at anytime without effecting the ultimate result of the computation.  $\square$

The following theorem demonstrates the *universal functional commutativity of coefficients*. The general idea is that
any function  $ca \in \mathcal{CF}$  can be rewritten as  $c1 \cdot 1a$ , where in  $c1$ ,  $1 \in \mathcal{F}$  and
in  $1a$ ,  $1 \in \mathcal{C}$ . Due to the commutative property of 1 in a ring's multiplicative monoid
( $a \cdot 1 = 1 \cdot a$ ),  $c1$  can be moved forward or backward through an expression. If  $c \in \mathcal{C}$
and  $a, b, f \in \mathcal{F}$ , then

$$ca \cdot 1b \cdot 1f = 1a \cdot 1b \cdot 1f \cdot c1.$$

It is important to note that unless the coefficient ring is commutative ( $c \cdot d = d \cdot c$ ), once a non-identity
coefficient is encountered by the “floating coefficient,” it must be left (or right) multiplied by the non-identity
coefficient.<sup>15</sup> That is, in a non-commutative coefficient ring, if  $d \in \mathcal{C}$ , then

$$ca \cdot 1b \cdot df = 1a \cdot 1b \cdot (cd)f = 1a \cdot 1b \cdot 1f \cdot (cd)1.$$

Another explanation for the above equalities is that stream multiplication is not bijective and therefore, is

not uniquely invertible. When the two stream functions  $ca, db \in \mathcal{CF}$  are multiplied
as  $(c \cdot d)(a \cdot b) = (cd)ab$ , the transformation leads to a loss of information as to which function had which
coefficient. Assuming that  $c, d, a$  and  $b$  are all prime elements in their respective rings,<sup>16</sup> the
function  $(cd)ab$  has the following factors:

$$\begin{aligned} (cd)a \cdot 1b &= (cd)ab \\
ca \cdot db &= (cd)ab \\
1a \cdot (cd)b &= (cd)ab.
\end{aligned}$$

As a side, in any non-commutative monoid  $\langle \mathcal{CF}, \cdot \rangle$ , any  $n$ -composite of prime functions
and respective prime coefficients can be factored in  $2^n - 1$  ways.

**Theorem 6.** If  $\langle \mathcal{CF}, +, \cdot \rangle$  is a stream ring,  $c, d \in \mathcal{C}$ ,
and  $a, b \in \mathcal{F}$ , then

1. $ca = a \cdot c$
2. $ca + cb = c (a + b) = (a + b)c$
3. $(ca)^n = c^n a^n = a^n \cdot c^n$

*Proof.* The theorem's equalities will be rigorously deduced from the stream ring axioms.

$$1. \ ca = a \cdot c$$

$$\begin{aligned}
ca &= a \cdot c \\
11 \cdot ca &= a \cdot c & [1 \cdot a = a] \\ (1 \cdot c)(1 \cdot a) &= a \cdot c & [\text{stream mult}] \\ (1 \cdot c)(a \cdot 1) &= a \cdot c & [1 \cdot a = a \cdot 1 = a] \\
1a \cdot c1 &= a \cdot c & [\text{stream mult}] \\
a \cdot c &= a \cdot c & [1a = a \text{ and } c1 = c].
\end{aligned}$$

When the coefficient and the function are not clear from context,  $a \cdot c = 1a \cdot c1$ .

$$2. \ ca + cb = c (a + b)$$

$$\begin{aligned}
ca + cb &= c (a + b) \\
ca + cb &= c1 (a + b) & [c = c1] \\
ca + cb &= c1 (1a + 1b) & [a = 1a \text{ and } b = 1b] \\
ca + cb &= (c1 \cdot 1a) + (c1 \cdot 1b) & [\cdot \text{ is left distrib}] \\
ca + cb &= ca + cb & [\text{stream mult}].
\end{aligned}$$

Given the first equality in the theorem, it is also true that  $c (a + b) = (a + b)c$ .

<sup>15</sup> In a standard ring, addition is commutative ( $a + b = b + a$ ), but multiplication is not
( $a \cdot b \neq b \cdot a$ ). In a commutative ring, both addition and multiplication are commutative.

<sup>16</sup> In number theory, a prime number can only be represented as the product of 1 and itself and thus, a prime
number has no *proper* factors. The concept of primes generalizes to any algebraic group, where a prime element is any
element of the group that has no proper factors.

$$3. (ca)^n = c^n a^n$$

$$\begin{aligned} (ca)^n &= c^n a^n \\ ca \cdot ca \cdot (ca)^{n-2} &= c^n a^n \quad [\text{exponent expansion}] \\ (c \cdot c)(a \cdot a) \cdot (ca)^{n-2} &= c^n a^n \quad [\text{stream mult}] \\ c^2 a^2 \cdot (ca)^{n-2} &= c^n a^n \quad [\text{stream mult}] \\ c^n a^n &= c^n a^n \quad [\text{induction}]. \end{aligned}$$

Given the first equality in the theorem, it is also true that  $c^n a^n = a^n \cdot c^n$ . □

**Corollary 1.** In a commutative coefficient ring, where  $\langle \mathcal{C}, \cdot \rangle$  is a commutative
monoid, the greatest common factor of the function coefficients in an additive stream is both left and right
distributive. If  $c, d \in \mathcal{C}$  and  $a, b \in \mathcal{F}$ , then

$$ca + (cd)b = c (a + db) = (a + db)c.$$

*Proof.*

$$\begin{aligned} ca + (cd)b &= c (a + db) \\ (c1 \cdot 1a) + ((cd)1 \cdot 1b) &= c (a + db) \quad [ca = c1 \cdot 1a] \\ c1 ((11 \cdot a) + (d1 \cdot 1b)) &= c (a + db) \quad [\cdot \text{ is left distrib}] \\ c ((11 \cdot a) + (d1 \cdot 1b)) &= c (a + db) \quad [c1 \equiv c] \\ c (a + (d1 \cdot 1b)) &= c (a + db) \quad [11 \cdot 1a = a] \\ c (a + db) &= c (a + db) \quad [c1 \cdot 1a = ca]. \end{aligned}$$

Given the above derivation, the universal functional commutativity of coefficients, and the commutative monoid property
that  $c \cdot d = d \cdot c$ , the greatest common factor of the function coefficients is also right distributive and
thus,

$$ca + (cd)b = (a + db)c.$$

□

**Corollary 2.** In a standard ring where  $\langle \mathcal{C}, \cdot \rangle$  is not a commutative monoid, only the
greatest common “left”-factor is left distributive and only the greatest common “right”-factor is right distributive.
If  $c, d \in \mathcal{C}$  and  $a, b \in \mathcal{F}$ , then

$$ca + (cd)b = c (a + db)$$

and

$$ca + (dc)b = (a + db)c.$$

*Proof.* The proof for left distributivity in Corollary 1 applies to the first equality. For the second equality,
since  $a \cdot b \neq b \cdot a$  in a non-commutative ring, then the following

derivation proves that the largest “right”-factor is right distributive. If  $c, d \in \mathcal{C}$
and  $a, b \in \mathcal{F}$ , then

$$\begin{aligned} ca + (dc)b &= (a + db)c \\ (c1 \cdot 1a) + ((dc)1 \cdot 1b) &= (a + db)c \quad [ca = c1 \cdot 1a] \\ ((11 \cdot 1a) + (d1 \cdot 1b))c1 &= (a + db)c \quad [\cdot \text{ is right distrib}] \\ ((11 \cdot 1a) + (d1 \cdot 1b))c &= (a + db)c \quad [c \equiv c1] \\ (a + (d1 \cdot 1b))c &= (a + db)c \quad [11 \cdot 1a = a] \\ (a + db)c &= (a + db)c \quad [c1 \cdot 1a = ca]. \end{aligned}$$

□

## IV. THE FUNCTION SUBRINGS

In every function ring  $\langle \mathcal{F}, +, \cdot \rangle$ , there are three logical subsets of the functions
in  $\mathcal{F}$ : map, filter, and flatmap functions [3]. Each of these three subsets form a ring with unity and each
ring has a unique set of algebraic properties. A fourth subset of reduce functions will be added to the stream ring
set  $\mathcal{F}$ . The reduce functions form a *near-ring*, where multiplication is not right distributive over
addition.<sup>17</sup>

1. **map** :  $X \rightarrow Y$  maps an incoming  $X$  object to an outgoing  $Y$  object. [one-to-one]<sup>18</sup>
2. **filter** :  $X \rightarrow X \cup \emptyset$  uses a predicate to determine whether to emit the incoming  $X$
   object to the outgoing stream or not. [one-to- (one or none)]
3. **flatMap** :  $X \rightarrow Y^*$  maps an incoming  $X$  object to zero or more outgoing  $Y$  objects. If more
   than one  $Y$  object is produced, then they are linearized into the outgoing stream. They are not mapped as a
   set. [one-to-many]
4. **reduce** :  $X^* \rightarrow Y$  gathers all the  $X$  stream objects of the incoming stream and yields a single
   outgoing  $Y$  stream object. [many-to-one]

The subset  $\mathcal{F}_m$  is the set of all **map** functions, the subset  $\mathcal{F}_f$  is the set of all
**filter** functions, the subset  $\mathcal{F}_{fm}$  is the set of all **flatMap** functions, and the
subset  $\mathcal{F}_r$  is the set of all **reduce** functions such that

$$\mathcal{F} = \mathcal{F}_m \cup \mathcal{F}_f \cup \mathcal{F}_{fm} \cup \mathcal{F}_r.$$

Note that these are not disjoint sets. It will be demonstrated that  $\mathcal{F}_f \subset \mathcal{F}_{fm}$
and  $\mathcal{F}_m \subset \mathcal{F}_{fm}$ .

<sup>17</sup> The inclusion of the reduce function subset makes  $\langle \mathcal{F}, +, \cdot \rangle$  a near-ring as
this is the ring-type for which all the axioms and theorems are guaranteed to hold. However, standard ring theory
applies throughout most expressions and when reduce functions are encountered, near-ring theory is required when
performing algebraic manipulations.

<sup>18</sup> The term “one-to-one” does not refer to the function being injective, but that it maps one input to one
output.

**Definition 4** (Functionally Closed). Any ring  $\langle A, +, \cdot \rangle$  is closed with respect to addition and
multiplication if, for any two elements  $a, b \in A$ ,  $a + b \in A$  and  $ab \in A$ . Every multi-typed function
ring  $\langle \mathcal{F}, +, \cdot \rangle$  is not closed because if  $a : X \rightarrow Y$
and  $b : W \rightarrow Z$ , then  $a + b \notin \mathcal{F}$  and  $ab \notin \mathcal{F}$  as these compositions are
undefined. However,  $\langle \mathcal{F}, +, \cdot \rangle$  is considered *functionally closed*
if  $a, b, c \in \mathcal{F}$ ,  $a : X \rightarrow Y$ ,  $b : Y \rightarrow Z$ ,  $c : X \rightarrow Y$ ,
then  $ab \in \mathcal{F}$  and  $a + c \in \mathcal{F}$ . A functional closure is a closure over those compositions for
which function input and output types are respected.

**Definition 5** (Stream Cardinality). The cardinality of the set  $A$  is the number of elements in the set and is
denoted  $|A|$ . Thus,  $|\{x, y, z\}| = 3$ . The cardinality of a stream  $\mathbf{x}$  is the sum of the absolute
value of the coefficients of the elements of the stream and is denoted  $|\mathbf{x}|$ . Thus,
if  $\mathcal{C} = \mathbb{Z}$ ,  $|\langle -1x, 2y, 4z \rangle| = 7$ .

**Definition 6** (Multiplicative Inverses). The algebraic structure  $\langle A, \cdot \rangle$  is a multiplicative
*group* if for every  $a \in A$  there is an  $a^{-1} \in A$  such that  $a \cdot a^{-1} = a^{-1} \cdot a = 1$ . The
elements  $a, a^{-1} \in A$  are multiplicative inverses of each other. The following equalities can be proved using
similar deductions as the additive inverse equalities in Theorem 1. If  $\langle A, \cdot \rangle$  is a group
and  $a, b, c \in A$ , then

1. $ab = ac \implies b = c$
2. $ba = ca \implies b = c$
3. $ab = 1 \implies a = b^{-1}$  and  $b = a^{-1}$
4. $(ab)^{-1} = b^{-1}a^{-1}$
5. $(a^{-1})^{-1} = a$ .

### A. The Map Ring

The map ring  $\langle \mathcal{F}_m, +, \cdot \rangle$  contains the set of all map functions  $a : X \rightarrow Y$
where, for each incoming object of type  $X$ ,  $a$  will map it to one and only one outgoing object of type  $Y$ .

**Theorem 7.** The abelian map group  $\langle \mathcal{F}_m, + \rangle$  is not functionally closed.

*Proof.* For every  $a, b \in \mathcal{F}_m$  such that  $b \neq -a$ , if  $a : X \rightarrow Y$
and  $b : X \rightarrow Y$ , then  $a + b \in \mathcal{F}_{fm}$  as  $|\langle x \rangle (a + b)| = 2$ . The
function  $a + b : X \rightarrow Y^*$  maps one object in  $X$  to two objects in  $Y$ .
Thus,  $a + b \notin \mathcal{F}_m$  and  $\langle \mathcal{F}_m, + \rangle$  is not functionally closed.  $\square$

**Theorem 8.** The map monoid  $\langle \mathcal{F}_m, \cdot \rangle$  is functionally closed.

*Proof.* For every  $a, b \in \mathcal{F}_m$  such that  $a \neq 0 \neq b$ ,
if  $a : X \rightarrow Y$ ,  $b : Y \rightarrow Z$ ,  $a (x) = y$ , and  $b (y) = z$ ,
then  $\langle x \rangle ab = z$ . Thus,  $ab : X \rightarrow Z$ ,  $ab \in \mathcal{F}_m$ ,
and  $\langle \mathcal{F}_m, \cdot \rangle$  is functionally closed.  $\square$

The map function  $a : X \rightarrow Y$  is *injective* if it maps every object of  $X$  to a unique object in  $Y$ .
That is, if  $a (x_1) = a (x_2)$ , then  $x_1 = x_2$ . The function  $a$  is *surjective* if every object in  $Y$  has a
mapping from one or more objects in  $X$ . That is,  $\bigcup_{x \in X} a (x) = Y$ . If function  $a$  is both injective
and surjective then it is *bijecive* and there exists an inverse function  $a^{-1} : Y \rightarrow X$  such
that  $aa^{-1} = 1$  and  $a^{-1}a = 1$ , where  $aa^{-1} : X \rightarrow X$  and  $a^{-1}a : Y \rightarrow Y$ . A
bijective function defines an isomorphism between the sets  $X$  and  $Y$  as  $a$  and  $a^{-1}$  can be used to move
between the sets without loss of information. The set of all bijective functions
in  $\mathcal{F}_{bm} \subset \mathcal{F}_m$  form the group  $\langle \mathcal{F}_{bm}, \cdot \rangle$  and can
leverage the axioms and theorems provided by multiplicative inverses.

### B. The Filter Ring

The filter ring  $\langle \mathcal{F}_f, +, \cdot \rangle$  contains the set of all filter
functions  $a : X \rightarrow X \cup \emptyset$ . The predicate

$$p : X \rightarrow \{\mathbf{true}, \mathbf{false}\}$$

determines whether or not an object  $x \in X$  has some “ $p$ ”-property. If it does, the predicate returns **true**,
else it returns **false**. Every filter function is founded on some predicate. If the predicate returns **true**, then
the filter function passes the object to the outgoing stream, else if the predicate returns **false**, it does not pass
the object to the outgoing stream. In general, if  $p$  is a predicate, then the filter function  $a$  is defined as

$$a (x) = \begin{cases} x & \text{if } p (x) = \mathbf{true}, \\ \emptyset & \text{otherwise.} \end{cases}$$

**Theorem 9.** The filter monoid  $\langle \mathcal{F}_f, \cdot \rangle$  is both idempotent and commutative.

*Proof.* If  $a \in \mathcal{F}_f$ , then  $a (x) = x$  or  $a (x) = \emptyset$ . It must then be true
that  $a (a (x)) = x$  or  $a (a (x)) = \emptyset$ , respectively.<sup>19</sup> Via induction, once a filter has been
applied, a repeated application of that filter on the same object will not alter the result of the stream and thus,
multiplication is *idempotent* in  $\langle \mathcal{F}_f, \cdot \rangle$ .
Symbolically,  $a \cdot a \cdot \dots \cdot a = a^n = a$ . Finally, if  $a, b \in \mathcal{F}_f$ ,  $a (x) = x$ ,
and  $b (x) = x$ , then  $a (b (x)) = b (a (x)) = x$ . If  $b (x) = \emptyset$ ,
then  $a (b (x)) = b (a (x)) = \emptyset$ . Lastly, if  $a (x) = \emptyset$  as well,
then  $a (b (x)) = b (a (x)) = \emptyset$ . Thus,  $a \cdot b = b \cdot a$  and multiplication is commutative
in  $\langle \mathcal{F}_f, \cdot \rangle$ .  $\square$

**Corollary 3.** There are no multiplicative inverses in the filter monoid  $\langle \mathcal{F}_f, \cdot \rangle$ .

<sup>19</sup> It is always the case that for every non-reduce function  $a$ ,  $a (\emptyset) = \emptyset$
as  $\langle \rangle a = \langle \rangle$  since  $a$  has no incoming objects to process and thus, no outgoing objects
to emit.