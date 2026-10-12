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

# 安顺(黄果树), 绵阳, 吉林市, 芜湖, 荆州, 衡阳, 延安

add("安顺", [
  {"id":"AS001","name":"黄果树瀑布","location":{"longitude":105.669,"latitude":25.989,"address":"安顺市镇宁县黄果树镇","city":"安顺"},"rating":4.8,"durationMin":360,"ticketPrice":180.0,"tags":["瀑布","自然","亚洲最大"]},
  {"id":"AS002","name":"龙宫风景区","location":{"longitude":105.879,"latitude":26.108,"address":"安顺市西秀区龙宫镇","city":"安顺"},"rating":4.5,"durationMin":240,"ticketPrice":150.0,"tags":["溶洞","暗河","自然"]},
  {"id":"AS003","name":"天龙屯堡","location":{"longitude":106.047,"latitude":26.287,"address":"安顺市平坝区天龙镇","city":"安顺"},"rating":4.3,"durationMin":120,"ticketPrice":60.0,"tags":["明代屯堡","历史文化","汉族活化石"]},
  {"id":"AS004","name":"格凸河","location":{"longitude":106.298,"latitude":25.621,"address":"安顺市紫云县格凸河镇","city":"安顺"},"rating":4.3,"durationMin":300,"ticketPrice":80.0,"tags":["喀斯特","暗河","蜘蛛人"]},
  {"id":"AS005","name":"云峰屯堡","location":{"longitude":106.047,"latitude":26.287,"address":"安顺市西秀区七眼桥镇","city":"安顺"},"rating":4.2,"durationMin":120,"ticketPrice":50.0,"tags":["屯堡","明代","历史文化"]},
  {"id":"AS006","name":"旧州古镇","location":{"longitude":106.090,"latitude":26.280,"address":"安顺市西秀区旧州镇","city":"安顺"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["古镇","屯堡","免费"]},
  {"id":"AS007","name":"夜郎洞","location":{"longitude":105.747,"latitude":26.053,"address":"安顺市镇宁县","city":"安顺"},"rating":4.1,"durationMin":120,"ticketPrice":100.0,"tags":["溶洞","夜郎文化"]},
  {"id":"AS008","name":"花江大峡谷","location":{"longitude":105.620,"latitude":25.760,"address":"安顺市关岭县花江镇","city":"安顺"},"rating":4.2,"durationMin":240,"ticketPrice":30.0,"tags":["峡谷","自然","北盘江"]},
  {"id":"AS009","name":"安顺文庙","location":{"longitude":105.947,"latitude":26.246,"address":"安顺市西秀区中华东路","city":"安顺"},"rating":4.2,"durationMin":60,"ticketPrice":10.0,"tags":["儒家","石雕","明代"]},
  {"id":"AS010","name":"虹山湖","location":{"longitude":105.932,"latitude":26.258,"address":"安顺市西秀区虹山湖路","city":"安顺"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["湖泊","免费"]},
  {"id":"AS011","name":"紫云苗寨","location":{"longitude":106.098,"latitude":25.789,"address":"安顺市紫云县格凸河镇","city":"安顺"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["苗族","民族文化","免费"]},
  {"id":"AS012","name":"关岭化石群国家地质公园","location":{"longitude":105.620,"latitude":25.760,"address":"安顺市关岭县新铺镇","city":"安顺"},"rating":4.2,"durationMin":120,"ticketPrice":80.0,"tags":["化石","地质","海百合"]},
  {"id":"AS013","name":"王若飞故居","location":{"longitude":105.947,"latitude":26.246,"address":"安顺市西秀区中华北路","city":"安顺"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["红色旅游","名人故居","免费"]},
  {"id":"AS014","name":"坝陵河大桥","location":{"longitude":105.669,"latitude":25.989,"address":"安顺市关岭县坝陵河","city":"安顺"},"rating":4.2,"durationMin":30,"ticketPrice":0.0,"tags":["现代建筑","大桥","蹦极","免费"]},
  {"id":"AS015","name":"平坝万亩樱花园","location":{"longitude":106.218,"latitude":26.388,"address":"安顺市平坝区贵安大道","city":"安顺"},"rating":4.5,"durationMin":180,"ticketPrice":0.0,"tags":["樱花","春季","世界最大樱花园","免费"]},
])

add("绵阳", [
  {"id":"MYG001","name":"北川羌城旅游区","location":{"longitude":104.466,"latitude":31.622,"address":"绵阳市北川县永昌镇","city":"绵阳"},"rating":4.4,"durationMin":240,"ticketPrice":0.0,"tags":["羌族","地震纪念","免费"]},
  {"id":"MYG002","name":"九皇山","location":{"longitude":104.649,"latitude":31.837,"address":"绵阳市北川县桂溪镇","city":"绵阳"},"rating":4.3,"durationMin":360,"ticketPrice":90.0,"tags":["羌族","自然","溶洞"]},
  {"id":"MYG003","name":"越王楼","location":{"longitude":104.757,"latitude":31.470,"address":"绵阳市游仙区剑南路东段","city":"绵阳"},"rating":4.3,"durationMin":90,"ticketPrice":50.0,"tags":["古建筑","唐代","四大名楼"]},
  {"id":"MYG004","name":"李白故里","location":{"longitude":104.691,"latitude":31.753,"address":"绵阳市江油市青莲镇","city":"绵阳"},"rating":4.2,"durationMin":120,"ticketPrice":40.0,"tags":["名人故居","李白","唐代"]},
  {"id":"MYG005","name":"富乐山","location":{"longitude":104.780,"latitude":31.479,"address":"绵阳市游仙区芙蓉路","city":"绵阳"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["园林","三国","免费"]},
  {"id":"MYG006","name":"绵阳科技馆","location":{"longitude":104.757,"latitude":31.470,"address":"绵阳市游仙区一环路东段","city":"绵阳"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["科技","中国科技城","免费"]},
  {"id":"MYG007","name":"仙海风景区","location":{"longitude":104.831,"latitude":31.535,"address":"绵阳市游仙区沉抗镇","city":"绵阳"},"rating":4.1,"durationMin":180,"ticketPrice":0.0,"tags":["湖泊","度假","免费"]},
  {"id":"MYG008","name":"药王谷","location":{"longitude":104.649,"latitude":31.837,"address":"绵阳市北川县桂溪镇","city":"绵阳"},"rating":4.2,"durationMin":240,"ticketPrice":90.0,"tags":["中药","辛夷花","自然"]},
  {"id":"MYG009","name":"窦圌山","location":{"longitude":104.805,"latitude":31.837,"address":"绵阳市江油市武都镇","city":"绵阳"},"rating":4.3,"durationMin":240,"ticketPrice":67.0,"tags":["丹霞","道教","飞索"]},
  {"id":"MYG010","name":"梓潼七曲山大庙","location":{"longitude":105.172,"latitude":31.645,"address":"绵阳市梓潼县七曲山","city":"绵阳"},"rating":4.2,"durationMin":120,"ticketPrice":40.0,"tags":["道教","文昌帝君","古建筑"]},
])

add("吉林市", [
  {"id":"JLS001","name":"松花湖风景区","location":{"longitude":126.753,"latitude":43.652,"address":"吉林市丰满区松滨路","city":"吉林市"},"rating":4.4,"durationMin":240,"ticketPrice":10.0,"tags":["湖泊","自然","夏季"]},
  {"id":"JLS002","name":"雾凇岛","location":{"longitude":126.459,"latitude":44.031,"address":"吉林市龙潭区乌拉街满族镇","city":"吉林市"},"rating":4.7,"durationMin":180,"ticketPrice":60.0,"tags":["雾凇","冬季","摄影"]},
  {"id":"JLS003","name":"北山公园","location":{"longitude":126.548,"latitude":43.842,"address":"吉林市船营区德胜路","city":"吉林市"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["公园","佛教","免费"]},
  {"id":"JLS004","name":"吉林陨石博物馆","location":{"longitude":126.553,"latitude":43.838,"address":"吉林市船营区吉林大街","city":"吉林市"},"rating":4.2,"durationMin":90,"ticketPrice":80.0,"tags":["博物馆","陨石","世界最大石陨石"]},
  {"id":"JLS005","name":"龙潭山遗址公园","location":{"longitude":126.581,"latitude":43.872,"address":"吉林市龙潭区龙山路","city":"吉林市"},"rating":4.2,"durationMin":150,"ticketPrice":0.0,"tags":["公园","高句丽遗址","免费"]},
  {"id":"JLS006","name":"朱雀山","location":{"longitude":126.717,"latitude":43.743,"address":"吉林市丰满区朱雀山","city":"吉林市"},"rating":4.1,"durationMin":240,"ticketPrice":30.0,"tags":["登山","自然","滑雪"]},
  {"id":"JLS007","name":"北大湖滑雪场","location":{"longitude":126.593,"latitude":43.416,"address":"吉林市永吉县北大湖镇","city":"吉林市"},"rating":4.6,"durationMin":480,"ticketPrice":400.0,"tags":["滑雪","冬季","运动"]},
  {"id":"JLS008","name":"乌拉街满族镇","location":{"longitude":126.459,"latitude":44.031,"address":"吉林市龙潭区乌拉街满族镇","city":"吉林市"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["满族","历史文化","古镇","免费"]},
  {"id":"JLS009","name":"松江中路夜景","location":{"longitude":126.553,"latitude":43.838,"address":"吉林市船营区松江中路","city":"吉林市"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["夜景","松花江","免费"]},
  {"id":"JLS010","name":"丰满水电站","location":{"longitude":126.717,"latitude":43.743,"address":"吉林市丰满区丰满街","city":"吉林市"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["工业","水电站","免费"]},
])

add("芜湖", [
  {"id":"WHU001","name":"方特欢乐世界","location":{"longitude":118.403,"latitude":31.342,"address":"芜湖市鸠江区赤铸山东路","city":"芜湖"},"rating":4.4,"durationMin":480,"ticketPrice":280.0,"tags":["主题乐园","亲子"]},
  {"id":"WHU002","name":"方特梦幻王国","location":{"longitude":118.403,"latitude":31.342,"address":"芜湖市鸠江区赤铸山东路","city":"芜湖"},"rating":4.3,"durationMin":420,"ticketPrice":260.0,"tags":["主题乐园","亲子"]},
  {"id":"WHU003","name":"方特东方神画","location":{"longitude":118.403,"latitude":31.342,"address":"芜湖市鸠江区","city":"芜湖"},"rating":4.3,"durationMin":420,"ticketPrice":260.0,"tags":["主题乐园","中国神话"]},
  {"id":"WHU004","name":"赭山公园","location":{"longitude":118.377,"latitude":31.340,"address":"芜湖市镜湖区赭山","city":"芜湖"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"WHU005","name":"鸠兹古镇","location":{"longitude":118.433,"latitude":31.372,"address":"芜湖市鸠江区纬二路","city":"芜湖"},"rating":4.2,"durationMin":180,"ticketPrice":80.0,"tags":["古镇","徽派建筑"]},
  {"id":"WHU006","name":"芜湖博物馆","location":{"longitude":118.422,"latitude":31.350,"address":"芜湖市鸠江区中江大道","city":"芜湖"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"WHU007","name":"镜湖公园","location":{"longitude":118.377,"latitude":31.335,"address":"芜湖市镜湖区镜湖","city":"芜湖"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["湖泊","公园","免费"]},
  {"id":"WHU008","name":"滨江公园","location":{"longitude":118.366,"latitude":31.336,"address":"芜湖市镜湖区滨江路","city":"芜湖"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["公园","长江","免费"]},
  {"id":"WHU009","name":"马仁奇峰","location":{"longitude":118.112,"latitude":30.952,"address":"芜湖市繁昌区孙村镇","city":"芜湖"},"rating":4.3,"durationMin":240,"ticketPrice":95.0,"tags":["自然","玻璃栈道","登山"]},
  {"id":"WHU010","name":"芜湖科技馆","location":{"longitude":118.400,"latitude":31.350,"address":"芜湖市鸠江区银湖北路","city":"芜湖"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["科普","亲子","免费"]},
])

add("荆州", [
  {"id":"JZH001","name":"荆州古城","location":{"longitude":112.241,"latitude":30.351,"address":"荆州市荆州区张居正街","city":"荆州"},"rating":4.4,"durationMin":240,"ticketPrice":50.0,"tags":["古城","三国","关羽"]},
  {"id":"JZH002","name":"荆州博物馆","location":{"longitude":112.205,"latitude":30.354,"address":"荆州市荆州区荆州中路166号","city":"荆州"},"rating":4.5,"durationMin":150,"ticketPrice":0.0,"tags":["博物馆","西汉古尸","免费"]},
  {"id":"JZH003","name":"关羽祠","location":{"longitude":112.241,"latitude":30.351,"address":"荆州市荆州区南门","city":"荆州"},"rating":4.2,"durationMin":60,"ticketPrice":20.0,"tags":["三国","关羽","历史文化"]},
  {"id":"JZH004","name":"张居正故居","location":{"longitude":112.241,"latitude":30.351,"address":"荆州市荆州区宾阳楼旁","city":"荆州"},"rating":4.2,"durationMin":60,"ticketPrice":20.0,"tags":["名人故居","明代首辅"]},
  {"id":"JZH005","name":"关帝庙","location":{"longitude":112.241,"latitude":30.351,"address":"荆州市荆州区南门","city":"荆州"},"rating":4.2,"durationMin":60,"ticketPrice":20.0,"tags":["关羽","三国","历史文化"]},
  {"id":"JZH006","name":"万寿宝塔","location":{"longitude":112.241,"latitude":30.351,"address":"荆州市沙市区荆江大堤","city":"荆州"},"rating":4.1,"durationMin":60,"ticketPrice":10.0,"tags":["古建筑","明代","长江"]},
  {"id":"JZH007","name":"荆州园博园","location":{"longitude":112.188,"latitude":30.370,"address":"荆州市荆州区纪南文旅区","city":"荆州"},"rating":4.2,"durationMin":240,"ticketPrice":60.0,"tags":["园林","花卉","生态"]},
  {"id":"JZH008","name":"楚王车马阵","location":{"longitude":112.150,"latitude":30.550,"address":"荆州市荆州区川店镇","city":"荆州"},"rating":4.3,"durationMin":180,"ticketPrice":100.0,"tags":["楚国","帝王陵","遗址"]},
  {"id":"JZH009","name":"洪湖蓝田","location":{"longitude":113.475,"latitude":29.808,"address":"荆州市洪湖市瞿家湾镇","city":"荆州"},"rating":4.2,"durationMin":240,"ticketPrice":80.0,"tags":["湖泊","红色旅游","荷花"]},
  {"id":"JZH010","name":"荆州海洋世界","location":{"longitude":112.241,"latitude":30.351,"address":"荆州市沙市区","city":"荆州"},"rating":4.1,"durationMin":180,"ticketPrice":120.0,"tags":["海洋馆","亲子"]},
])

add("衡阳", [
  {"id":"HYG001","name":"衡山风景区","location":{"longitude":112.735,"latitude":27.235,"address":"衡阳市南岳区衡山","city":"衡阳"},"rating":4.7,"durationMin":480,"ticketPrice":120.0,"tags":["五岳","佛教","道教","登山"]},
  {"id":"HYG002","name":"南岳大庙","location":{"longitude":112.735,"latitude":27.235,"address":"衡阳市南岳区北街","city":"衡阳"},"rating":4.5,"durationMin":120,"ticketPrice":60.0,"tags":["佛教","道教","古建筑"]},
  {"id":"HYG003","name":"石鼓书院","location":{"longitude":112.614,"latitude":26.904,"address":"衡阳市石鼓区石鼓山","city":"衡阳"},"rating":4.3,"durationMin":90,"ticketPrice":20.0,"tags":["书院","宋代四大书院","历史文化"]},
  {"id":"HYG004","name":"回雁峰","location":{"longitude":112.614,"latitude":26.904,"address":"衡阳市雁峰区雁城路","city":"衡阳"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["登山","南岳第一峰","免费"]},
  {"id":"HYG005","name":"衡阳博物馆","location":{"longitude":112.614,"latitude":26.904,"address":"衡阳市石鼓区","city":"衡阳"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"HYG006","name":"祝融峰","location":{"longitude":112.735,"latitude":27.235,"address":"衡阳市南岳区衡山","city":"衡阳"},"rating":4.7,"durationMin":180,"ticketPrice":120.0,"tags":["登山","日出","最高峰"]},
  {"id":"HYG007","name":"东洲岛","location":{"longitude":112.614,"latitude":26.904,"address":"衡阳市珠晖区湘江中","city":"衡阳"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["岛屿","湘江","免费"]},
  {"id":"HYG008","name":"南岳忠烈祠","location":{"longitude":112.735,"latitude":27.235,"address":"衡阳市南岳区衡山","city":"衡阳"},"rating":4.3,"durationMin":60,"ticketPrice":120.0,"tags":["爱国","抗日战争"]},
  {"id":"HYG009","name":"水帘洞","location":{"longitude":112.735,"latitude":27.235,"address":"衡阳市南岳区衡山","city":"衡阳"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["瀑布","自然"]},
  {"id":"HYG010","name":"衡阳抗战纪念城","location":{"longitude":112.614,"latitude":26.904,"address":"衡阳市雁峰区岳屏公园","city":"衡阳"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["爱国","免费"]},
])

add("延安", [
  {"id":"YAN001","name":"宝塔山","location":{"longitude":109.494,"latitude":36.591,"address":"延安市宝塔区宝塔山","city":"延安"},"rating":4.5,"durationMin":120,"ticketPrice":65.0,"tags":["革命圣地","地标","红色旅游"]},
  {"id":"YAN002","name":"杨家岭革命旧址","location":{"longitude":109.494,"latitude":36.591,"address":"延安市宝塔区杨家岭","city":"延安"},"rating":4.4,"durationMin":120,"ticketPrice":0.0,"tags":["红色旅游","中共七大","免费"]},
  {"id":"YAN003","name":"枣园革命旧址","location":{"longitude":109.420,"latitude":36.632,"address":"延安市宝塔区枣园路","city":"延安"},"rating":4.4,"durationMin":120,"ticketPrice":0.0,"tags":["红色旅游","毛泽东旧居","免费"]},
  {"id":"YAN004","name":"延安革命纪念馆","location":{"longitude":109.494,"latitude":36.591,"address":"延安市宝塔区王家坪","city":"延安"},"rating":4.6,"durationMin":150,"ticketPrice":0.0,"tags":["红色旅游","博物馆","免费"]},
  {"id":"YAN005","name":"黄帝陵","location":{"longitude":109.277,"latitude":35.585,"address":"延安市黄陵县桥山镇","city":"延安"},"rating":4.6,"durationMin":180,"ticketPrice":90.0,"tags":["历史文化","黄帝","中华文明"]},
  {"id":"YAN006","name":"壶口瀑布（陕西侧）","location":{"longitude":110.446,"latitude":36.148,"address":"延安市宜川县壶口镇","city":"延安"},"rating":4.7,"durationMin":180,"ticketPrice":90.0,"tags":["瀑布","黄河","自然奇观"]},
  {"id":"YAN007","name":"延安清凉山","location":{"longitude":109.494,"latitude":36.591,"address":"延安市宝塔区清凉山","city":"延安"},"rating":4.3,"durationMin":120,"ticketPrice":40.0,"tags":["佛教","石窟","红色旅游"]},
  {"id":"YAN008","name":"南泥湾","location":{"longitude":109.727,"latitude":36.193,"address":"延安市宝塔区南泥湾镇","city":"延安"},"rating":4.2,"durationMin":180,"ticketPrice":0.0,"tags":["红色旅游","自力更生","免费"]},
  {"id":"YAN009","name":"王家坪革命旧址","location":{"longitude":109.494,"latitude":36.591,"address":"延安市宝塔区王家坪","city":"延安"},"rating":4.3,"durationMin":90,"ticketPrice":0.0,"tags":["红色旅游","免费"]},
  {"id":"YAN010","name":"乾坤湾","location":{"longitude":110.446,"latitude":36.148,"address":"延安市延川县乾坤湾镇","city":"延安"},"rating":4.4,"durationMin":240,"ticketPrice":90.0,"tags":["黄河","自然","蛇曲"]},
])

total = sum(len(c['attractions']) for c in data['cities'])
with open('huazai-trip-skills/src/main/resources/attractions/knowledge-base.json','w',encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print(f"\nTotal: {len(data['cities'])} cities, {total} attractions")
print("Done b19")
