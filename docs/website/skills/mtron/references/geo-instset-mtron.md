---
name: geo instruction set
description: |
  The GeoJSON (RFC 7946) model at `/m/math/geo`, in six types: a point IS a position, a line IS an ordered list of
  points, a polygon IS a list of lines — the kind is the type's name, and there is no `type` or `coordinates` key
  anywhere in the model. Every `Multi*` form is a coefficient, a Feature is a rec with a `geo:`-prefixed fiber, and the
  JSON vocabulary lives only in `ObjGeoJSONSerializer`. Plus the geodesic and planar measures (distance, length,
  area), the containment filter (`has`/`inside`), the shape reductions as casts (`as(point::T)` is the centroid,
  `as(bbox2d::T)` the envelope), and the two encodings (GeoJSON and WKT).
  TRIGGER: when reading or writing a GeoJSON document, building a geometry from a list or from text, measuring a
  geometry, asking whether one geometry contains, lies inside or intersects another, handing a geometry to a spatial
  database as WKT, or declaring a native spatial pushdown.
---

# the geographic instruction set (`/m/math/geo`)

`/m/math/geo` is GeoJSON with the JSON taken out of it. The wire format's `type`, `coordinates`, `geometry`,
`properties` and `features` keys exist because JSON has no type system: a point cannot say "I am a position", so the
document says it for the point. mtron does not need the help. **A point IS a position. A line IS an ordered list of
points. A polygon IS a list of lines.** The model is the value, the kind is the value's type name, and everything
GeoJSON adds on top — the `Multi*` synonyms, the feature wrapper, the properties bag — is a mapping that happens at the
boundary and nowhere else.

**The names carry no `geo_` prefix: the instruction set's namespace IS the prefix.**

```mtron
mtron> import(/m/math/geo,geo)
...
```
## the model

```
lst::T@geometry                                         any list: a shape on the earth
geometry::T[?[real::T,real::T,real{?}::T]]@point        a coordinate pair, or a triple with an altitude
geometry::T[?[point{+}::T]]@line                        an ORDERED list of points
geometry::T[?[line{+}::T]]@polygon                      a shell line, then zero or more hole lines

lst::T@bbox                                             the nominal base of an envelope
bbox::T[?[real::T,real::T,real::T,real::T]]@bbox2d      [λmin, φmin, λmax, φmax]
bbox::T[?[real::T,real::T,real::T,real::T,real::T,real::T]]@bbox3d   [λmin, φmin, hmin, λmax, φmax, hmax]
```

Six types. The two bases declare **no poly** — a refinement declares its own shape, so `geometry` is the family label
and `point`, `line`, `polygon` are the shapes:

```
lst
├── geo:geometry          "a shape on the earth" — the instruction domain
│   ├── geo:point         [λ, φ[, h]]
│   ├── geo:line          [point{+}]
│   └── geo:polygon       [line{+}]
└── geo:bbox
    ├── geo:bbox2d        [λmin, φmin, λmax, φmax]
    └── geo:bbox3d        [λmin, φmin, hmin, λmax, φmax, hmax]
```

There is **no `position`, no `multipoint`, no `multilinestring`, no `multipolygon`, no `geometrycollection` and no
`feature` type.** A multi-point is a *bag* of points (`point{2,}::T`), a multi-line is a bag of lines, a collection is a
bag of geometries, and a feature is a rec — coefficients and fields, not types. The bag/list distinction is real (a bag
has no order, a line does) and JSON has no way to say it; that difference is the serializer's business.

A `point` is two or more reals: the RFC's two or three, plus the further elements §3.1.1 allows after the altitude. A
`line` needs at least one point and a `polygon` at least one line, and construction is checked against exactly those
declarations:

