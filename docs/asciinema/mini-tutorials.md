```mtron_pre cast=mini-reference cols=100 rows=16 typing_delay=0.05
[NO_PROMPT][== clone vs. anchor references ==]
5.to(a)
from(a).plus(2)
from(a)
at(a).plus(2)
from(a)
[NO_PROMPT][== sugar coated ==]
a -> 5
*a + 2
*a
@a + 2
*a
```

```mtron_pre cast=mini-arithmetic cols=100 rows=16 typing_delay=0.05
1 + 1
2 * 21
```

```mtron_pre cast=mini-values cols=100 rows=16 typing_delay=0.05
int::42@the_answer
being::[name=>'marko',age=>29]
```

```mtron_pre cast=mini-tensors cols=100 rows=35 typing_delay=0.05 speed_rates="0.5,1,1.5,2" speed_default="1"

```

```mtron_pre cast=mini-fiber-bundle cols=100 rows=35 typing_delay=0.05 speed_rates="0.5,1,1.5,2" speed_default="1"
[NO_PROMPT][== rec polynomials are called fiber bundles ==]
[NO_PROMPT][== type definitions ==]
rec[hp,role]::T[?[name=>str::T,hp=>int::T]]@human                                [-- a human is a structural fiber bundle type w/ two fibers: hp and role --]
rec::T@warrior                                                                   [-- warrior is a nominal type (no predicate nor constructor) --]
warrior::T@ninja                                                                 [-- ninja is a subtype of warrior (nominal hiearchies form subtypes) --]
warrior::T@sumo                                                                  [-- sumo is a subtype of warrior --]
[NO_PROMPT][== instruction definitions ==]
|inst?int<=int(int::T){-*0}@damage                                               [-- damage maps an int to an int --]
|inst?human{?}<=human(){.?[hp=>?>0]}@is_dead                                     [-- is_dead maps a human to maybe a human --]
|inst?warrior<=warrior(){>>=[weapon=>none]}@no_more_war                          [-- no_more_war maps a warrior to a warrior --]
[NO_PROMPT][NO_PROMPT] [== type values ==]
human[int,ninja]::[name=>'marko',hp=>100,role=>ninja::[weapon=>bulldozer]]@marko           [-- marko is a human ninja (fibers typed) --]
human[int,sumo]::[name=>'thunder down under',hp=>100,role=>sumo::[weapon=>self]]@thunder   [-- thunder is a human sumo (fibers typed) --]
@thunder.damage(10).is_dead().else('dead')                                                 [-- thunder's hp fiber is modulated even though damage has an int dom --]
@marko.damage(1).is_dead().else('dead')                                                    [-- the human is deemed lifted while damage applies to its internals --]
@thunder.damage(90).is_dead().else('dead')                                                 [-- thunder loses the battle --]
@marko.no_more_war()                                                                       [-- marko is a human warrior with role being the other fiber --]
[NO_PROMPT][== marko must find new meaning in life...for his trusty bulldozer is no longer ==]
```

```mtron_pre cast=mini-distributed cols=110 rows=32 typing_delay=0.05 speed_rates="0.5,1,1.5,2" speed_default="1"
[HIDDEN]import(/m/math,math)
[HIDDEN]import(/m/math/cat,cat)
[NO_PROMPT][== the fold, written plainly ==]
{1,2,3,4,5,6,7,8}.plus(1).sum()                       [-- 44: one machine does all of it --]
[NO_PROMPT][== and what it compiles to: one chain ==]
{1,2,3,4,5,6,7,8}.plus(1).sum().explain()>>format     [-- start, then plus, then sum -- no fan-out to be had --]
[NO_PROMPT][== fork two peers, dialing each as it comes up ==]
[MAXOUTPUT 2]spawn(<//localhost:1111/store>, <//0>); ws_client::[pattern=><ws://localhost:1111/mtron>]   [-- a node serving /store, then dial it --]
[MAXOUTPUT 2]spawn(<//localhost:1112/store>, <//1>); ws_client::[pattern=><ws://localhost:1112/mtron>]
[NO_PROMPT][== the same expression, byte for byte ==]
{1,2,3,4,5,6,7,8}.plus(1).sum()                       [-- 44 again --]
[NO_PROMPT][== and now: one lane per peer ==]
{1,2,3,4,5,6,7,8}.plus(1).sum().explain()>>format     [-- branch, keyed by the two peers; each folds its own part --]
```

```mtron_pre cast=dist-data-local cols=110 rows=30 typing_delay=0.03
[HIDDEN]import(/m/math,math)
[HIDDEN]import(/m/math/cat,cat)
[NO_PROMPT][== the same two peers ==]
[MAXOUTPUT 2]spawn(<//localhost:1111/store>, <//0>); ws_client::[pattern=><ws://localhost:1111/mtron>]
[MAXOUTPUT 2]spawn(<//localhost:1112/store>, <//1>); ws_client::[pattern=><ws://localhost:1112/mtron>]
[NO_PROMPT][== each peer takes its own text, over the wire ==]
<ws://localhost:1111/store/a1> -> 'one two three four five'
<ws://localhost:1112/store/a2> -> 'six seven eight nine ten eleven'
[NO_PROMPT][== and one expression puts each lane where its data stands ==]
[/store/a1,/store/a2].branch(//0 => >>0.*_.-<'\s'>-.count(), //1 => >>1.*_-<'\s'>-.count()).sum()   [-- 11 --]
```

