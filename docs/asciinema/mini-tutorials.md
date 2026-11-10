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