```mtron
mtron> geo:point::[-123.13,44.47]                          [-- a point: a coordinate pair --]
==>geo:point::[-123.13,44.47]
mtron> geo:point::[-123.13,44.47,1240.0]                   [-- and with an altitude (Powell Butte, ~1240 m) --]
==>geo:point::[-123.13,44.47,1240.0]
mtron> geo:line::[[-123.13,44.47],[-121.18,45.61]]         [-- a line: Portland to The Dalles --]
==>geo:line::[[-123.13,44.47],
              [-121.18,45.61]]
mtron> geo:polygon::[[[-123.2,44.4],[-122.9,44.4],[-122.9,44.6],[-123.2,44.6],[-123.2,44.4]]]
==>geo:polygon::[[[-123.2,44.4],
                  [-122.9,44.4],
                  [-122.9,44.6],
                  [-123.2,44.6],
                  [-123.2,44.4]]]
```
```mtron
mtron> geo:point::[-123.13]                        [-- fail: not enough coordinates --]
==>fail::[parse error at line 1, col 1: obj does not match geo:point::T
     [[-123.13]
         X=>
     geometry::T[?[real::T,real::T,real{?}::T]][ctor?point<=(#{*}::T){<j>}]@/m/math/geo/point
     ────────────────────────────────────────────────────────────────────────────────────────
     [==>-123.13,X=>noobj,==>noobj]]
     predicate apply failed on execution
   	while parsing: geo:point::[-123.13]]
mtron> geo:point::[1,2]                            [-- fail: mtron's int is not a real --]
==>fail::[parse error at line 1, col 1: obj does not match geo:point::T
     [[1,2]
         X=>
     geometry::T[?[real::T,real::T,real{?}::T]][ctor?point<=(#{*}::T){<j>}]@/m/math/geo/point
     ────────────────────────────────────────────────────────────────────────────────────────
     [X=>1,X=>2,==>noobj]]
     predicate apply failed on execution
   	while parsing: geo:point::[1,2]]
mtron> geo:line::[-123.13,44.47]                   [-- fail: a line is a list of POINTS --]
==>fail::[parse error at line 1, col 1: obj does not match geo:line::T
     [[-123.13,44.47]
         X=>
     geometry::T[?[geometry{+}::T[?[real::T,real::T,real{?}::T]][ctor?point<=(#{*}::T){<j>}]@/m/math/geo/point]][ctor?line<=(#{*}::T){<j>}]@/m/math/geo/line
     ───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
     [X=>-123.13,X=>44.47]]
     predicate apply failed on execution
   	while parsing: geo:line::[-123.13,44.47]]
```
## what the wire adds, and where

The mapping table is the whole of GeoJSON's extra vocabulary, and every row is a serializer rule rather than a type:

| the model                                                    | the document                     |
|--------------------------------------------------------------|----------------------------------|
| `point::[λ,φ]`                                               | `{"type":"Point","coordinates":[λ,φ]}` |
| `line::[[λ,φ],…]`                                            | `{"type":"LineString","coordinates":[[λ,φ],…]}` |
| `polygon::[[[λ,φ],…],…]`                                     | `{"type":"Polygon","coordinates":[[[λ,φ],…],…]}` |
| a **bag** of points (`point{2,}::T`)                         | `{"type":"MultiPoint","coordinates":[…]}` |
| a bag of lines                                               | `{"type":"MultiLineString",…}`    |
| a bag of polygons                                            | `{"type":"MultiPolygon",…}`       |
| a bag of mixed geometries                                    | `{"type":"GeometryCollection","geometries":[…]}` |
| a **rec with a `geo:`-prefixed fiber** (`rec[geo:+]::T`)     | `{"type":"Feature","geometry":{…},"properties":{…}}` |
| a bag of such recs                                           | `{"type":"FeatureCollection","features":[…]}` |
| `bbox2d` / `bbox3d`                                          | the `"bbox"` field on an object  |

