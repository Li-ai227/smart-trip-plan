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

# b25: 平遥, 五台山, 牡丹江, 安庆, 常德, 南充, 玉溪, 吐鲁番

add("晋中", [
  {"id":"JZ001","name":"平遥古城","location":{"longitude":112.185,"latitude":37.189,"address":"晋中市平遥县古城","city":"晋中"},"rating":4.7,"durationMin":480,"ticketPrice":130.0,"tags":["世界遗产","古城","明清"]},
  {"id":"JZ002","name":"乔家大院","location":{"longitude":112.536,"latitude":37.397,"address":"晋中市祁县乔家堡村","city":"晋中"},"rating":4.5,"durationMin":180,"ticketPrice":138.0,"tags":["晋商","清代民居","大红灯笼"]},
  {"id":"JZ003","name":"王家大院","location":{"longitude":111.872,"latitude":36.846,"address":"晋中市灵石县静升镇","city":"晋中"},"rating":4.5,"durationMin":240,"ticketPrice":66.0,"tags":["晋商","华夏第一宅","清代"]},
  {"id":"JZ004","name":"双林寺","location":{"longitude":112.185,"latitude":37.189,"address":"晋中市平遥县双林寺","city":"晋中"},"rating":4.4,"durationMin":90,"ticketPrice":35.0,"tags":["佛教","彩塑","世界遗产"]},
  {"id":"JZ005","name":"镇国寺","location":{"longitude":112.185,"latitude":37.189,"address":"晋中市平遥县郝洞村","city":"晋中"},"rating":4.3,"durationMin":60,"ticketPrice":25.0,"tags":["佛教","五代","世界遗产"]},
  {"id":"JZ006","name":"常家庄园","location":{"longitude":112.754,"latitude":37.662,"address":"晋中市榆次区东阳镇","city":"晋中"},"rating":4.3,"durationMin":180,"ticketPrice":80.0,"tags":["晋商","园林","清代"]},
  {"id":"JZ007","name":"平遥县衙","location":{"longitude":112.185,"latitude":37.189,"address":"晋中市平遥县古城内","city":"晋中"},"rating":4.2,"durationMin":90,"ticketPrice":130.0,"tags":["明清","衙署","历史文化"]},
  {"id":"JZ008","name":"平遥文庙","location":{"longitude":112.185,"latitude":37.189,"address":"晋中市平遥县城隍庙街","city":"晋中"},"rating":4.2,"durationMin":60,"ticketPrice":130.0,"tags":["儒家","金代","古建筑"]},
  {"id":"JZ009","name":"又见平遥","location":{"longitude":112.185,"latitude":37.189,"address":"晋中市平遥县顺城路154号","city":"晋中"},"rating":4.7,"durationMin":90,"ticketPrice":238.0,"tags":["实景演出","沉浸式"]},
  {"id":"JZ010","name":"张壁古堡","location":{"longitude":111.957,"latitude":37.025,"address":"晋中市介休市龙凤镇张壁村","city":"晋中"},"rating":4.3,"durationMin":120,"ticketPrice":60.0,"tags":["古堡","地道","军事"]},
])

