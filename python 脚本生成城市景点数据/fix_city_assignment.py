"""
Fix cross-city attractions in knowledge-base.json.

Problem: Many attractions in the "昆明" (Kunming) city grouping are actually
located in other Yunnan prefectures (文山, 玉溪, 红河, 迪庆, 大理, etc.).
Their location.city is incorrectly set to "昆明".

This script:
1. Detects cross-city attractions by comparing address with grouping city
2. Extracts the correct prefecture-level city from the address
3. Moves attractions to proper city groupings (creates new ones if needed)
4. Fixes location.city to match actual city
"""
import json
import re
import os

SRC_PATH = 'huazai-trip-skills/src/main/resources/attractions/knowledge-base.json'

# Chinese prefecture-level city name patterns
# Address format: 云南省XX市XX区/县 or 云南省XX自治州XX市/县
PREFECTURE_RE = re.compile(
    r'云南省(.+?)(?:自治州|市)(.+?)?(?:市|区|县)'
)

# Map address prefecture names to knowledge base city keys
PREFECTURE_TO_CITY_KEY = {
    '文山壮族苗族': '文山',
    '红河哈尼族彝族': '红河',
    '迪庆藏族': '迪庆',
    '大理白族': '大理',
    '西双版纳傣族': '西双版纳',
    '保山': '保山',
    '临沧': '临沧',
    '曲靖': '曲靖',
    '玉溪': '玉溪',
    '楚雄彝族': '楚雄',
    '德宏傣族景颇族': '德宏',
    '怒江傈僳族': '怒江',
    '普洱': '普洱',
    '丽江': '丽江',
    '昭通': '昭通',
}

def extract_prefecture_city(address: str) -> str | None:
    """Extract prefecture-level city name from address."""
    if not address.startswith('云南省'):
        return None
    match = PREFECTURE_RE.match(address)
    if not match:
        return None
    prefecture = match.group(1)
    # Try exact match first
    for key, city in PREFECTURE_TO_CITY_KEY.items():
        if key in prefecture:
            return city
    # Fallback: use the prefecture name directly
    # Strip 自治州 suffix
    simple = prefecture.replace('自治州', '').replace('市', '')
    return simple


def main():
    with open(SRC_PATH, 'r', encoding='utf-8') as f:
        data = json.load(f)

    city_index = {c['city']: i for i, c in enumerate(data['cities'])}
    fixed_count = 0
    new_cities = {}  # city_name -> [attractions]

    for city_entry in data['cities']:
        grouping_city = city_entry['city']
        keep = []
        for attr in city_entry['attractions']:
            address = attr.get('location', {}).get('address', '')
            actual_city = extract_prefecture_city(address)

            if actual_city is None:
                # Not a Yunnan address or already correct -> keep
                keep.append(attr)
                continue

            if actual_city == grouping_city:
                keep.append(attr)
                continue

            # Cross-city! Fix and move
            attr['location']['city'] = actual_city
            new_cities.setdefault(actual_city, []).append(attr)
            fixed_count += 1
            print(f'  MOVE: {attr["name"]} ({attr["id"]}) {grouping_city} -> {actual_city}')

        city_entry['attractions'] = keep

    # Add new city groupings
    for city_name, attractions in new_cities.items():
        if city_name in city_index:
            # Merge into existing city
            data['cities'][city_index[city_name]]['attractions'].extend(attractions)
            print(f'  MERGE: {len(attractions)} attractions -> existing "{city_name}"')
        else:
            # Create new city grouping
            data['cities'].append({
                'city': city_name,
                'attractions': attractions
            })
            print(f'  NEW: city "{city_name}" with {len(attractions)} attractions')

    # Remove empty city groupings
    data['cities'] = [c for c in data['cities'] if c['attractions']]

    total = sum(len(c['attractions']) for c in data['cities'])
    print(f'\nFixed {fixed_count} cross-city attractions')
    print(f'Result: {len(data["cities"])} cities, {total} attractions')

    # Save
    with open(SRC_PATH, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print('Saved.')

    # Also copy to target/classes if it exists
    tgt_path = 'huazai-trip-skills/target/classes/attractions/knowledge-base.json'
    tgt_dir = os.path.dirname(tgt_path)
    if os.path.exists(tgt_dir):
        with open(tgt_path, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print(f'Copied to {tgt_path}')


if __name__ == '__main__':
    main()