The document's `type` is consumed on the way in (the tid says the kind) and derived on the way out; its `coordinates`
is the value itself; a `Feature`'s `properties` come back **into** the rec, flattened, because properties are just the
rec's other fields. `ObjGeoJSONSerializer` is the only place any of these words appear.

```mtron
mtron> geo:point::[-123.13,44.47].as(json::T)      [-- json::'{"type":"Point","coordinates":[-123.13,44.47]}' --]
==>
   json::'{"type":"Point","coordinates":[-123.13,44.47]}'
   
   
mtron> geo:line::[[-123.13,44.47],[-121.18,45.61]].as(json::T)
==>
   json::'{"type":"LineString","coordinates":[[-123.13,44.47],[-121.18,45.61]]}'
   
   
mtron> '{"type":"Point","coordinates":[-123.13,44.47]}'.as(geo:point::T)   [-- back to the bare point --]
==>point::[-123.13,44.47]
```
### a feature is a rec, and the wild-fiber is how you say so

Declare the fiber with the `geo:` namespace and the rec becomes a feature: everything else on the rec is its
`properties`, and the geometry answers the geo instructions **untyped** — the engine offers the call to the values the
fiber exposes, so the point inside the person receives it.

```mtron
mtron> person -> rec[geo:location]::T[?[name=>str::T]]@person
...
mtron> person::[name=>'marko',geo:location=>geo:point::[-123.0,44.5]]@marko
==>person::[name=>'marko',
            geo:location=>geo:point::[-123.0,44.5]]@marko
mtron> *marko.type()                                         [-- the point type: the geometry at the fiber answers --]
mtron> *marko.inside(geo:bbox2d::[-123.2,44.4,-122.9,44.6])  [-- the point itself, since the filter answers the contained --]
==>person::[name=>'marko',
            geo:location=>geo:point::[-123.0,44.5],
            location=>geo:point::[-123.0,44.5]]
mtron> *marko>>geo:location                                   [-- the point itself --]
==>geo:point::[-123.0,44.5]
```
On the way out that rec is a GeoJSON `Feature` — `geometry` is the fiber's value, `properties` is `{name: "marko"}` —
and on the way back in the properties are flattened into the rec again. No feature type exists at either end.

## the questions

| instruction                                          | answer                                                                        |
|------------------------------------------------------|-------------------------------------------------------------------------------|
| `g.type()`                                           | the kind — the type's name, `point::T`, `line::T`, `polygon::T`                |
| `geo:coordinates()`                                  | every stored position, in document order, as stamped points                   |
| `geo:distance(g)`                                    | geodesic metres to `g`; `0` when they intersect (a point in a polygon)        |
| `geo:length()`                                       | geodesic metres of every polyline and polygon boundary                        |
| `geo:area()`                                          | square metres of the polygonal part, shell positive and holes negative        |
| `has(g)` / `geo:inside(g)`                       | the containment FILTER: `has` answers the container, `inside` the contained; both take a geometry or an envelope |
| `geo:wkt()` / `as(json::T)` / `as(<kind>::T)`        | the two string encodings, out and in                                          |

The two shape reductions are **casts**, not instructions, because construction and conversion are the same door:

| cast | what it is |
|------|------------|
| `plane::[…].as(point::T)` | the centroid — a point from any geometry |
| `line::[…].as(bbox2d::T)` | the envelope — the box around every stored position |
| `bbox3d::[…].as(bbox2d::T)` | the 2D box: z dropped, lossy and stated |

