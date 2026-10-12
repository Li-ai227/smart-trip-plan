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

# b27: 邯郸, 运城, 盐城, 淮安, 株洲, 湘潭, 泸州, 日喀则

add("邯郸", [
  {"id":"HDG001","name":"娲皇宫","location":{"longitude":113.692,"latitude":36.597,"address":"邯郸市涉县索堡镇","city":"邯郸"},"rating":4.4,"durationMin":180,"ticketPrice":80.0,"tags":["女娲","石窟","道教"]},
  {"id":"HDG002","name":"响堂山石窟","location":{"longitude":114.177,"latitude":36.422,"address":"邯郸市峰峰矿区","city":"邯郸"},"rating":4.4,"durationMin":150,"ticketPrice":60.0,"tags":["石窟","北齐","佛教"]},
  {"id":"HDG003","name":"丛台公园","location":{"longitude":114.498,"latitude":36.610,"address":"邯郸市丛台区中华北大街","city":"邯郸"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["公园","战国","免费"]},
  {"id":"HDG004","name":"邯郸博物馆","location":{"longitude":114.498,"latitude":36.610,"address":"邯郸市丛台区中华北大街","city":"邯郸"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["博物馆","赵国","免费"]},
  {"id":"HDG005","name":"京娘湖","location":{"longitude":113.912,"latitude":36.860,"address":"邯郸市武安市活水乡","city":"邯郸"},"rating":4.2,"durationMin":240,"ticketPrice":80.0,"tags":["湖泊","自然","传说"]},
  {"id":"HDG006","name":"广府古城","location":{"longitude":114.727,"latitude":36.699,"address":"邯郸市永年区广府镇","city":"邯郸"},"rating":4.3,"durationMin":180,"ticketPrice":100.0,"tags":["古城","太极之乡","明代"]},
  {"id":"HDG007","name":"七步沟","location":{"longitude":113.912,"latitude":36.860,"address":"邯郸市武安市活水乡","city":"邯郸"},"rating":4.2,"durationMin":240,"ticketPrice":80.0,"tags":["峡谷","自然","漂流"]},
  {"id":"HDG008","name":"赵王城遗址","location":{"longitude":114.455,"latitude":36.573,"address":"邯郸市复兴区赵王城","city":"邯郸"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["遗址","赵国","免费"]},
  {"id":"HDG009","name":"武灵丛台","location":{"longitude":114.498,"latitude":36.610,"address":"邯郸市丛台区中华北大街","city":"邯郸"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["古建筑","赵武灵王","免费"]},
  {"id":"HDG010","name":"太行五指山","location":{"longitude":113.692,"latitude":36.597,"address":"邯郸市涉县","city":"邯郸"},"rating":4.1,"durationMin":240,"ticketPrice":70.0,"tags":["登山","自然","红叶"]},
])

add("运城", [
  {"id":"YCG001","name":"解州关帝庙","location":{"longitude":110.862,"latitude":34.914,"address":"运城市盐湖区解州镇","city":"运城"},"rating":4.5,"durationMin":150,"ticketPrice":60.0,"tags":["关公","全国最大关帝庙"]},
  {"id":"YCG002","name":"永乐宫","location":{"longitude":110.691,"latitude":34.721,"address":"运城市芮城县古魏镇","city":"运城"},"rating":4.5,"durationMin":120,"ticketPrice":50.0,"tags":["道教","元代壁画","世界遗产"]},
  {"id":"YCG003","name":"运城盐湖","location":{"longitude":110.998,"latitude":34.963,"address":"运城市盐湖区","city":"运城"},"rating":4.2,"durationMin":120,"ticketPrice":50.0,"tags":["盐湖","死海","自然"]},
  {"id":"YCG004","name":"普救寺","location":{"longitude":110.340,"latitude":34.843,"address":"运城市永济市蒲州镇","city":"运城"},"rating":4.2,"durationMin":90,"ticketPrice":50.0,"tags":["西厢记","佛教","唐代"]},
  {"id":"YCG005","name":"鹳雀楼","location":{"longitude":110.296,"latitude":34.824,"address":"运城市永济市蒲州镇","city":"运城"},"rating":4.3,"durationMin":90,"ticketPrice":50.0,"tags":["古建筑","王之涣","黄河"]},
  {"id":"YCG006","name":"五老峰","location":{"longitude":110.584,"latitude":34.832,"address":"运城市永济市虞乡镇","city":"运城"},"rating":4.2,"durationMin":240,"ticketPrice":60.0,"tags":["登山","道教","自然"]},
  {"id":"YCG007","name":"风陵渡","location":{"longitude":110.207,"latitude":34.607,"address":"运城市芮城县风陵渡镇","city":"运城"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["黄河","金庸","免费"]},
  {"id":"YCG008","name":"李家大院","location":{"longitude":110.790,"latitude":35.416,"address":"运城市万荣县阎景村","city":"运城"},"rating":4.2,"durationMin":120,"ticketPrice":60.0,"tags":["晋商","民居","慈善"]},
  {"id":"YCG009","name":"后土祠","location":{"longitude":110.452,"latitude":35.312,"address":"运城市万荣县庙前村","city":"运城"},"rating":4.1,"durationMin":90,"ticketPrice":20.0,"tags":["祭祀","后土","汉代"]},
  {"id":"YCG010","name":"舜帝陵","location":{"longitude":110.998,"latitude":34.963,"address":"运城市盐湖区","city":"运城"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["历史文化","舜帝","免费"]},
])

add("盐城", [
  {"id":"YAC001","name":"中华麋鹿园","location":{"longitude":120.582,"latitude":33.065,"address":"盐城市大丰区麋鹿保护区","city":"盐城"},"rating":4.4,"durationMin":240,"ticketPrice":55.0,"tags":["麋鹿","自然保护区","动物"]},
  {"id":"YAC002","name":"丹顶鹤自然保护区","location":{"longitude":120.360,"latitude":33.600,"address":"盐城市射阳县新洋港","city":"盐城"},"rating":4.3,"durationMin":240,"ticketPrice":45.0,"tags":["丹顶鹤","湿地","候鸟"]},
  {"id":"YAC003","name":"大纵湖","location":{"longitude":119.841,"latitude":33.307,"address":"盐城市盐都区大纵湖镇","city":"盐城"},"rating":4.2,"durationMin":180,"ticketPrice":100.0,"tags":["湖泊","自然","芦苇"]},
  {"id":"YAC004","name":"新四军纪念馆","location":{"longitude":120.161,"latitude":33.360,"address":"盐城市亭湖区建军东路159号","city":"盐城"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["红色旅游","新四军","免费"]},
  {"id":"YAC005","name":"盐城海盐博物馆","location":{"longitude":120.149,"latitude":33.364,"address":"盐城市亭湖区开放大道","city":"盐城"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","海盐文化","免费"]},
  {"id":"YAC006","name":"荷兰花海","location":{"longitude":120.468,"latitude":33.163,"address":"盐城市大丰区新丰镇","city":"盐城"},"rating":4.4,"durationMin":180,"ticketPrice":50.0,"tags":["花海","郁金香","春季"]},
  {"id":"YAC007","name":"九龙口","location":{"longitude":119.687,"latitude":33.441,"address":"盐城市建湖县九龙口镇","city":"盐城"},"rating":4.2,"durationMin":180,"ticketPrice":45.0,"tags":["湖泊","自然","九河交汇"]},
  {"id":"YAC008","name":"水街","location":{"longitude":120.156,"latitude":33.363,"address":"盐城市亭湖区水街","city":"盐城"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["仿古街区","美食","免费"]},
  {"id":"YAC009","name":"泰山寺","location":{"longitude":120.161,"latitude":33.360,"address":"盐城市东台市西溪景区","city":"盐城"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["佛教","免费"]},
  {"id":"YAC010","name":"黄海森林公园","location":{"longitude":120.582,"latitude":33.065,"address":"盐城市东台市沿海","city":"盐城"},"rating":4.3,"durationMin":240,"ticketPrice":50.0,"tags":["森林","自然","生态"]},
])

add("淮安", [
  {"id":"HUA001","name":"周恩来故居","location":{"longitude":119.021,"latitude":33.610,"address":"淮安市淮安区驸马巷7号","city":"淮安"},"rating":4.5,"durationMin":120,"ticketPrice":0.0,"tags":["红色旅游","周恩来","免费"]},
  {"id":"HUA002","name":"淮安府署","location":{"longitude":119.142,"latitude":33.503,"address":"淮安市淮安区东门大街38号","city":"淮安"},"rating":4.3,"durationMin":120,"ticketPrice":60.0,"tags":["衙署","明清","历史文化"]},
  {"id":"HUA003","name":"河下古镇","location":{"longitude":119.021,"latitude":33.610,"address":"淮安市淮安区河下古镇","city":"淮安"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["古镇","千年","免费"]},
  {"id":"HUA004","name":"中国漕运博物馆","location":{"longitude":119.142,"latitude":33.503,"address":"淮安市淮安区漕运广场","city":"淮安"},"rating":4.2,"durationMin":90,"ticketPrice":60.0,"tags":["博物馆","漕运","历史文化"]},
  {"id":"HUA005","name":"吴承恩故居","location":{"longitude":119.021,"latitude":33.610,"address":"淮安市淮安区河下古镇","city":"淮安"},"rating":4.2,"durationMin":90,"ticketPrice":40.0,"tags":["名人故居","西游记"]},
  {"id":"HUA006","name":"洪泽湖古堰","location":{"longitude":118.622,"latitude":33.352,"address":"淮安市洪泽区洪泽湖","city":"淮安"},"rating":4.2,"durationMin":180,"ticketPrice":0.0,"tags":["水利工程","世界遗产","免费"]},
  {"id":"HUA007","name":"金湖水上森林","location":{"longitude":119.021,"latitude":33.610,"address":"淮安市金湖县银涂镇","city":"淮安"},"rating":4.3,"durationMin":180,"ticketPrice":80.0,"tags":["水杉","自然","划船"]},
  {"id":"HUA008","name":"淮安博物馆","location":{"longitude":119.021,"latitude":33.610,"address":"淮安市清江浦区","city":"淮安"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"HUA009","name":"铁山寺","location":{"longitude":118.461,"latitude":32.812,"address":"淮安市盱眙县铁山寺","city":"淮安"},"rating":4.2,"durationMin":180,"ticketPrice":80.0,"tags":["佛教","自然","森林"]},
  {"id":"HUA010","name":"里运河文化长廊","location":{"longitude":119.021,"latitude":33.610,"address":"淮安市清江浦区里运河","city":"淮安"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["运河","夜景","免费"]},
])

add("株洲", [
  {"id":"ZZH001","name":"炎帝陵","location":{"longitude":113.688,"latitude":26.455,"address":"株洲市炎陵县鹿原镇","city":"株洲"},"rating":4.3,"durationMin":180,"ticketPrice":78.0,"tags":["历史文化","炎帝","祭祀"]},
  {"id":"ZZH002","name":"云阳山","location":{"longitude":113.558,"latitude":26.727,"address":"株洲市茶陵县云阳山","city":"株洲"},"rating":4.2,"durationMin":240,"ticketPrice":45.0,"tags":["登山","道教","自然"]},
  {"id":"ZZH003","name":"神农谷","location":{"longitude":114.031,"latitude":26.515,"address":"株洲市炎陵县十都镇","city":"株洲"},"rating":4.2,"durationMin":300,"ticketPrice":75.0,"tags":["峡谷","自然","珠帘瀑布"]},
  {"id":"ZZH004","name":"株洲方特欢乐世界","location":{"longitude":113.131,"latitude":27.828,"address":"株洲市云龙示范区","city":"株洲"},"rating":4.3,"durationMin":480,"ticketPrice":280.0,"tags":["主题乐园","亲子"]},
  {"id":"ZZH005","name":"醴陵陶瓷博物馆","location":{"longitude":113.498,"latitude":27.657,"address":"株洲市醴陵市瓷谷大道","city":"株洲"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["陶瓷","非遗","免费"]},
  {"id":"ZZH006","name":"神农城","location":{"longitude":113.131,"latitude":27.828,"address":"株洲市天元区","city":"株洲"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["商业","休闲","免费"]},
  {"id":"ZZH007","name":"株洲博物馆","location":{"longitude":113.131,"latitude":27.828,"address":"株洲市天元区珠江北路","city":"株洲"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"ZZH008","name":"酒仙湖","location":{"longitude":113.561,"latitude":27.185,"address":"株洲市攸县酒埠江镇","city":"株洲"},"rating":4.2,"durationMin":240,"ticketPrice":89.0,"tags":["湖泊","度假","自然"]},
  {"id":"ZZH009","name":"大京水库","location":{"longitude":113.131,"latitude":27.828,"address":"株洲市芦淞区大京","city":"株洲"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["水库","免费"]},
  {"id":"ZZH010","name":"株洲市规划展览馆","location":{"longitude":113.131,"latitude":27.828,"address":"株洲市天元区天台路","city":"株洲"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["规划","免费"]},
])

add("湘潭", [
  {"id":"XTN001","name":"韶山毛泽东故居","location":{"longitude":112.527,"latitude":27.915,"address":"湘潭市韶山市韶山乡","city":"湘潭"},"rating":4.8,"durationMin":240,"ticketPrice":0.0,"tags":["红色旅游","毛泽东","免费"]},
  {"id":"XTN002","name":"彭德怀故居","location":{"longitude":112.864,"latitude":27.667,"address":"湘潭市湘潭县乌石镇","city":"湘潭"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["红色旅游","免费"]},
  {"id":"XTN003","name":"韶山滴水洞","location":{"longitude":112.527,"latitude":27.915,"address":"湘潭市韶山市韶山","city":"湘潭"},"rating":4.3,"durationMin":120,"ticketPrice":50.0,"tags":["红色旅游","自然"]},
  {"id":"XTN004","name":"盘龙大观园","location":{"longitude":112.944,"latitude":27.831,"address":"湘潭市岳塘区","city":"湘潭"},"rating":4.2,"durationMin":240,"ticketPrice":100.0,"tags":["园林","花卉","亲子"]},
  {"id":"XTN005","name":"齐白石纪念馆","location":{"longitude":112.907,"latitude":27.858,"address":"湘潭市雨湖区大湖路2号","city":"湘潭"},"rating":4.3,"durationMin":90,"ticketPrice":0.0,"tags":["艺术","名人","免费"]},
  {"id":"XTN006","name":"湘潭博物馆","location":{"longitude":112.944,"latitude":27.831,"address":"湘潭市岳塘区湖湘东路","city":"湘潭"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"XTN007","name":"窑湾历史文化街区","location":{"longitude":112.907,"latitude":27.858,"address":"湘潭市雨湖区窑湾","city":"湘潭"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["历史街区","免费"]},
  {"id":"XTN008","name":"东山书院","location":{"longitude":112.527,"latitude":27.915,"address":"湘潭市湘乡市东山","city":"湘潭"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["历史文化","毛泽东","免费"]},
  {"id":"XTN009","name":"昭山","location":{"longitude":112.982,"latitude":27.922,"address":"湘潭市岳塘区昭山镇","city":"湘潭"},"rating":4.2,"durationMin":150,"ticketPrice":0.0,"tags":["登山","自然","免费"]},
  {"id":"XTN010","name":"湘乡水府庙","location":{"longitude":112.290,"latitude":27.674,"address":"湘潭市湘乡市棋梓镇","city":"湘潭"},"rating":4.1,"durationMin":180,"ticketPrice":60.0,"tags":["水库","度假","自然"]},
])

add("泸州", [
  {"id":"LZH010","name":"泸州老窖窖池群","location":{"longitude":105.443,"latitude":28.871,"address":"泸州市江阳区三星街","city":"泸州"},"rating":4.4,"durationMin":120,"ticketPrice":50.0,"tags":["酒文化","非遗","明代"]},
  {"id":"LZU001","name":"黄荆老林","location":{"longitude":105.605,"latitude":28.172,"address":"泸州市古蔺县黄荆乡","city":"泸州"},"rating":4.3,"durationMin":360,"ticketPrice":58.0,"tags":["原始森林","自然","瀑布"]},
  {"id":"LZU002","name":"太平古镇","location":{"longitude":106.023,"latitude":28.035,"address":"泸州市古蔺县太平镇","city":"泸州"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["古镇","红色旅游","免费"]},
  {"id":"LZU003","name":"福宝古镇","location":{"longitude":106.047,"latitude":28.715,"address":"泸州市合江县福宝镇","city":"泸州"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["古镇","明清","免费"]},
  {"id":"LZU004","name":"张坝桂圆林","location":{"longitude":105.443,"latitude":28.871,"address":"泸州市江阳区张坝","city":"泸州"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["桂圆","生态","免费"]},
  {"id":"LZU005","name":"方山","location":{"longitude":105.443,"latitude":28.871,"address":"泸州市江阳区方山镇","city":"泸州"},"rating":4.2,"durationMin":180,"ticketPrice":20.0,"tags":["佛教","登山","自然"]},
  {"id":"LZU006","name":"泸州博物馆","location":{"longitude":105.443,"latitude":28.871,"address":"泸州市江阳区","city":"泸州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"LZU007","name":"尧坝古镇","location":{"longitude":105.740,"latitude":28.717,"address":"泸州市合江县尧坝镇","city":"泸州"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["古镇","影视基地","免费"]},
  {"id":"LZU008","name":"报恩塔","location":{"longitude":105.443,"latitude":28.871,"address":"泸州市江阳区","city":"泸州"},"rating":4.1,"durationMin":30,"ticketPrice":0.0,"tags":["古建筑","宋代","免费"]},
  {"id":"LZU009","name":"凤凰湖","location":{"longitude":105.443,"latitude":28.871,"address":"泸州市纳溪区大渡口镇","city":"泸州"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["湖泊","免费"]},
])

add("日喀则", [
  {"id":"RKZ001","name":"珠穆朗玛峰大本营","location":{"longitude":86.856,"latitude":28.100,"address":"日喀则市定日县珠峰大本营","city":"日喀则"},"rating":4.9,"durationMin":600,"ticketPrice":180.0,"tags":["世界之巅","登山","自然"]},
  {"id":"RKZ002","name":"扎什伦布寺","location":{"longitude":88.881,"latitude":29.267,"address":"日喀则市桑珠孜区","city":"日喀则"},"rating":4.6,"durationMin":180,"ticketPrice":80.0,"tags":["佛教","班禅驻地","格鲁派"]},
  {"id":"RKZ003","name":"羊卓雍措","location":{"longitude":90.615,"latitude":29.004,"address":"山南市浪卡子县(日喀则出发)","city":"日喀则"},"rating":4.8,"durationMin":240,"ticketPrice":60.0,"tags":["圣湖","自然","摄影"]},
  {"id":"RKZ004","name":"江孜宗山古堡","location":{"longitude":89.606,"latitude":28.920,"address":"日喀则市江孜县宗山","city":"日喀则"},"rating":4.4,"durationMin":120,"ticketPrice":30.0,"tags":["抗英遗址","古堡","红色"]},
  {"id":"RKZ005","name":"白居寺","location":{"longitude":89.606,"latitude":28.920,"address":"日喀则市江孜县","city":"日喀则"},"rating":4.4,"durationMin":90,"ticketPrice":60.0,"tags":["佛教","十万佛塔"]},
  {"id":"RKZ006","name":"萨迦寺","location":{"longitude":88.018,"latitude":28.904,"address":"日喀则市萨迦县萨迦镇","city":"日喀则"},"rating":4.5,"durationMin":120,"ticketPrice":45.0,"tags":["佛教","萨迦派","元代"]},
  {"id":"RKZ007","name":"珠峰公路","location":{"longitude":86.856,"latitude":28.100,"address":"日喀则市定日县","city":"日喀则"},"rating":4.5,"durationMin":480,"ticketPrice":0.0,"tags":["自驾","珠峰","免费"]},
  {"id":"RKZ008","name":"绒布寺","location":{"longitude":86.856,"latitude":28.100,"address":"日喀则市定日县","city":"日喀则"},"rating":4.3,"durationMin":60,"ticketPrice":35.0,"tags":["佛教","世界最高寺庙"]},
  {"id":"RKZ009","name":"佩枯措","location":{"longitude":86.052,"latitude":28.825,"address":"日喀则市吉隆县","city":"日喀则"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["湖泊","希夏邦马峰","免费"]},
  {"id":"RKZ010","name":"日喀则博物馆","location":{"longitude":88.881,"latitude":29.267,"address":"日喀则市桑珠孜区","city":"日喀则"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
])

total = sum(len(c['attractions']) for c in data['cities'])
with open('huazai-trip-skills/src/main/resources/attractions/knowledge-base.json','w',encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print(f"\nTotal: {len(data['cities'])} cities, {total} attractions")
print("Done b27")
