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

add("张掖", [
  {"id":"ZYE001","name":"张掖丹霞","location":{"longitude":100.069,"latitude":38.933,"address":"张掖市临泽县倪家营镇","city":"张掖"},"rating":4.7,"durationMin":240,"ticketPrice":75.0,"tags":["丹霞","彩色丘陵","世界遗产"]},
  {"id":"ZYE002","name":"大佛寺","location":{"longitude":100.460,"latitude":38.935,"address":"张掖市甘州区民主西街","city":"张掖"},"rating":4.3,"durationMin":90,"ticketPrice":41.0,"tags":["佛教","世界最大室内卧佛"]},
  {"id":"ZYE003","name":"马蹄寺","location":{"longitude":100.425,"latitude":38.515,"address":"张掖市肃南县马蹄藏族乡","city":"张掖"},"rating":4.4,"durationMin":180,"ticketPrice":74.0,"tags":["石窟","佛教","祁连山"]},
  {"id":"ZYE004","name":"平山湖大峡谷","location":{"longitude":100.530,"latitude":39.086,"address":"张掖市甘州区平山湖乡","city":"张掖"},"rating":4.4,"durationMin":240,"ticketPrice":100.0,"tags":["峡谷","丹霞","徒步"]},
  {"id":"ZYE005","name":"冰沟丹霞","location":{"longitude":100.069,"latitude":38.933,"address":"张掖市肃南县康乐镇","city":"张掖"},"rating":4.3,"durationMin":180,"ticketPrice":40.0,"tags":["丹霞","窗棂式","自然"]},
  {"id":"ZYE006","name":"山丹军马场","location":{"longitude":101.087,"latitude":38.239,"address":"张掖市山丹县大马营镇","city":"张掖"},"rating":4.3,"durationMin":480,"ticketPrice":65.0,"tags":["草原","军马","骑马"]},
  {"id":"ZYE007","name":"张掖国家湿地公园","location":{"longitude":100.460,"latitude":38.935,"address":"张掖市甘州区北环路","city":"张掖"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["湿地","自然","免费"]},
  {"id":"ZYE008","name":"木塔寺","location":{"longitude":100.460,"latitude":38.935,"address":"张掖市甘州区县府街","city":"张掖"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["古建筑","隋代","免费"]},
  {"id":"ZYE009","name":"张掖博物馆","location":{"longitude":100.460,"latitude":38.935,"address":"张掖市甘州区","city":"张掖"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"ZYE010","name":"七彩镇","location":{"longitude":100.069,"latitude":38.933,"address":"张掖市临泽县丹霞口","city":"张掖"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["特色小镇","免费"]},
])

add("天水", [
  {"id":"TSH001","name":"麦积山石窟","location":{"longitude":106.002,"latitude":34.380,"address":"天水市麦积区麦积山","city":"天水"},"rating":4.6,"durationMin":240,"ticketPrice":90.0,"tags":["世界遗产","石窟","北魏"]},
  {"id":"TSH002","name":"伏羲庙","location":{"longitude":105.725,"latitude":34.581,"address":"天水市秦州区伏羲路110号","city":"天水"},"rating":4.3,"durationMin":90,"ticketPrice":40.0,"tags":["历史文化","伏羲","明代"]},
  {"id":"TSH003","name":"仙人崖","location":{"longitude":106.002,"latitude":34.380,"address":"天水市麦积区麦积山","city":"天水"},"rating":4.3,"durationMin":150,"ticketPrice":40.0,"tags":["自然","佛教","石窟"]},
  {"id":"TSH004","name":"南郭寺","location":{"longitude":105.725,"latitude":34.581,"address":"天水市秦州区南郭路","city":"天水"},"rating":4.1,"durationMin":90,"ticketPrice":20.0,"tags":["佛教","杜甫","古刹"]},
  {"id":"TSH005","name":"玉泉观","location":{"longitude":105.725,"latitude":34.581,"address":"天水市秦州区玉泉路","city":"天水"},"rating":4.2,"durationMin":90,"ticketPrice":20.0,"tags":["道教","元代","古建筑"]},
  {"id":"TSH006","name":"天水博物馆","location":{"longitude":105.725,"latitude":34.581,"address":"天水市秦州区伏羲路","city":"天水"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"TSH007","name":"卦台山","location":{"longitude":105.725,"latitude":34.581,"address":"天水市麦积区渭南镇","city":"天水"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["伏羲","八卦","免费"]},
  {"id":"TSH008","name":"街亭古战场","location":{"longitude":105.725,"latitude":34.581,"address":"天水市秦安县陇城镇","city":"天水"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["三国","马谡","免费"]},
  {"id":"TSH009","name":"净土寺","location":{"longitude":106.002,"latitude":34.380,"address":"天水市麦积区麦积山","city":"天水"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["佛教","免费"]},
  {"id":"TSH010","name":"李广墓","location":{"longitude":105.725,"latitude":34.581,"address":"天水市秦州区石马坪","city":"天水"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["历史文化","西汉","免费"]},
])

add("宜宾", [
  {"id":"YBN001","name":"蜀南竹海","location":{"longitude":105.024,"latitude":28.464,"address":"宜宾市长宁县竹海镇","city":"宜宾"},"rating":4.5,"durationMin":480,"ticketPrice":100.0,"tags":["竹海","自然","卧虎藏龙"]},
  {"id":"YBN002","name":"兴文石海","location":{"longitude":105.129,"latitude":28.196,"address":"宜宾市兴文县石海镇","city":"宜宾"},"rating":4.4,"durationMin":360,"ticketPrice":140.0,"tags":["喀斯特","天坑","溶洞"]},
  {"id":"YBN003","name":"李庄古镇","location":{"longitude":104.837,"latitude":28.813,"address":"宜宾市翠屏区李庄镇","city":"宜宾"},"rating":4.3,"durationMin":180,"ticketPrice":0.0,"tags":["古镇","抗战文化","同济大学旧址","免费"]},
  {"id":"YBN004","name":"五粮液酒厂","location":{"longitude":104.631,"latitude":28.775,"address":"宜宾市翠屏区岷江西路150号","city":"宜宾"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["酒文化","五粮液","免费"]},
  {"id":"YBN005","name":"翠屏山公园","location":{"longitude":104.631,"latitude":28.775,"address":"宜宾市翠屏区翠屏路","city":"宜宾"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["公园","哪吒","免费"]},
  {"id":"YBN006","name":"真武山古建筑群","location":{"longitude":104.631,"latitude":28.775,"address":"宜宾市翠屏区真武山","city":"宜宾"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["道教","古建筑","免费"]},
  {"id":"YBN007","name":"夕佳山古民居","location":{"longitude":105.024,"latitude":28.464,"address":"宜宾市江安县夕佳山镇","city":"宜宾"},"rating":4.2,"durationMin":90,"ticketPrice":30.0,"tags":["古建筑","明代"]},
  {"id":"YBN008","name":"大观楼","location":{"longitude":104.631,"latitude":28.775,"address":"宜宾市翠屏区西街口","city":"宜宾"},"rating":4.1,"durationMin":30,"ticketPrice":0.0,"tags":["古建筑","地标","免费"]},
  {"id":"YBN009","name":"流杯池公园","location":{"longitude":104.631,"latitude":28.775,"address":"宜宾市翠屏区岷江北路","city":"宜宾"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["公园","黄庭坚","免费"]},
  {"id":"YBN010","name":"僰人悬棺","location":{"longitude":104.770,"latitude":28.061,"address":"宜宾市珙县洛表镇","city":"宜宾"},"rating":4.4,"durationMin":120,"ticketPrice":30.0,"tags":["悬棺","僰人","千古之谜"]},
])

add("曲靖", [
  {"id":"QJN001","name":"罗平油菜花海","location":{"longitude":104.327,"latitude":24.885,"address":"曲靖市罗平县","city":"曲靖"},"rating":4.7,"durationMin":240,"ticketPrice":0.0,"tags":["油菜花","春季","摄影","免费"]},
  {"id":"QJN002","name":"九龙瀑布群","location":{"longitude":104.327,"latitude":24.885,"address":"曲靖市罗平县长底乡","city":"曲靖"},"rating":4.4,"durationMin":240,"ticketPrice":95.0,"tags":["瀑布","自然","中国最美"]},
  {"id":"QJN003","name":"陆良彩色沙林","location":{"longitude":103.667,"latitude":25.033,"address":"曲靖市陆良县彩色沙林","city":"曲靖"},"rating":4.2,"durationMin":180,"ticketPrice":70.0,"tags":["沙林","彩色","自然"]},
  {"id":"QJN004","name":"珠江源风景区","location":{"longitude":103.892,"latitude":25.883,"address":"曲靖市沾益区马雄山","city":"曲靖"},"rating":4.2,"durationMin":180,"ticketPrice":40.0,"tags":["珠江源头","自然","登山"]},
  {"id":"QJN005","name":"多依河","location":{"longitude":104.327,"latitude":24.885,"address":"曲靖市罗平县鲁布革乡","city":"曲靖"},"rating":4.2,"durationMin":240,"ticketPrice":70.0,"tags":["河流","瀑布","自然"]},
  {"id":"QJN006","name":"大海草山","location":{"longitude":103.294,"latitude":26.404,"address":"曲靖市会泽县大海乡","city":"曲靖"},"rating":4.3,"durationMin":300,"ticketPrice":50.0,"tags":["高山草甸","自然","摄影"]},
  {"id":"QJN007","name":"会泽古城","location":{"longitude":103.294,"latitude":26.404,"address":"曲靖市会泽县古城","city":"曲靖"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["古城","钱王之乡","免费"]},
  {"id":"QJN008","name":"曲靖博物馆","location":{"longitude":103.800,"latitude":25.500,"address":"曲靖市麒麟区","city":"曲靖"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"QJN009","name":"师宗凤凰谷","location":{"longitude":104.036,"latitude":24.723,"address":"曲靖市师宗县凤凰谷","city":"曲靖"},"rating":4.2,"durationMin":240,"ticketPrice":80.0,"tags":["峡谷","溶洞","自然"]},
  {"id":"QJN010","name":"麒麟水乡","location":{"longitude":103.800,"latitude":25.500,"address":"曲靖市麒麟区","city":"曲靖"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["湖泊","湿地","免费"]},
])

add("咸宁", [
  {"id":"XNN001","name":"三国赤壁古战场","location":{"longitude":113.900,"latitude":29.719,"address":"咸宁市赤壁市赤壁镇","city":"咸宁"},"rating":4.3,"durationMin":240,"ticketPrice":150.0,"tags":["三国","赤壁之战","历史文化"]},
  {"id":"XNN002","name":"九宫山","location":{"longitude":114.400,"latitude":29.472,"address":"咸宁市通山县九宫山镇","city":"咸宁"},"rating":4.4,"durationMin":360,"ticketPrice":70.0,"tags":["避暑","滑雪","道教"]},
  {"id":"XNN003","name":"隐水洞","location":{"longitude":114.400,"latitude":29.472,"address":"咸宁市通山县大畈镇","city":"咸宁"},"rating":4.3,"durationMin":120,"ticketPrice":125.0,"tags":["溶洞","地下河","自然"]},
  {"id":"XNN004","name":"咸宁温泉","location":{"longitude":114.327,"latitude":29.843,"address":"咸宁市咸安区温泉镇","city":"咸宁"},"rating":4.3,"durationMin":240,"ticketPrice":168.0,"tags":["温泉","养生","温泉之乡"]},
  {"id":"XNN005","name":"潜山国家森林公园","location":{"longitude":114.327,"latitude":29.843,"address":"咸宁市咸安区潜山路","city":"咸宁"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["森林","免费"]},
  {"id":"XNN006","name":"陆水湖","location":{"longitude":113.900,"latitude":29.719,"address":"咸宁市赤壁市陆水湖","city":"咸宁"},"rating":4.2,"durationMin":240,"ticketPrice":100.0,"tags":["湖泊","水库","自然"]},
  {"id":"XNN007","name":"太乙洞","location":{"longitude":114.327,"latitude":29.843,"address":"咸宁市咸安区","city":"咸宁"},"rating":4.1,"durationMin":90,"ticketPrice":60.0,"tags":["溶洞","道教"]},
  {"id":"XNN008","name":"刘家桥古村","location":{"longitude":114.327,"latitude":29.843,"address":"咸宁市咸安区桂花镇","city":"咸宁"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["古村落","免费"]},
  {"id":"XNN009","name":"羊楼洞古镇","location":{"longitude":113.741,"latitude":29.529,"address":"咸宁市赤壁市羊楼洞","city":"咸宁"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["古镇","茶马古道","明清","免费"]},
  {"id":"XNN010","name":"咸宁市博物馆","location":{"longitude":114.327,"latitude":29.843,"address":"咸宁市咸安区金桂路","city":"咸宁"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
])

total = sum(len(c['attractions']) for c in data['cities'])
with open('huazai-trip-skills/src/main/resources/attractions/knowledge-base.json','w',encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print(f"\nTotal: {len(data['cities'])} cities, {total} attractions")
print("Done b21")
