import json
from collections import Counter

src_path = 'huazai-trip-skills/src/main/resources/attractions/knowledge-base.json'
tgt_path = 'huazai-trip-skills/target/classes/attractions/knowledge-base.json'

# Read source
with open(src_path, 'r', encoding='utf-8') as f:
    source = json.load(f)

# Read target (for 庐山 data)
with open(tgt_path, 'r', encoding='utf-8') as f:
    target = json.load(f)

# ---- Fix 1: YC conflict - 银川(YinChuan) -> YCH, 宜昌(YiChang) keep YC ----
YINCHUAN_HEX = 'e993b6e5b79d'  # 银川
for c in source['cities']:
    if c['city'].encode('utf-8').hex() == YINCHUAN_HEX:
        for a in c['attractions']:
            if a['id'].startswith('YC'):
                a['id'] = 'YCH' + a['id'][2:]
        print(f'Fixed 银川: YC -> YCH')
        break

# ---- Fix 2: LS conflict - 拉萨(Lhasa) keep LS, 庐山(LuShan) -> LUS ----
# Use hex comparison to avoid terminal encoding issues
LUSHAN_HEX = 'e4b990e5b1b1'  # 庐山
lushan = None
for c in target['cities']:
    if c['city'].encode('utf-8').hex() == LUSHAN_HEX:
        lushan = c
        break

if lushan is None:
    print('ERROR: 庐山 not found in target!')
else:
    # Fix LS -> LUS prefix
    for a in lushan['attractions']:
        if a['id'].startswith('LS'):
            a['id'] = 'LUS' + a['id'][2:]
    source['cities'].append(lushan)
    print(f'Added 庐山 with {len(lushan["attractions"])} attractions, LUS prefix')

# Also check for other cross-city LS conflicts in source
# 乐山(LeShan) e4b990e5b1b1 vs 拉萨(Lhasa) e68b89e890a8
print('\nChecking LS prefix in source after fixes:')
for c in source['cities']:
    ids = [a['id'] for a in c['attractions']]
    ls_ids = [i for i in ids if i == 'LS' or i[:3] in ('LS0','LS1','LS2','LS3','LS4','LS5','LS6','LS7','LS8','LS9')]
    ls_exact = [i for i in ids if i.startswith('LS') and not i.startswith('LSH')]
    if ls_exact:
        print(f'  {c["city"]}: {len(ls_exact)} LS-prefixed IDs')

# ---- Verify no global duplicates ----
all_ids = []
id_to_city = {}
for c in source['cities']:
    for a in c['attractions']:
        all_ids.append(a['id'])
        id_to_city.setdefault(a['id'], []).append(c['city'])

counter = Counter(all_ids)
dups = {k: v for k, v in counter.items() if v > 1}
if dups:
    print(f'\nERROR: {len(dups)} global duplicate IDs REMAINING:')
    for id, count in sorted(dups.items())[:20]:
        print(f'  {id}: {count}x in {id_to_city[id]}')
else:
    print(f'\nNO global duplicate IDs! ({len(set(all_ids))} unique, {len(all_ids)} total attractions)')

# ---- Save ----
with open(src_path, 'w', encoding='utf-8') as f:
    json.dump(source, f, ensure_ascii=False, indent=2)

total = sum(len(c['attractions']) for c in source['cities'])
print(f'\nSaved: {len(source["cities"])} cities, {total} attractions')
