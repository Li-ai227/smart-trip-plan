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

# b20: 嘉峪关, 南通, 连云港, 大同, 宝鸡, 恩施, 呼伦贝尔

add("嘉峪关", [
  {"id":"JYG001","name":"嘉峪关长城","location":{"longitude":98.216,"latitude":39.800,"address":"嘉峪关市峪泉镇","city":"嘉峪关"},"rating":4.6,"durationMin":240,"ticketPrice":120.0,"tags":["长城","世界遗产","明代西端"]},
  {"id":"JYG002","name":"悬壁长城","location":{"longitude":98.203,"latitude":39.868,"address":"嘉峪关市峪泉镇黄草营村","city":"嘉峪关"},"rating":4.3,"durationMin":150,"ticketPrice":31.0,"tags":["长城","自然","登山"]},
  {"id":"JYG003","name":"长城第一墩","location":{"longitude":98.203,"latitude":39.752,"address":"嘉峪关市讨赖河岸","city":"嘉峪关"},"rating":4.2,"durationMin":90,"ticketPrice":22.0,"tags":["长城","讨赖河","明代"]},
  {"id":"JYG004","name":"嘉峪关城市博物馆","location":{"longitude":98.288,"latitude":39.772,"address":"嘉峪关市新华南路","city":"嘉峪关"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"JYG005","name":"东湖生态旅游景区","location":{"longitude":98.288,"latitude":39.772,"address":"嘉峪关市体育大道","city":"嘉峪关"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["湖泊","公园","免费"]},
  {"id":"JYG006","name":"魏晋墓壁画","location":{"longitude":98.243,"latitude":39.803,"address":"嘉峪关市新城镇","city":"嘉峪关"},"rating":4.2,"durationMin":90,"ticketPrice":31.0,"tags":["历史文化","魏晋","砖壁画"]},
  {"id":"JYG007","name":"嘉峪关方特欢乐世界","location":{"longitude":98.288,"latitude":39.772,"address":"嘉峪关市南市区","city":"嘉峪关"},"rating":4.2,"durationMin":360,"ticketPrice":280.0,"tags":["主题乐园","亲子"]},
  {"id":"JYG008","name":"紫轩葡萄酒庄园","location":{"longitude":98.288,"latitude":39.772,"address":"嘉峪关市嘉东工业园区","city":"嘉峪关"},"rating":4.1,"durationMin":90,"ticketPrice":50.0,"tags":["葡萄酒","庄园","品酒"]},
  {"id":"JYG009","name":"讨赖河生态景区","location":{"longitude":98.288,"latitude":39.772,"address":"嘉峪关市南市区","city":"嘉峪关"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["河流","生态","免费"]},
  {"id":"JYG010","name":"嘉峪关夜市（大唐美食街）","location":{"longitude":98.288,"latitude":39.772,"address":"嘉峪关市大唐路","city":"嘉峪关"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["夜市","美食","烤肉","免费"]},
])

add("南通", [
  {"id":"NTG001","name":"狼山风景区","location":{"longitude":120.887,"latitude":31.956,"address":"南通市崇川区狼山镇","city":"南通"},"rating":4.4,"durationMin":180,"ticketPrice":70.0,"tags":["佛教","登山","长江"]},
  {"id":"NTG002","name":"濠河风景区","location":{"longitude":120.864,"latitude":32.018,"address":"南通市崇川区濠河","city":"南通"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["千年古护城河","夜景","免费"]},
  {"id":"NTG003","name":"南通博物苑","location":{"longitude":120.865,"latitude":32.015,"address":"南通市崇川区濠南路19号","city":"南通"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["博物馆","中国最早","免费"]},
  {"id":"NTG004","name":"启东黄金海滩","location":{"longitude":121.942,"latitude":31.818,"address":"南通市启东市寅阳镇","city":"南通"},"rating":4.2,"durationMin":240,"ticketPrice":40.0,"tags":["海滩","赶海","亲子"]},
  {"id":"NTG005","name":"唐闸古镇","location":{"longitude":120.838,"latitude":32.043,"address":"南通市崇川区唐闸镇","city":"南通"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["古镇","近代工业","免费"]},
  {"id":"NTG006","name":"啬园","location":{"longitude":120.887,"latitude":31.956,"address":"南通市崇川区狼山镇","city":"南通"},"rating":4.1,"durationMin":90,"ticketPrice":20.0,"tags":["园林","张謇"]},
  {"id":"NTG007","name":"南通探险王国","location":{"longitude":120.909,"latitude":32.037,"address":"南通市港闸区北大街","city":"南通"},"rating":4.1,"durationMin":240,"ticketPrice":120.0,"tags":["主题乐园","亲子"]},
  {"id":"NTG008","name":"园博园","location":{"longitude":120.887,"latitude":31.956,"address":"南通市崇川区狼山北麓","city":"南通"},"rating":4.1,"durationMin":180,"ticketPrice":30.0,"tags":["园林","花卉"]},
  {"id":"NTG009","name":"南通市图书馆","location":{"longitude":120.864,"latitude":32.018,"address":"南通市崇川区","city":"南通"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["图书馆","免费"]},
  {"id":"NTG010","name":"如皋水绘园","location":{"longitude":120.575,"latitude":32.373,"address":"南通市如皋市如城碧霞路","city":"南通"},"rating":4.3,"durationMin":120,"ticketPrice":50.0,"tags":["园林","明代","冒辟疆"]},
])

add("连云港", [
  {"id":"LYG001","name":"花果山风景区","location":{"longitude":119.240,"latitude":34.618,"address":"连云港市海州区花果山","city":"连云港"},"rating":4.4,"durationMin":360,"ticketPrice":90.0,"tags":["西游记","孙悟空","自然"]},
  {"id":"LYG002","name":"连岛海滨浴场","location":{"longitude":119.463,"latitude":34.761,"address":"连云港市连云区连岛","city":"连云港"},"rating":4.3,"durationMin":240,"ticketPrice":50.0,"tags":["海滩","海岛","夏季"]},
  {"id":"LYG003","name":"渔湾风景区","location":{"longitude":119.221,"latitude":34.596,"address":"连云港市海州区渔湾","city":"连云港"},"rating":4.2,"durationMin":150,"ticketPrice":40.0,"tags":["瀑布","自然","夏季"]},
  {"id":"LYG004","name":"孔望山","location":{"longitude":119.180,"latitude":34.600,"address":"连云港市海州区孔望山","city":"连云港"},"rating":4.2,"durationMin":120,"ticketPrice":40.0,"tags":["历史文化","佛教","汉代石刻"]},
  {"id":"LYG005","name":"海上云台山","location":{"longitude":119.463,"latitude":34.761,"address":"连云港市连云区宿城","city":"连云港"},"rating":4.2,"durationMin":240,"ticketPrice":80.0,"tags":["登山","海景","自然"]},
  {"id":"LYG006","name":"连云港博物馆","location":{"longitude":119.221,"latitude":34.596,"address":"连云港市海州区朝阳东路","city":"连云港"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"LYG007","name":"云龙涧","location":{"longitude":119.221,"latitude":34.596,"address":"连云港市海州区","city":"连云港"},"rating":4.1,"durationMin":120,"ticketPrice":30.0,"tags":["峡谷","自然"]},
  {"id":"LYG008","name":"东海国际水晶城","location":{"longitude":118.753,"latitude":34.545,"address":"连云港市东海县水晶城","city":"连云港"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["水晶","购物","免费"]},
  {"id":"LYG009","name":"大伊山","location":{"longitude":119.240,"latitude":34.618,"address":"连云港市灌云县大伊山","city":"连云港"},"rating":4.1,"durationMin":150,"ticketPrice":40.0,"tags":["登山","自然","佛教"]},
  {"id":"LYG010","name":"海州古城","location":{"longitude":119.132,"latitude":34.573,"address":"连云港市海州区海州古城","city":"连云港"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["古城","历史文化","免费"]},
])

add("大同", [
  {"id":"DTG001","name":"云冈石窟","location":{"longitude":113.133,"latitude":40.110,"address":"大同市云冈区云冈镇","city":"大同"},"rating":4.8,"durationMin":240,"ticketPrice":120.0,"tags":["世界遗产","石窟","北魏"]},
  {"id":"DTG002","name":"悬空寺","location":{"longitude":113.710,"latitude":39.659,"address":"大同市浑源县恒山","city":"大同"},"rating":4.7,"durationMin":120,"ticketPrice":130.0,"tags":["古建筑","佛教","悬崖"]},
  {"id":"DTG003","name":"恒山","location":{"longitude":113.710,"latitude":39.659,"address":"大同市浑源县恒山","city":"大同"},"rating":4.4,"durationMin":360,"ticketPrice":55.0,"tags":["五岳","道教","登山"]},
  {"id":"DTG004","name":"华严寺","location":{"longitude":113.296,"latitude":40.091,"address":"大同市平城区下寺坡街","city":"大同"},"rating":4.5,"durationMin":120,"ticketPrice":65.0,"tags":["佛教","辽代","古建筑"]},
  {"id":"DTG005","name":"大同古城墙","location":{"longitude":113.296,"latitude":40.091,"address":"大同市平城区古城","city":"大同"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["古城墙","明代","免费"]},
  {"id":"DTG006","name":"九龙壁","location":{"longitude":113.296,"latitude":40.091,"address":"大同市平城区和阳街","city":"大同"},"rating":4.2,"durationMin":30,"ticketPrice":0.0,"tags":["明代","琉璃","地标","免费"]},
  {"id":"DTG007","name":"善化寺","location":{"longitude":113.296,"latitude":40.091,"address":"大同市平城区南寺街","city":"大同"},"rating":4.3,"durationMin":90,"ticketPrice":0.0,"tags":["佛教","辽金","免费"]},
  {"id":"DTG008","name":"大同博物馆","location":{"longitude":113.369,"latitude":40.098,"address":"大同市平城区太和路","city":"大同"},"rating":4.4,"durationMin":150,"ticketPrice":0.0,"tags":["博物馆","北魏","免费"]},
  {"id":"DTG009","name":"应县木塔","location":{"longitude":113.191,"latitude":39.562,"address":"大同市应县（朔州）","city":"大同"},"rating":4.7,"durationMin":90,"ticketPrice":60.0,"tags":["世界最高木塔","辽代","古建筑"]},
  {"id":"DTG010","name":"土林","location":{"longitude":113.251,"latitude":40.045,"address":"大同市云州区杜庄乡","city":"大同"},"rating":4.2,"durationMin":90,"ticketPrice":60.0,"tags":["自然","土林","摄影"]},
])

add("宝鸡", [
  {"id":"BJ001","name":"法门寺","location":{"longitude":107.903,"latitude":34.427,"address":"宝鸡市扶风县法门镇","city":"宝鸡"},"rating":4.6,"durationMin":240,"ticketPrice":120.0,"tags":["佛教","佛指舍利","唐代"]},
  {"id":"BJI002","name":"太白山国家森林公园","location":{"longitude":107.790,"latitude":34.002,"address":"宝鸡市眉县汤峪镇","city":"宝鸡"},"rating":4.5,"durationMin":480,"ticketPrice":90.0,"tags":["登山","秦岭","自然"]},
  {"id":"BJI003","name":"中华石鼓园","location":{"longitude":107.237,"latitude":34.362,"address":"宝鸡市渭滨区滨河大道","city":"宝鸡"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["历史文化","石鼓","免费"]},
  {"id":"BJI004","name":"宝鸡青铜器博物院","location":{"longitude":107.237,"latitude":34.362,"address":"宝鸡市渭滨区中华石鼓园","city":"宝鸡"},"rating":4.5,"durationMin":150,"ticketPrice":0.0,"tags":["博物馆","青铜器","周秦","免费"]},
  {"id":"BJI005","name":"炎帝陵","location":{"longitude":107.134,"latitude":34.336,"address":"宝鸡市渭滨区神农镇","city":"宝鸡"},"rating":4.2,"durationMin":120,"ticketPrice":30.0,"tags":["历史文化","炎帝","中华始祖"]},
  {"id":"BJI006","name":"关山草原","location":{"longitude":106.582,"latitude":34.728,"address":"宝鸡市陇县关山乡","city":"宝鸡"},"rating":4.4,"durationMin":360,"ticketPrice":65.0,"tags":["草原","骑马","自然"]},
  {"id":"BJI007","name":"红河谷","location":{"longitude":107.790,"latitude":34.002,"address":"宝鸡市眉县营头镇","city":"宝鸡"},"rating":4.2,"durationMin":240,"ticketPrice":60.0,"tags":["峡谷","瀑布","自然"]},
  {"id":"BJI008","name":"金台观","location":{"longitude":107.147,"latitude":34.376,"address":"宝鸡市金台区北坡","city":"宝鸡"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["道教","张三丰","免费"]},
  {"id":"BJI009","name":"大散关","location":{"longitude":107.089,"latitude":34.315,"address":"宝鸡市渭滨区川陕路","city":"宝鸡"},"rating":4.1,"durationMin":90,"ticketPrice":40.0,"tags":["历史文化","古代关隘","陆游"]},
  {"id":"BJI010","name":"宝鸡民俗博物馆","location":{"longitude":107.237,"latitude":34.362,"address":"宝鸡市渭滨区","city":"宝鸡"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["民俗","西府文化","免费"]},
])

add("恩施", [
  {"id":"ENS001","name":"恩施大峡谷","location":{"longitude":109.178,"latitude":30.218,"address":"恩施市沐抚办事处","city":"恩施"},"rating":4.7,"durationMin":480,"ticketPrice":170.0,"tags":["峡谷","喀斯特","自然"]},
  {"id":"ENS002","name":"腾龙洞","location":{"longitude":108.978,"latitude":30.277,"address":"恩施州利川市腾龙大道1号","city":"恩施"},"rating":4.4,"durationMin":180,"ticketPrice":180.0,"tags":["溶洞","亚洲最大","激光秀"]},
  {"id":"ENS003","name":"恩施土司城","location":{"longitude":109.483,"latitude":30.283,"address":"恩施市土司路138号","city":"恩施"},"rating":4.3,"durationMin":180,"ticketPrice":45.0,"tags":["土家族","土司文化","历史文化"]},
  {"id":"ENS004","name":"清江蝴蝶崖","location":{"longitude":109.610,"latitude":30.288,"address":"恩施市清江","city":"恩施"},"rating":4.3,"durationMin":240,"ticketPrice":180.0,"tags":["清江","游船","自然"]},
  {"id":"ENS005","name":"梭布垭石林","location":{"longitude":109.617,"latitude":30.476,"address":"恩施市太阳河乡","city":"恩施"},"rating":4.3,"durationMin":240,"ticketPrice":108.0,"tags":["石林","奥陶纪","自然"]},
  {"id":"ENS006","name":"女儿城","location":{"longitude":109.483,"latitude":30.283,"address":"恩施市马鞍山路41号","city":"恩施"},"rating":4.2,"durationMin":180,"ticketPrice":0.0,"tags":["土家族","民俗","美食","免费"]},
  {"id":"ENS007","name":"恩施博物馆","location":{"longitude":109.483,"latitude":30.283,"address":"恩施市金桂大道","city":"恩施"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","土家族","免费"]},
  {"id":"ENS008","name":"鹤峰屏山峡谷","location":{"longitude":110.033,"latitude":29.894,"address":"恩施州鹤峰县容美镇屏山村","city":"恩施"},"rating":4.6,"durationMin":360,"ticketPrice":0.0,"tags":["峡谷","悬浮船","自然","免费"]},
  {"id":"ENS009","name":"利川大水井","location":{"longitude":108.978,"latitude":30.277,"address":"恩施州利川市柏杨坝镇","city":"恩施"},"rating":4.2,"durationMin":120,"ticketPrice":50.0,"tags":["古建筑群","土司文化"]},
  {"id":"ENS010","name":"鹿院坪","location":{"longitude":109.178,"latitude":30.218,"address":"恩施市板桥镇新田村","city":"恩施"},"rating":4.4,"durationMin":480,"ticketPrice":128.0,"tags":["天坑","徒步","原始"]},
])

add("呼伦贝尔", [
  {"id":"HLB001","name":"呼伦贝尔大草原","location":{"longitude":119.765,"latitude":49.212,"address":"呼伦贝尔市陈巴尔虎旗","city":"呼伦贝尔"},"rating":4.8,"durationMin":600,"ticketPrice":0.0,"tags":["草原","骑马","自然","免费"]},
  {"id":"HLB002","name":"满洲里国门景区","location":{"longitude":117.412,"latitude":49.594,"address":"呼伦贝尔市满洲里市国门","city":"呼伦贝尔"},"rating":4.3,"durationMin":120,"ticketPrice":80.0,"tags":["边境","中俄","口岸"]},
  {"id":"HLB003","name":"呼伦湖","location":{"longitude":117.412,"latitude":49.594,"address":"呼伦贝尔市新巴尔虎右旗","city":"呼伦贝尔"},"rating":4.3,"durationMin":240,"ticketPrice":40.0,"tags":["湖泊","自然","草原明珠"]},
  {"id":"HLB004","name":"额尔古纳湿地","location":{"longitude":120.181,"latitude":50.243,"address":"呼伦贝尔市额尔古纳市拉布大林镇","city":"呼伦贝尔"},"rating":4.4,"durationMin":240,"ticketPrice":65.0,"tags":["湿地","亚洲第一湿地","自然"]},
  {"id":"HLB005","name":"根河敖鲁古雅使鹿部落","location":{"longitude":121.524,"latitude":50.780,"address":"呼伦贝尔市根河市敖鲁古雅乡","city":"呼伦贝尔"},"rating":4.4,"durationMin":120,"ticketPrice":80.0,"tags":["驯鹿","鄂温克族","民族文化"]},
  {"id":"HLB006","name":"莫尔道嘎国家森林公园","location":{"longitude":120.786,"latitude":51.258,"address":"呼伦贝尔市额尔古纳市莫尔道嘎镇","city":"呼伦贝尔"},"rating":4.3,"durationMin":480,"ticketPrice":160.0,"tags":["森林","大兴安岭","自然"]},
  {"id":"HLB007","name":"白桦林景区","location":{"longitude":120.181,"latitude":50.243,"address":"呼伦贝尔市额尔古纳市","city":"呼伦贝尔"},"rating":4.3,"durationMin":120,"ticketPrice":55.0,"tags":["白桦林","自然","摄影"]},
  {"id":"HLB008","name":"满洲里套娃广场","location":{"longitude":117.418,"latitude":49.592,"address":"呼伦贝尔市满洲里市套娃广场","city":"呼伦贝尔"},"rating":4.3,"durationMin":120,"ticketPrice":148.0,"tags":["主题乐园","俄罗斯文化"]},
  {"id":"HLB009","name":"黑山头古城","location":{"longitude":119.321,"latitude":50.213,"address":"呼伦贝尔市额尔古纳市黑山头镇","city":"呼伦贝尔"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["古城","辽代","免费"]},
  {"id":"HLB010","name":"室韦俄罗斯民族乡","location":{"longitude":119.905,"latitude":51.336,"address":"呼伦贝尔市额尔古纳市室韦镇","city":"呼伦贝尔"},"rating":4.3,"durationMin":240,"ticketPrice":0.0,"tags":["俄罗斯族","边境","民族风情","免费"]},
])

total = sum(len(c['attractions']) for c in data['cities'])
with open('huazai-trip-skills/src/main/resources/attractions/knowledge-base.json','w',encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print(f"\nTotal: {len(data['cities'])} cities, {total} attractions")
print("Done b20")
