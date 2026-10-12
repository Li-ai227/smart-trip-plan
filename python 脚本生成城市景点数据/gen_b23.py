import json
with open('huazai-trip-skills/src/main/resources/attractions/knowledge-base.json','r',encoding='utf-8') as f:
    data = json.load(f)
ids = set()
for c in data['cities']:
    for a in c['attractions']: ids.add(a['id'])

def add(city, attrs):
    added = skipped = 0; found = False
    for c in data['cities']:
        if c['city'] == city:
            found = True
            for a in attrs:
                if a['id'] in ids: skipped += 1
                else: c['attractions'].append(a); ids.add(a['id']); added += 1
            break
    if not found:
        valid = [a for a in attrs if a['id'] not in ids]
        for a in valid: ids.add(a['id'])
        data['cities'].append({"city": city, "attractions": valid})
        added = len(valid)
    print(f"  {city}: +{added}" + (f" skip{skipped}" if skipped else ""))

add("丽江", [
  {"id":"LJ051","name":"观音峡","location":{"longitude":100.200,"latitude":26.800,"address":"丽江市古城区七河镇","city":"丽江"},"rating":4.2,"durationMin":120,"ticketPrice":80.0,"tags":["峡谷","自然","瀑布"]},
  {"id":"LJ052","name":"文峰寺","location":{"longitude":100.210,"latitude":26.870,"address":"丽江市古城区文峰寺","city":"丽江"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["佛教","免费"]},
  {"id":"LJ053","name":"万古楼","location":{"longitude":100.234,"latitude":26.876,"address":"丽江市古城区狮子山","city":"丽江"},"rating":4.2,"durationMin":60,"ticketPrice":50.0,"tags":["古建筑","古城全景","观景"]},
  {"id":"LJ054","name":"永胜他留山","location":{"longitude":100.710,"latitude":26.700,"address":"丽江市永胜县六德乡","city":"丽江"},"rating":4.1,"durationMin":180,"ticketPrice":0.0,"tags":["他留文化","古墓群","免费"]},
  {"id":"LJ055","name":"东巴万神园","location":{"longitude":100.208,"latitude":27.032,"address":"丽江市玉龙县白沙镇","city":"丽江"},"rating":4.1,"durationMin":90,"ticketPrice":30.0,"tags":["东巴文化","纳西族"]},
])

add("昆明", [
  {"id":"KM051","name":"东川红土地摄影基地","location":{"longitude":103.082,"latitude":25.908,"address":"昆明市东川区红土地镇","city":"昆明"},"rating":4.5,"durationMin":240,"ticketPrice":0.0,"tags":["摄影","丹霞","免费"]},
  {"id":"KM052","name":"寻甸凤龙湾风景区","location":{"longitude":103.200,"latitude":25.550,"address":"昆明市寻甸县","city":"昆明"},"rating":4.2,"durationMin":240,"ticketPrice":60.0,"tags":["湖泊","度假","自然"]},
  {"id":"KM053","name":"禄劝轿子仙宫","location":{"longitude":102.827,"latitude":25.944,"address":"昆明市禄劝县","city":"昆明"},"rating":4.1,"durationMin":90,"ticketPrice":50.0,"tags":["溶洞","自然"]},
  {"id":"KM054","name":"北大营万亩草场","location":{"longitude":103.120,"latitude":25.540,"address":"昆明市寻甸县河口镇","city":"昆明"},"rating":4.2,"durationMin":180,"ticketPrice":0.0,"tags":["草原","露营","免费"]},
  {"id":"KM055","name":"宜良九乡张口洞","location":{"longitude":103.360,"latitude":24.950,"address":"昆明市宜良县九乡","city":"昆明"},"rating":4.1,"durationMin":90,"ticketPrice":90.0,"tags":["溶洞","自然"]},
])

add("保定", [
  {"id":"BDG001","name":"白洋淀","location":{"longitude":116.010,"latitude":38.918,"address":"雄安新区（保定市安新县）","city":"保定"},"rating":4.4,"durationMin":360,"ticketPrice":40.0,"tags":["湖泊","湿地","荷花"]},
  {"id":"BDG002","name":"清西陵","location":{"longitude":115.350,"latitude":39.370,"address":"保定市易县梁各庄镇","city":"保定"},"rating":4.4,"durationMin":240,"ticketPrice":120.0,"tags":["世界遗产","清代皇陵"]},
  {"id":"BDG003","name":"直隶总督署","location":{"longitude":115.487,"latitude":38.874,"address":"保定市莲池区裕华西路","city":"保定"},"rating":4.2,"durationMin":90,"ticketPrice":30.0,"tags":["清代","总督","历史文化"]},
  {"id":"BDG004","name":"古莲花池","location":{"longitude":115.487,"latitude":38.874,"address":"保定市莲池区裕华西路","city":"保定"},"rating":4.2,"durationMin":90,"ticketPrice":30.0,"tags":["园林","元代","荷花"]},
  {"id":"BDG005","name":"野三坡百里峡","location":{"longitude":115.383,"latitude":39.671,"address":"保定市涞水县野三坡镇","city":"保定"},"rating":4.6,"durationMin":360,"ticketPrice":115.0,"tags":["峡谷","自然","世界地质公园"]},
  {"id":"BDG006","name":"白石山","location":{"longitude":114.700,"latitude":39.230,"address":"保定市涞源县白石山镇","city":"保定"},"rating":4.5,"durationMin":360,"ticketPrice":150.0,"tags":["自然","玻璃栈道","登山"]},
  {"id":"BDG007","name":"保定博物馆","location":{"longitude":115.487,"latitude":38.874,"address":"保定市竞秀区","city":"保定"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"BDG008","name":"冉庄地道战遗址","location":{"longitude":115.620,"latitude":38.790,"address":"保定市清苑区冉庄镇","city":"保定"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["红色旅游","地道战","免费"]},
  {"id":"BDG009","name":"狼牙山","location":{"longitude":115.423,"latitude":39.134,"address":"保定市易县狼牙山镇","city":"保定"},"rating":4.3,"durationMin":300,"ticketPrice":80.0,"tags":["红色旅游","登山","自然"]},
  {"id":"BDG010","name":"满城汉墓","location":{"longitude":115.314,"latitude":38.948,"address":"保定市满城区中山西路","city":"保定"},"rating":4.2,"durationMin":150,"ticketPrice":75.0,"tags":["汉墓","金缕玉衣","西汉"]},
])

total = sum(len(c['attractions']) for c in data['cities'])
with open('huazai-trip-skills/src/main/resources/attractions/knowledge-base.json','w',encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print(f"\nTotal: {len(data['cities'])} cities, {total} attractions")
print("Done b23 - Reached 100!")