```mtron_pre cast=dist-keyed cols=110 rows=30 typing_delay=0.03
[HIDDEN]import(/m/math,math)
[HIDDEN]import(/m/math/cat,cat)
[NO_PROMPT][== the same two peers ==]
[MAXOUTPUT 2]spawn(<//localhost:1111/store>, <//0>); ws_client::[pattern=><ws://localhost:1111/mtron>]
[MAXOUTPUT 2]spawn(<//localhost:1112/store>, <//1>); ws_client::[pattern=><ws://localhost:1112/mtron>]
[NO_PROMPT][== a keyed fold: three keys, two peers ==]
{1,2,3,4,5,6}.group([mod(3)=>sum()])                  [-- each lane groups its part --]
[NO_PROMPT][== recombined POINTWISE: merge by key, then fold each key ==]
{1,2,3,4,5,6}.group([mod(3)=>sum()]).explain()>>format   [-- no single combiner op names this --]
```

```mtron_pre cast=dist-master cols=110 rows=30 typing_delay=0.03
[HIDDEN]import(/m/math,math)
[HIDDEN]import(/m/math/cat,cat)
[NO_PROMPT][== the same two peers ==]
[MAXOUTPUT 2]spawn(<//localhost:1111/store>, <//0>); ws_client::[pattern=><ws://localhost:1111/mtron>]
[MAXOUTPUT 2]spawn(<//localhost:1112/store>, <//1>); ws_client::[pattern=><ws://localhost:1112/mtron>]
[NO_PROMPT][== keep a slice home: the master folds too ==]
{1,2,3,4,5,6,7,8}.plus(1).sum?place=[local,peer]()    [-- 44, with one lane at distance zero --]
[NO_PROMPT][== and the home lane is keyed <~> ==]
{1,2,3,4,5,6,7,8}.plus(1).sum?place=[local,peer]().explain()>>format
```

```mtron_pre cast=dist-move cols=110 rows=30 typing_delay=0.03
[HIDDEN]import(/m/math,math)
[HIDDEN]import(/m/math/cat,cat)
[NO_PROMPT][== the same two peers ==]
[MAXOUTPUT 2]spawn(<//localhost:1111/store>, <//0>); ws_client::[pattern=><ws://localhost:1111/mtron>]
[MAXOUTPUT 2]spawn(<//localhost:1112/store>, <//1>); ws_client::[pattern=><ws://localhost:1112/mtron>]
[NO_PROMPT][== move the frame of reference, not the data ==]
{1,2,3}.plus(5).move(//0).plus(2).to(/store/moved).sum()   [-- 27 --]
[NO_PROMPT][== the write landed on the peer, not here ==]
*//0/store/moved                                       [-- 10: the tail ran where the frame went --]
```

```mtron_pre cast=dist-const cols=110 rows=26 typing_delay=0.03
[HIDDEN]import(/m/math,math)
[HIDDEN]import(/m/math/cat,cat)
[NO_PROMPT][== the same two peers ==]
[MAXOUTPUT 2]spawn(<//localhost:1111/store>, <//0>); ws_client::[pattern=><ws://localhost:1111/mtron>]
[MAXOUTPUT 2]spawn(<//localhost:1112/store>, <//1>); ws_client::[pattern=><ws://localhost:1112/mtron>]
[NO_PROMPT][== a constant rides along: plus(10) instead of plus(1) ==]
{1,2,3,4,5,6}.plus(10).sum()                           [-- 81 --]
[NO_PROMPT][== and the plan is the SAME plan — the constant needed no plumbing ==]
{1,2,3,4,5,6}.plus(10).sum().explain()>>format
```

```mtron_pre cast=dist-tree cols=110 rows=30 typing_delay=0.03 speed_rates="0.5,1,1.5,2" speed_default="1"
[HIDDEN]import(/m/math,math)
[HIDDEN]import(/m/math/cat,cat)
[NO_PROMPT][== the same two peers ==]
[MAXOUTPUT 2]spawn(<//localhost:1111/store>, <//0>); ws_client::[pattern=><ws://localhost:1111/mtron>]
[MAXOUTPUT 2]spawn(<//localhost:1112/store>, <//1>); ws_client::[pattern=><ws://localhost:1112/mtron>]
[NO_PROMPT][== the fold, planned as a tree: two levels, two children per node ==]
{1,2,3,4,5,6,7,8}.plus(1).sum?hop=2()                     [-- 44 --]
[NO_PROMPT][== every lane restates the form one height down, ?hop=1, so it re-plans where it lands ==]
{1,2,3,4,5,6,7,8}.plus(1).sum?hop=2().explain()>>format   [-- the budget rides each lane's first instruction --]
```

```mtron_pre cast=dist-run cols=110 rows=32 typing_delay=0.03 speed_rates="0.5,1,1.5,2" speed_default="1"
[HIDDEN]import(/m/math,math)
[HIDDEN]import(/m/math/cat,cat)
[NO_PROMPT][== the same two peers ==]
[MAXOUTPUT 2]spawn(<//localhost:1111/store>, <//0>); ws_client::[pattern=><ws://localhost:1111/mtron>]
[MAXOUTPUT 2]spawn(<//localhost:1112/store>, <//1>); ws_client::[pattern=><ws://localhost:1112/mtron>]
[NO_PROMPT][== a seed of one: the only multiplicity is the run's span, the unrolls 0 .. 10 ==]
start(0).{0,10}plus(1).sum()                              [-- 55 --]
[NO_PROMPT][== the plan factors the UNROLL INDEX, not the seed: one lane per place, a narrowed span ==]
start(0).{0,10}plus(1).sum().explain()>>format            [-- one lane per place, the unrolls' work split by sqrt --]
```
