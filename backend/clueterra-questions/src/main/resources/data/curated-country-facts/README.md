# Curated Country Facts

This directory contains short country-to-fact mappings used by the
`CURATED_COUNTRY_FACTS` Questions source. It is intentionally separate from the
runtime Wikipedia client: the application never scrapes Wikipedia pages to
produce these values.

The collection was originally assembled from English Wikipedia country lists
and has been modified by Clueterra by normalizing country keys to ISO 3166-1
alpha-2 codes, shortening values, combining synonymous entries, and assigning
more cautious gameplay semantics. In particular, a value described by a source
as a "national" dish, drink, dance, instrument, or sport is presented by
Clueterra as a signature, traditional, or culturally prominent association
unless official status is explicit in the stored value.

Gameplay output applies an additional presentation layer without changing the
source records:

- country names, demonyms, and country-derived list items are omitted so a clue
  cannot reveal its own answer;
- related facts are combined into a short culture trail, a symbol trail, or a
  statehood date and motto clue;
- a culture trail uses at most three kinds of fact and one example of each;
  remaining source rows are not emitted again as weaker standalone clues;
- repeated or near-identical symbols are collapsed, long source lists are
  shortened, and comma-separated values are rendered as natural English lists.

Attribution and review snapshots (accessed 2026-09-20):

- dishes: https://en.wikipedia.org/w/index.php?title=National_dish&oldid=1373550208
- animals: https://en.wikipedia.org/w/index.php?title=List_of_national_animals&oldid=1374560351
- birds: https://en.wikipedia.org/w/index.php?title=List_of_national_birds&oldid=1358085371
- trees: https://en.wikipedia.org/w/index.php?title=List_of_national_trees&oldid=1370464873
- flowers: https://en.wikipedia.org/w/index.php?title=List_of_national_flowers&oldid=1375620772
- dances: https://en.wikipedia.org/w/index.php?title=List_of_national_dances&oldid=1355079915
- drinks: https://en.wikipedia.org/w/index.php?title=List_of_national_drinks&oldid=1375825267
- sports: https://en.wikipedia.org/w/index.php?title=National_sport&oldid=1368449071
- mottos: https://en.wikipedia.org/w/index.php?title=List_of_national_mottos&oldid=1371937431
- instruments: https://en.wikipedia.org/w/index.php?title=List_of_national_instruments_(music)&oldid=1368981869
- statehood dates: https://en.wikipedia.org/w/index.php?title=List_of_national_independence_days&oldid=1375294840

Wikipedia contributors make article text available under CC BY-SA 4.0. This
adapted collection is distributed under the same license. See `LICENSE.md`.