add("忻州", [
  {"id":"XZH001","name":"五台山","location":{"longitude":113.565,"latitude":39.030,"address":"忻州市五台县台怀镇","city":"忻州"},"rating":4.7,"durationMin":600,"ticketPrice":135.0,"tags":["佛教","文殊菩萨","世界遗产"]},
  {"id":"XZH002","name":"五台山菩萨顶","location":{"longitude":113.590,"latitude":39.011,"address":"忻州市五台县台怀镇灵鹫峰","city":"忻州"},"rating":4.5,"durationMin":90,"ticketPrice":10.0,"tags":["佛教","清代","黄教"]},
  {"id":"XZH003","name":"五台山塔院寺","location":{"longitude":113.590,"latitude":39.011,"address":"忻州市五台县台怀镇","city":"忻州"},"rating":4.5,"durationMin":90,"ticketPrice":10.0,"tags":["佛教","大白塔","地标"]},
  {"id":"XZH004","name":"五台山显通寺","location":{"longitude":113.590,"latitude":39.011,"address":"忻州市五台县台怀镇","city":"忻州"},"rating":4.5,"durationMin":120,"ticketPrice":10.0,"tags":["佛教","东汉","五台第一寺"]},
  {"id":"XZH005","name":"五台山黛螺顶","location":{"longitude":113.590,"latitude":39.011,"address":"忻州市五台县台怀镇","city":"忻州"},"rating":4.5,"durationMin":180,"ticketPrice":8.0,"tags":["佛教","小朝台","登山"]},
  {"id":"XZH006","name":"雁门关","location":{"longitude":112.860,"latitude":39.186,"address":"忻州市代县雁门关","city":"忻州"},"rating":4.4,"durationMin":240,"ticketPrice":90.0,"tags":["长城","名关","杨家将"]},
  {"id":"XZH007","name":"芦芽山","location":{"longitude":112.009,"latitude":38.817,"address":"忻州市宁武县东寨镇","city":"忻州"},"rating":4.3,"durationMin":360,"ticketPrice":70.0,"tags":["自然","原始森林","高山草原"]},
  {"id":"XZH008","name":"万年冰洞","location":{"longitude":112.009,"latitude":38.817,"address":"忻州市宁武县东寨镇","city":"忻州"},"rating":4.4,"durationMin":90,"ticketPrice":120.0,"tags":["冰洞","奇观","自然"]},
  {"id":"XZH009","name":"忻州古城","location":{"longitude":112.734,"latitude":38.417,"address":"忻州市忻府区古城","city":"忻州"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["古城","免费"]},
  {"id":"XZH010","name":"阎锡山故居","location":{"longitude":113.041,"latitude":38.550,"address":"忻州市定襄县河边镇","city":"忻州"},"rating":4.1,"durationMin":90,"ticketPrice":40.0,"tags":["名人故居","民国"]},
])

add("牡丹江", [
  {"id":"MDJ001","name":"镜泊湖","location":{"longitude":128.967,"latitude":43.873,"address":"牡丹江市宁安市镜泊湖","city":"牡丹江"},"rating":4.5,"durationMin":360,"ticketPrice":100.0,"tags":["湖泊","火山","吊水楼瀑布"]},
  {"id":"MDJ002","name":"中国雪乡","location":{"longitude":128.490,"latitude":44.166,"address":"牡丹江市海林市长汀镇双峰林场","city":"牡丹江"},"rating":4.6,"durationMin":480,"ticketPrice":120.0,"tags":["雪景","童话世界","冬季"]},
  {"id":"MDJ003","name":"地下森林","location":{"longitude":128.967,"latitude":43.873,"address":"牡丹江市宁安市镜泊湖","city":"牡丹江"},"rating":4.3,"durationMin":240,"ticketPrice":50.0,"tags":["火山口","森林","自然"]},
  {"id":"MDJ004","name":"绥芬河口岸","location":{"longitude":131.157,"latitude":44.379,"address":"牡丹江市绥芬河市","city":"牡丹江"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["边境","中俄","口岸","免费"]},
  {"id":"MDJ005","name":"牡丹峰","location":{"longitude":129.633,"latitude":44.550,"address":"牡丹江市东安区","city":"牡丹江"},"rating":4.1,"durationMin":180,"ticketPrice":30.0,"tags":["登山","自然","滑雪"]},
  {"id":"MDJ006","name":"威虎山","location":{"longitude":129.262,"latitude":44.865,"address":"牡丹江市海林市柴河镇","city":"牡丹江"},"rating":4.2,"durationMin":240,"ticketPrice":60.0,"tags":["历史文化","林海雪原","红色"]},
  {"id":"MDJ007","name":"渤海国上京龙泉府遗址","location":{"longitude":129.340,"latitude":44.119,"address":"牡丹江市宁安市渤海镇","city":"牡丹江"},"rating":4.2,"durationMin":120,"ticketPrice":35.0,"tags":["历史文化","渤海国","唐代"]},
  {"id":"MDJ008","name":"三道关","location":{"longitude":129.633,"latitude":44.550,"address":"牡丹江市爱民区三道关","city":"牡丹江"},"rating":4.0,"durationMin":180,"ticketPrice":40.0,"tags":["峡谷","自然"]},
  {"id":"MDJ009","name":"牡丹江博物馆","location":{"longitude":129.633,"latitude":44.550,"address":"牡丹江市西安区","city":"牡丹江"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"MDJ010","name":"横道河子俄罗斯风情小镇","location":{"longitude":129.262,"latitude":44.865,"address":"牡丹江市海林市横道河子镇","city":"牡丹江"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["俄式建筑","影视基地","免费"]},
])

add("安庆", [
  {"id":"AQG001","name":"天柱山","location":{"longitude":116.484,"latitude":30.742,"address":"安庆市潜山市天柱山镇","city":"安庆"},"rating":4.5,"durationMin":480,"ticketPrice":130.0,"tags":["世界地质公园","登山","自然"]},
  {"id":"AQG002","name":"迎江寺","location":{"longitude":117.046,"latitude":30.506,"address":"安庆市迎江区沿江东路","city":"安庆"},"rating":4.2,"durationMin":90,"ticketPrice":10.0,"tags":["佛教","明代","长江"]},
  {"id":"AQG003","name":"振风塔","location":{"longitude":117.046,"latitude":30.506,"address":"安庆市迎江区沿江东路迎江寺内","city":"安庆"},"rating":4.2,"durationMin":60,"ticketPrice":10.0,"tags":["古建筑","明代","长江第一塔"]},
  {"id":"AQG004","name":"菱湖公园","location":{"longitude":117.046,"latitude":30.506,"address":"安庆市大观区菱湖南路","city":"安庆"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"AQG005","name":"独秀园","location":{"longitude":117.046,"latitude":30.506,"address":"安庆市大观区十里铺乡","city":"安庆"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["名人纪念","陈独秀","免费"]},
  {"id":"AQG006","name":"安庆博物馆","location":{"longitude":117.046,"latitude":30.506,"address":"安庆市迎江区潜江路","city":"安庆"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"AQG007","name":"花亭湖","location":{"longitude":116.296,"latitude":30.494,"address":"安庆市太湖县花亭湖","city":"安庆"},"rating":4.2,"durationMin":240,"ticketPrice":100.0,"tags":["湖泊","水库","自然"]},
  {"id":"AQG008","name":"黄梅戏博物馆","location":{"longitude":117.046,"latitude":30.506,"address":"安庆市迎江区","city":"安庆"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["非遗","黄梅戏","免费"]},
  {"id":"AQG009","name":"倒扒狮街","location":{"longitude":117.046,"latitude":30.506,"address":"安庆市迎江区倒扒狮街","city":"安庆"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["历史街区","免费"]},
  {"id":"AQG010","name":"巨石山","location":{"longitude":117.046,"latitude":30.506,"address":"安庆市宜秀区罗岭镇","city":"安庆"},"rating":4.2,"durationMin":240,"ticketPrice":90.0,"tags":["登山","自然","玻璃栈道"]},
])

add("常德", [
  {"id":"CDE001","name":"桃花源","location":{"longitude":111.528,"latitude":28.791,"address":"常德市桃源县桃花源镇","city":"常德"},"rating":4.4,"durationMin":360,"ticketPrice":128.0,"tags":["世外桃源","陶渊明","自然"]},
  {"id":"CDE002","name":"柳叶湖","location":{"longitude":111.697,"latitude":29.052,"address":"常德市武陵区柳叶大道","city":"常德"},"rating":4.3,"durationMin":180,"ticketPrice":0.0,"tags":["湖泊","度假","免费"]},
  {"id":"CDE003","name":"常德博物馆","location":{"longitude":111.697,"latitude":29.052,"address":"常德市武陵区武陵大道","city":"常德"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"CDE004","name":"诗墙公园","location":{"longitude":111.697,"latitude":29.052,"address":"常德市武陵区沅安路","city":"常德"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["文化墙","诗词","免费"]},
  {"id":"CDE005","name":"壶瓶山","location":{"longitude":110.808,"latitude":30.008,"address":"常德市石门县壶瓶山镇","city":"常德"},"rating":4.2,"durationMin":480,"ticketPrice":80.0,"tags":["登山","湖南屋脊","自然"]},
  {"id":"CDE006","name":"夹山寺","location":{"longitude":111.425,"latitude":29.621,"address":"常德市石门县夹山镇","city":"常德"},"rating":4.1,"durationMin":90,"ticketPrice":40.0,"tags":["佛教","禅宗"]},
  {"id":"CDE007","name":"城头山遗址","location":{"longitude":111.736,"latitude":29.687,"address":"常德市澧县城头山镇","city":"常德"},"rating":4.2,"durationMin":120,"ticketPrice":75.0,"tags":["遗址","新石器","稻田"]},
  {"id":"CDE008","name":"常德河街","location":{"longitude":111.697,"latitude":29.052,"address":"常德市武陵区河街","city":"常德"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["历史街区","美食","免费"]},
  {"id":"CDE009","name":"花岩溪","location":{"longitude":111.583,"latitude":28.948,"address":"常德市鼎城区花岩溪镇","city":"常德"},"rating":4.1,"durationMin":240,"ticketPrice":70.0,"tags":["自然","白鹭","森林"]},
  {"id":"CDE010","name":"常德欢乐水世界","location":{"longitude":111.697,"latitude":29.052,"address":"常德市武陵区柳叶湖","city":"常德"},"rating":4.1,"durationMin":300,"ticketPrice":130.0,"tags":["水上乐园","夏季","亲子"]},
])

add("南充", [
  {"id":"NCH001","name":"阆中古城","location":{"longitude":105.985,"latitude":31.579,"address":"南充市阆中市古城","city":"南充"},"rating":4.5,"durationMin":360,"ticketPrice":0.0,"tags":["古城","春节文化","风水","免费"]},
  {"id":"NCH002","name":"朱德故里","location":{"longitude":106.385,"latitude":31.495,"address":"南充市仪陇县马鞍镇","city":"南充"},"rating":4.3,"durationMin":180,"ticketPrice":0.0,"tags":["红色旅游","朱德","免费"]},
  {"id":"NCH003","name":"西山风景区","location":{"longitude":106.080,"latitude":30.790,"address":"南充市顺庆区西山路","city":"南充"},"rating":4.2,"durationMin":150,"ticketPrice":0.0,"tags":["登山","道教","免费"]},
  {"id":"NCH004","name":"南充博物馆","location":{"longitude":106.080,"latitude":30.790,"address":"南充市顺庆区","city":"南充"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","三国","免费"]},
  {"id":"NCH005","name":"凌云山","location":{"longitude":106.147,"latitude":30.741,"address":"南充市高坪区凌云山","city":"南充"},"rating":4.2,"durationMin":180,"ticketPrice":30.0,"tags":["佛教","道教","登山"]},
  {"id":"NCH006","name":"升钟湖","location":{"longitude":105.746,"latitude":31.451,"address":"南充市南部县升水镇","city":"南充"},"rating":4.2,"durationMin":240,"ticketPrice":0.0,"tags":["湖泊","钓鱼","免费"]},
  {"id":"NCH007","name":"嘉陵江夜游","location":{"longitude":106.080,"latitude":30.790,"address":"南充市顺庆区嘉陵江","city":"南充"},"rating":4.2,"durationMin":90,"ticketPrice":80.0,"tags":["游船","夜景"]},
  {"id":"NCH008","name":"北湖公园","location":{"longitude":106.080,"latitude":30.790,"address":"南充市顺庆区文化路","city":"南充"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"NCH009","name":"罗瑞卿故居","location":{"longitude":106.080,"latitude":30.790,"address":"南充市顺庆区将军路","city":"南充"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["红色旅游","免费"]},
  {"id":"NCH010","name":"阆中张飞庙","location":{"longitude":105.985,"latitude":31.579,"address":"南充市阆中市古城","city":"南充"},"rating":4.2,"durationMin":60,"ticketPrice":50.0,"tags":["三国","张飞","历史文化"]},
])

add("玉溪", [
  {"id":"YUX001","name":"抚仙湖","location":{"longitude":102.890,"latitude":24.521,"address":"玉溪市澄江市抚仙湖","city":"玉溪"},"rating":4.7,"durationMin":480,"ticketPrice":0.0,"tags":["湖泊","自然","免费"]},
  {"id":"YUX002","name":"帽天山古生物化石地","location":{"longitude":102.890,"latitude":24.521,"address":"玉溪市澄江市帽天山","city":"玉溪"},"rating":4.3,"durationMin":120,"ticketPrice":60.0,"tags":["世界遗产","寒武纪","化石"]},
  {"id":"YUX003","name":"玉溪红塔","location":{"longitude":102.545,"latitude":24.370,"address":"玉溪市红塔区红塔山","city":"玉溪"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["地标","免费"]},
  {"id":"YUX004","name":"聂耳故居","location":{"longitude":102.545,"latitude":24.370,"address":"玉溪市红塔区北门街","city":"玉溪"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["名人故居","音乐家","免费"]},
  {"id":"YUX005","name":"通海秀山","location":{"longitude":102.762,"latitude":24.120,"address":"玉溪市通海县秀山镇","city":"玉溪"},"rating":4.2,"durationMin":180,"ticketPrice":30.0,"tags":["登山","佛教","园林"]},
  {"id":"YUX006","name":"禄充风景区","location":{"longitude":102.890,"latitude":24.521,"address":"玉溪市澄江市禄充","city":"玉溪"},"rating":4.2,"durationMin":240,"ticketPrice":15.0,"tags":["沙滩","抚仙湖"]},
  {"id":"YUX007","name":"孤山岛","location":{"longitude":102.890,"latitude":24.521,"address":"玉溪市澄江市抚仙湖中","city":"玉溪"},"rating":4.2,"durationMin":120,"ticketPrice":15.0,"tags":["岛屿","抚仙湖"]},
  {"id":"YUX008","name":"玉溪博物馆","location":{"longitude":102.545,"latitude":24.370,"address":"玉溪市红塔区红塔大道","city":"玉溪"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"YUX009","name":"九龙池","location":{"longitude":102.545,"latitude":24.370,"address":"玉溪市红塔区九龙路","city":"玉溪"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["公园","泉水","免费"]},
  {"id":"YUX010","name":"戛洒镇","location":{"longitude":101.600,"latitude":24.050,"address":"玉溪市新平县戛洒镇","city":"玉溪"},"rating":4.2,"durationMin":180,"ticketPrice":0.0,"tags":["傣族","花腰傣","免费"]},
])

add("吐鲁番", [
  {"id":"TLF001","name":"火焰山","location":{"longitude":89.563,"latitude":42.868,"address":"吐鲁番市高昌区火焰山","city":"吐鲁番"},"rating":4.3,"durationMin":120,"ticketPrice":40.0,"tags":["西游记","自然","高温"]},
  {"id":"TLF002","name":"葡萄沟","location":{"longitude":89.221,"latitude":42.932,"address":"吐鲁番市高昌区葡萄沟","city":"吐鲁番"},"rating":4.4,"durationMin":240,"ticketPrice":75.0,"tags":["葡萄","维吾尔族","夏季"]},
  {"id":"TLF003","name":"交河故城","location":{"longitude":89.071,"latitude":42.973,"address":"吐鲁番市高昌区亚尔镇","city":"吐鲁番"},"rating":4.5,"durationMin":180,"ticketPrice":70.0,"tags":["世界遗产","故城","唐代"]},
  {"id":"TLF004","name":"高昌故城","location":{"longitude":89.547,"latitude":42.863,"address":"吐鲁番市高昌区三堡乡","city":"吐鲁番"},"rating":4.3,"durationMin":150,"ticketPrice":70.0,"tags":["世界遗产","故城","玄奘"]},
  {"id":"TLF005","name":"坎儿井民俗园","location":{"longitude":89.190,"latitude":42.950,"address":"吐鲁番市高昌区亚尔乡","city":"吐鲁番"},"rating":4.3,"durationMin":120,"ticketPrice":40.0,"tags":["水利工程","维吾尔族文化"]},
  {"id":"TLF006","name":"柏孜克里克千佛洞","location":{"longitude":89.543,"latitude":42.891,"address":"吐鲁番市高昌区火焰山","city":"吐鲁番"},"rating":4.2,"durationMin":120,"ticketPrice":40.0,"tags":["石窟","佛教","唐代"]},
  {"id":"TLF007","name":"库木塔格沙漠","location":{"longitude":90.210,"latitude":42.861,"address":"吐鲁番市鄯善县库木塔格","city":"吐鲁番"},"rating":4.5,"durationMin":240,"ticketPrice":30.0,"tags":["沙漠","自然","摄影"]},
  {"id":"TLF008","name":"吐峪沟","location":{"longitude":89.650,"latitude":42.870,"address":"吐鲁番市鄯善县吐峪沟乡","city":"吐鲁番"},"rating":4.3,"durationMin":120,"ticketPrice":45.0,"tags":["峡谷","维吾尔族村落"]},
  {"id":"TLF009","name":"艾丁湖","location":{"longitude":89.054,"latitude":42.650,"address":"吐鲁番市高昌区艾丁湖","city":"吐鲁番"},"rating":4.1,"durationMin":90,"ticketPrice":30.0,"tags":["湖泊","中国最低点"]},
  {"id":"TLF010","name":"吐鲁番博物馆","location":{"longitude":89.190,"latitude":42.950,"address":"吐鲁番市高昌区木纳尔路","city":"吐鲁番"},"rating":4.3,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","丝绸之路","免费"]},
])

total = sum(len(c['attractions']) for c in data['cities'])
with open('huazai-trip-skills/src/main/resources/attractions/knowledge-base.json','w',encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print(f"\nTotal: {len(data['cities'])} cities, {total} attractions")
print("Done b25")
