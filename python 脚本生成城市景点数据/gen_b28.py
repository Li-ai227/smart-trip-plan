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

# b28: 衢州, 丽水, 黄山(市), 安庆(done), 肇庆, 汕头, 齐齐哈尔, 包头

add("衢州", [
  {"id":"QUZ001","name":"江郎山","location":{"longitude":118.567,"latitude":28.532,"address":"衢州市江山市石门镇","city":"衢州"},"rating":4.5,"durationMin":360,"ticketPrice":100.0,"tags":["世界遗产","丹霞","登山"]},
  {"id":"QUZ002","name":"廿八都古镇","location":{"longitude":118.475,"latitude":28.332,"address":"衢州市江山市廿八都镇","city":"衢州"},"rating":4.3,"durationMin":180,"ticketPrice":80.0,"tags":["古镇","千年","三省交界"]},
  {"id":"QUZ003","name":"烂柯山","location":{"longitude":118.873,"latitude":28.950,"address":"衢州市柯城区","city":"衢州"},"rating":4.1,"durationMin":120,"ticketPrice":20.0,"tags":["围棋","道教","传说"]},
  {"id":"QUZ004","name":"龙游石窟","location":{"longitude":119.182,"latitude":29.036,"address":"衢州市龙游县小南海镇","city":"衢州"},"rating":4.3,"durationMin":150,"ticketPrice":85.0,"tags":["石窟","千古之谜","地下"]},
  {"id":"QUZ005","name":"衢州孔庙","location":{"longitude":118.873,"latitude":28.950,"address":"衢州市柯城区新桥街96号","city":"衢州"},"rating":4.2,"durationMin":90,"ticketPrice":10.0,"tags":["儒家","南宗","南宋"]},
  {"id":"QUZ006","name":"根宫佛国","location":{"longitude":118.415,"latitude":29.138,"address":"衢州市开化县","city":"衢州"},"rating":4.3,"durationMin":180,"ticketPrice":120.0,"tags":["根雕","佛教","艺术"]},
  {"id":"QUZ007","name":"古田山","location":{"longitude":118.130,"latitude":29.266,"address":"衢州市开化县苏庄镇","city":"衢州"},"rating":4.1,"durationMin":240,"ticketPrice":50.0,"tags":["自然保护区","原始森林"]},
  {"id":"QUZ008","name":"水亭门","location":{"longitude":118.873,"latitude":28.950,"address":"衢州市柯城区水亭街","city":"衢州"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["历史街区","古城门","免费"]},
  {"id":"QUZ009","name":"衢州博物馆","location":{"longitude":118.873,"latitude":28.950,"address":"衢州市柯城区新桥街","city":"衢州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"QUZ010","name":"三衢石林","location":{"longitude":118.567,"latitude":28.949,"address":"衢州市常山县辉埠镇","city":"衢州"},"rating":4.2,"durationMin":180,"ticketPrice":80.0,"tags":["石林","喀斯特","自然"]},
])

add("丽水", [
  {"id":"LSH001","name":"仙都风景区","location":{"longitude":120.120,"latitude":28.678,"address":"丽水市缙云县仙都街道","city":"丽水"},"rating":4.5,"durationMin":360,"ticketPrice":90.0,"tags":["自然","鼎湖峰","影视基地"]},
  {"id":"LSH002","name":"云和梯田","location":{"longitude":119.569,"latitude":28.064,"address":"丽水市云和县崇头镇","city":"丽水"},"rating":4.5,"durationMin":240,"ticketPrice":80.0,"tags":["梯田","云海","摄影"]},
  {"id":"LSH003","name":"古堰画乡","location":{"longitude":119.724,"latitude":28.366,"address":"丽水市莲都区大港头镇","city":"丽水"},"rating":4.3,"durationMin":180,"ticketPrice":50.0,"tags":["古堰","油画","艺术"]},
  {"id":"LSH004","name":"东西岩","location":{"longitude":119.848,"latitude":28.493,"address":"丽水市莲都区老竹镇","city":"丽水"},"rating":4.2,"durationMin":180,"ticketPrice":45.0,"tags":["丹霞","自然","畲族"]},
  {"id":"LSH005","name":"遂昌金矿国家矿山公园","location":{"longitude":119.280,"latitude":28.599,"address":"丽水市遂昌县花园岭","city":"丽水"},"rating":4.3,"durationMin":240,"ticketPrice":126.0,"tags":["金矿","工业旅游","唐代"]},
  {"id":"LSH006","name":"南尖岩","location":{"longitude":119.069,"latitude":28.388,"address":"丽水市遂昌县王村口镇","city":"丽水"},"rating":4.4,"durationMin":240,"ticketPrice":80.0,"tags":["云海","梯田","摄影"]},
  {"id":"LSH007","name":"千峡湖","location":{"longitude":120.030,"latitude":28.142,"address":"丽水市青田县北山镇","city":"丽水"},"rating":4.2,"durationMin":240,"ticketPrice":100.0,"tags":["湖泊","峡谷","度假"]},
  {"id":"LSH008","name":"龙泉青瓷博物馆","location":{"longitude":119.132,"latitude":28.075,"address":"丽水市龙泉市中山西路","city":"丽水"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","非遗","青瓷","免费"]},
  {"id":"LSH009","name":"龙泉山","location":{"longitude":119.132,"latitude":28.075,"address":"丽水市龙泉市凤阳山","city":"丽水"},"rating":4.3,"durationMin":360,"ticketPrice":80.0,"tags":["登山","自然保护区","江浙第一高峰"]},
  {"id":"LSH010","name":"庆元廊桥","location":{"longitude":119.066,"latitude":27.620,"address":"丽水市庆元县","city":"丽水"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["廊桥","世界遗产预备","免费"]},
])

add("肇庆", [
  {"id":"ZQN001","name":"七星岩","location":{"longitude":112.467,"latitude":23.064,"address":"肇庆市端州区七星路","city":"肇庆"},"rating":4.4,"durationMin":300,"ticketPrice":78.0,"tags":["喀斯特","湖泊","自然"]},
  {"id":"ZQN002","name":"鼎湖山","location":{"longitude":112.551,"latitude":23.170,"address":"肇庆市鼎湖区鼎湖山","city":"肇庆"},"rating":4.5,"durationMin":360,"ticketPrice":78.0,"tags":["自然保护区","佛教","北回归线"]},
  {"id":"ZQN003","name":"端州古城墙","location":{"longitude":112.467,"latitude":23.064,"address":"肇庆市端州区宋城路","city":"肇庆"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["古城墙","宋代","免费"]},
  {"id":"ZQN004","name":"梅庵","location":{"longitude":112.447,"latitude":23.054,"address":"肇庆市端州区梅庵路","city":"肇庆"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["佛教","宋代","免费"]},
  {"id":"ZQN005","name":"肇庆博物馆","location":{"longitude":112.467,"latitude":23.064,"address":"肇庆市端州区","city":"肇庆"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"ZQN006","name":"紫云谷","location":{"longitude":112.489,"latitude":23.172,"address":"肇庆市鼎湖区","city":"肇庆"},"rating":4.2,"durationMin":240,"ticketPrice":60.0,"tags":["峡谷","端砚","自然"]},
  {"id":"ZQN007","name":"德庆龙母庙","location":{"longitude":111.782,"latitude":23.149,"address":"肇庆市德庆县悦城镇","city":"肇庆"},"rating":4.3,"durationMin":120,"ticketPrice":50.0,"tags":["民间信仰","龙母","历史文化"]},
  {"id":"ZQN008","name":"星湖湿地公园","location":{"longitude":112.467,"latitude":23.064,"address":"肇庆市端州区星湖","city":"肇庆"},"rating":4.3,"durationMin":150,"ticketPrice":78.0,"tags":["湿地","候鸟","自然"]},
  {"id":"ZQN009","name":"岩前村","location":{"longitude":112.467,"latitude":23.064,"address":"肇庆市端州区岩前村","city":"肇庆"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["文创","民宿","免费"]},
  {"id":"ZQN010","name":"盘龙峡","location":{"longitude":111.782,"latitude":23.149,"address":"肇庆市德庆县官圩镇","city":"肇庆"},"rating":4.2,"durationMin":240,"ticketPrice":80.0,"tags":["峡谷","瀑布","漂流"]},
])

add("汕头", [
  {"id":"STU001","name":"南澳岛","location":{"longitude":117.092,"latitude":23.425,"address":"汕头市南澳县","city":"汕头"},"rating":4.5,"durationMin":480,"ticketPrice":0.0,"tags":["海岛","灯塔","免费"]},
  {"id":"STU002","name":"汕头老城","location":{"longitude":116.682,"latitude":23.354,"address":"汕头市金平区小公园","city":"汕头"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["骑楼","历史街区","免费"]},
  {"id":"STU003","name":"礐石风景区","location":{"longitude":116.682,"latitude":23.354,"address":"汕头市濠江区礐石","city":"汕头"},"rating":4.2,"durationMin":180,"ticketPrice":0.0,"tags":["登山","自然","免费"]},
  {"id":"STU004","name":"陈慈簧故居","location":{"longitude":116.772,"latitude":23.592,"address":"汕头市澄海区隆都镇","city":"汕头"},"rating":4.2,"durationMin":90,"ticketPrice":35.0,"tags":["潮汕民居","岭南第一侨宅"]},
  {"id":"STU005","name":"南澳岛青澳湾","location":{"longitude":117.092,"latitude":23.425,"address":"汕头市南澳县青澳湾","city":"汕头"},"rating":4.4,"durationMin":240,"ticketPrice":0.0,"tags":["海滩","游泳","免费"]},
  {"id":"STU006","name":"汕头开埠文化陈列馆","location":{"longitude":116.682,"latitude":23.354,"address":"汕头市金平区","city":"汕头"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["开埠文化","免费"]},
  {"id":"STU007","name":"潮汕历史文化博览中心","location":{"longitude":116.682,"latitude":23.354,"address":"汕头市濠江区南滨路","city":"汕头"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["潮汕文化","免费"]},
  {"id":"STU008","name":"莲花峰","location":{"longitude":116.568,"latitude":23.223,"address":"汕头市潮阳区海门镇","city":"汕头"},"rating":4.2,"durationMin":120,"ticketPrice":20.0,"tags":["自然","文天祥","海景"]},
  {"id":"STU009","name":"汕头美食街","location":{"longitude":116.682,"latitude":23.354,"address":"汕头市金平区福平路","city":"汕头"},"rating":4.5,"durationMin":120,"ticketPrice":0.0,"tags":["潮汕美食","牛肉丸","免费"]},
  {"id":"STU010","name":"樟林古港","location":{"longitude":116.772,"latitude":23.592,"address":"汕头市澄海区东里镇","city":"汕头"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["古港","红头船","免费"]},
])

add("齐齐哈尔", [
  {"id":"QQH001","name":"扎龙自然保护区","location":{"longitude":124.251,"latitude":47.173,"address":"齐齐哈尔市铁锋区扎龙镇","city":"齐齐哈尔"},"rating":4.6,"durationMin":240,"ticketPrice":65.0,"tags":["丹顶鹤","湿地","自然"]},
  {"id":"QQH002","name":"龙沙公园","location":{"longitude":123.956,"latitude":47.351,"address":"齐齐哈尔市龙沙区公园路","city":"齐齐哈尔"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["公园","百年","免费"]},
  {"id":"QQH003","name":"明月岛","location":{"longitude":123.914,"latitude":47.357,"address":"齐齐哈尔市建华区嫩江中","city":"齐齐哈尔"},"rating":4.1,"durationMin":180,"ticketPrice":40.0,"tags":["岛屿","自然"]},
  {"id":"QQH004","name":"齐齐哈尔博物馆","location":{"longitude":123.956,"latitude":47.351,"address":"齐齐哈尔市建华区中华西路","city":"齐齐哈尔"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"QQH005","name":"昂昂溪古文化遗址","location":{"longitude":123.808,"latitude":47.145,"address":"齐齐哈尔市昂昂溪区","city":"齐齐哈尔"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["新石器","遗址","免费"]},
  {"id":"QQH006","name":"大乘寺","location":{"longitude":123.956,"latitude":47.351,"address":"齐齐哈尔市铁锋区大乘路","city":"齐齐哈尔"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["佛教","免费"]},
  {"id":"QQH007","name":"黑龙江将军府","location":{"longitude":123.956,"latitude":47.351,"address":"齐齐哈尔市建华区","city":"齐齐哈尔"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["历史文化","清代","免费"]},
  {"id":"QQH008","name":"劳动湖","location":{"longitude":123.956,"latitude":47.351,"address":"齐齐哈尔市建华区","city":"齐齐哈尔"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["湖泊","免费"]},
  {"id":"QQH009","name":"碾子山","location":{"longitude":122.887,"latitude":47.516,"address":"齐齐哈尔市碾子山区","city":"齐齐哈尔"},"rating":4.1,"durationMin":180,"ticketPrice":50.0,"tags":["登山","自然","滑雪"]},
  {"id":"QQH010","name":"卜奎清真寺","location":{"longitude":123.956,"latitude":47.351,"address":"齐齐哈尔市建华区","city":"齐齐哈尔"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["伊斯兰","免费"]},
])

add("包头", [
  {"id":"BTO001","name":"希拉穆仁草原","location":{"longitude":111.129,"latitude":41.327,"address":"包头市达尔罕茂明安联合旗","city":"包头"},"rating":4.4,"durationMin":480,"ticketPrice":80.0,"tags":["草原","蒙古族","骑马"]},
  {"id":"BTO002","name":"五当召","location":{"longitude":110.106,"latitude":40.857,"address":"包头市石拐区五当召镇","city":"包头"},"rating":4.4,"durationMin":180,"ticketPrice":60.0,"tags":["藏传佛教","小布达拉宫"]},
  {"id":"BTO003","name":"赛汗塔拉生态园","location":{"longitude":109.840,"latitude":40.657,"address":"包头市青山区建设路","city":"包头"},"rating":4.3,"durationMin":180,"ticketPrice":0.0,"tags":["城中草原","免费"]},
  {"id":"BTO004","name":"美岱召","location":{"longitude":110.703,"latitude":40.598,"address":"包头市土右旗美岱召镇","city":"包头"},"rating":4.2,"durationMin":120,"ticketPrice":30.0,"tags":["藏传佛教","明代","壁画"]},
  {"id":"BTO005","name":"北方兵器城","location":{"longitude":109.840,"latitude":40.657,"address":"包头市青山区兵工路","city":"包头"},"rating":4.2,"durationMin":120,"ticketPrice":30.0,"tags":["军事","坦克","亲子"]},
  {"id":"BTO006","name":"南海湿地","location":{"longitude":110.030,"latitude":40.561,"address":"包头市东河区南海路","city":"包头"},"rating":4.2,"durationMin":150,"ticketPrice":20.0,"tags":["湿地","自然"]},
  {"id":"BTO007","name":"包头博物馆","location":{"longitude":109.840,"latitude":40.657,"address":"包头市昆都仑区阿尔丁大街","city":"包头"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"BTO008","name":"梅力更","location":{"longitude":109.452,"latitude":40.865,"address":"包头市九原区阿嘎如泰苏木","city":"包头"},"rating":4.1,"durationMin":240,"ticketPrice":40.0,"tags":["自然","瀑布","登山"]},
  {"id":"BTO009","name":"吕祖庙","location":{"longitude":110.030,"latitude":40.561,"address":"包头市东河区吕祖庙街","city":"包头"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["道教","免费"]},
  {"id":"BTO010","name":"阿尔丁植物园","location":{"longitude":109.840,"latitude":40.657,"address":"包头市昆都仑区青年路","city":"包头"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["植物","免费"]},
])

total = sum(len(c['attractions']) for c in data['cities'])
with open('huazai-trip-skills/src/main/resources/attractions/knowledge-base.json','w',encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print(f"\nTotal: {len(data['cities'])} cities, {total} attractions")
print("Done b28")