```mtron
mtron> geo:point::[-123.13,44.47].type()                            [-- the point type --]
mtron> geo:line::[[-123.13,44.47],[-121.18,45.61]].as(bbox2d::T)    [-- geo:bbox2d::[-123.13,44.47,-121.18,45.61] --]
==>bbox2d::[-123.13,44.47,-121.18,45.61]
mtron> geo:line::[[-123.13,44.47],[-121.18,45.61]].geo:length()     [-- meter::198845.05… — Portland to The Dalles --]
==>meter::198845.0518
mtron> geo:polygon::[[[-123.2,44.4],[-122.9,44.4],[-122.9,44.6],[-123.2,44.6],[-123.2,44.4]]].geo:area()   [-- about 5.3e8 m² --]
==>5.2931E8
mtron> geo:polygon::[[[-123.2,44.4],[-122.9,44.4],[-122.9,44.6],[-123.2,44.6],[-123.2,44.4]]].has(geo:point::[-123.05,44.5])   [-- the polygon itself --]
==>geo:polygon::[[[-123.2,44.4],
                  [-122.9,44.4],
                  [-122.9,44.6],
                  [-123.2,44.6],
                  [-123.2,44.4]]]
mtron> geo:point::[-123.05,44.5].geo:inside(geo:bbox2d::[-123.2,44.4,-122.9,44.6])   [-- the point itself --]
==>geo:point::[-123.05,44.5]
mtron> geo:polygon::[[[-123.2,44.4],[-122.9,44.4],[-122.9,44.6],[-123.2,44.6],[-123.2,44.4]]].as(point::T)   [-- the centroid --]
==>point::[-123.0499,
           44.4999]
```
## what the numbers mean

- **distance** is the great-circle distance (haversine, IUGG mean radius 6371008.8 m) — exact on the sphere to
  centimetres, and `0` when the geometries intersect, so a point inside a polygon is at distance zero rather than at the
  distance to the nearest vertex;
- **length** sums the same distance along each polyline, and along a polygon's boundary (closing the ring);
- **area** is the planar shoelace formula scaled by $R^2\cos\bar\varphi$ at the mean latitude of the stored positions:
  exact for a rectangle whose sides run along parallels and meridians, an approximation otherwise;
- **`has`/`geo:inside`** are exact for a point, a vertex-on-vertex test plus a ray cast otherwise — one predicate, two subjects (the container, or the contained one, which is what a fibered rec preserves);
- the **envelope** is always the axis-aligned box around every stored position.

What is *not* modeled is stated rather than half-done: there is no antimeridian cutting (RFC 7946 §3.1.9), no ring
closure check (a "ring" is a line, and the RFC's first-point-equals-last is the caller's to honour), no altitude in any
measurement, and no 3D envelope on a geometry (a `bbox3d` is a value of its own, not a field the model carries).

## the algebra the rewriter may use

Each geo instruction declares beside itself (`?decq`) what a rewrite may do to it: a native spatial operator may be substituted
only where the laws license it, so **an undeclared instruction is one the optimizer must leave alone.**

| instruction                                     | laws                            | what it licenses                                                                 |
|-------------------------------------------------|---------------------------------|----------------------------------------------------------------------------------|
| `geo:distance`                                  | `pure`                          | no effect, so a rewrite may move or drop it; symmetric, but not `commutative`: geometry × geometry → meter is not closed |
| `has` / `geo:inside`                            | `pure`                          | converses, not inverses (`a.inside(b)` iff `b.has(a)`): a pushdown may swap the operands, no law cancels them |
| `geo:coordinates`, `geo:wkt`                    | `pure`                          | structural projections have no effect, so a rewrite can move or drop them        |
| `geo:area`, `geo:length`                        | `pure`, `blocked`               | measured here, not floatable to a native operator                                |

A geometry is not a ring, a monoid or a lattice: there is no `plus` of two geometries, so the family declares no
carrier algebra: no geo type's `?decq` carries a `theory`. The measures are homomorphisms **to** the reals, not
operations on geometries.

## pushdown

The instructions are for spaces that have **no** spatial support. One that does — PostGIS behind `/m/db/tble`, a
document store's geo operator, a graph database's predicate — should answer `from(<path>).geo:inside(<polygon>)` with an
indexed operator and never materialize the geometry in the VM. `geoRewrite` provides the pushdown factories; nothing is
registered by default, because a pushdown is a claim about a backend, not about this family.