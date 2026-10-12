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
        added = len(valid); skipped = len(attrs) - added
    print(f"  {city}: +{added}" + (f" skip{skipped}" if skipped else ""))

# ===== 佛山 =====
add("佛山", [
  {"id":"FSH001","name":"西樵山","location":{"longitude":112.975,"latitude":22.930,"address":"佛山市南海区西樵镇","city":"佛山"},"rating":4.4,"durationMin":240,"ticketPrice":70.0,"tags":["自然","佛教","登山"]},
  {"id":"FSH002","name":"佛山祖庙","location":{"longitude":113.121,"latitude":23.030,"address":"佛山市禅城区祖庙路21号","city":"佛山"},"rating":4.5,"durationMin":120,"ticketPrice":20.0,"tags":["历史文化","北帝","岭南建筑"]},
  {"id":"FSH003","name":"南风古灶","location":{"longitude":113.081,"latitude":23.001,"address":"佛山市禅城区石湾镇","city":"佛山"},"rating":4.3,"durationMin":90,"ticketPrice":25.0,"tags":["陶瓷","非遗","古窑"]},
  {"id":"FSH004","name":"清晖园","location":{"longitude":113.256,"latitude":22.838,"address":"佛山市顺德区大良清晖路23号","city":"佛山"},"rating":4.5,"durationMin":120,"ticketPrice":15.0,"tags":["岭南园林","清代","四大名园"]},
  {"id":"FSH005","name":"长鹿旅游休博园","location":{"longitude":113.301,"latitude":22.895,"address":"佛山市顺德区伦教三洲建设东路","city":"佛山"},"rating":4.3,"durationMin":360,"ticketPrice":160.0,"tags":["主题乐园","亲子","动物园"]},
  {"id":"FSH006","name":"南海影视城","location":{"longitude":113.018,"latitude":23.148,"address":"佛山市南海区狮山镇","city":"佛山"},"rating":4.2,"durationMin":240,"ticketPrice":90.0,"tags":["影视城","三国","亲子"]},
  {"id":"FSH007","name":"佛山梁园","location":{"longitude":113.117,"latitude":23.038,"address":"佛山市禅城区松风路","city":"佛山"},"rating":4.3,"durationMin":90,"ticketPrice":10.0,"tags":["岭南园林","清代","四大名园"]},
  {"id":"FSH008","name":"千灯湖","location":{"longitude":113.148,"latitude":23.062,"address":"佛山市南海区桂城","city":"佛山"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["湖泊","夜景","免费"]},
  {"id":"FSH009","name":"佛山博物馆","location":{"longitude":113.121,"latitude":23.030,"address":"佛山市禅城区祖庙路","city":"佛山"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"FSH010","name":"顺德陈村花卉世界","location":{"longitude":113.226,"latitude":22.956,"address":"佛山市顺德区陈村镇","city":"佛山"},"rating":4.2,"durationMin":180,"ticketPrice":0.0,"tags":["花卉","园艺","免费"]},
  {"id":"FSH011","name":"逢简水乡","location":{"longitude":113.185,"latitude":22.801,"address":"佛山市顺德区杏坛镇逢简村","city":"佛山"},"rating":4.3,"durationMin":150,"ticketPrice":0.0,"tags":["水乡","古村落","免费"]},
  {"id":"FSH012","name":"岭南天地","location":{"longitude":113.121,"latitude":23.030,"address":"佛山市禅城区祖庙东华里","city":"佛山"},"rating":4.4,"durationMin":120,"ticketPrice":0.0,"tags":["历史街区","文创","美食","免费"]},
  {"id":"FSH013","name":"三水荷花世界","location":{"longitude":112.893,"latitude":23.183,"address":"佛山市三水区西南街道","city":"佛山"},"rating":4.2,"durationMin":180,"ticketPrice":70.0,"tags":["荷花","自然","夏季"]},
  {"id":"FSH014","name":"皂幕山","location":{"longitude":112.713,"latitude":22.792,"address":"佛山市高明区杨和镇","city":"佛山"},"rating":4.1,"durationMin":240,"ticketPrice":30.0,"tags":["登山","自然","佛山第一峰"]},
  {"id":"FSH015","name":"顺德华侨城欢乐海岸","location":{"longitude":113.284,"latitude":22.811,"address":"佛山市顺德区大良街道","city":"佛山"},"rating":4.4,"durationMin":360,"ticketPrice":160.0,"tags":["主题乐园","购物","美食"]},
  {"id":"FSH016","name":"九江双蒸博物馆","location":{"longitude":112.979,"latitude":22.837,"address":"佛山市南海区九江镇","city":"佛山"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["博物馆","米酒","非遗","免费"]},
  {"id":"FSH017","name":"叶问纪念馆","location":{"longitude":113.104,"latitude":23.055,"address":"佛山市南海区罗村联星村","city":"佛山"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["名人","武术","免费"]},
  {"id":"FSH018","name":"黄飞鸿纪念馆","location":{"longitude":113.121,"latitude":23.030,"address":"佛山市禅城区祖庙内","city":"佛山"},"rating":4.3,"durationMin":60,"ticketPrice":20.0,"tags":["名人","武术","醒狮"]},
  {"id":"FSH019","name":"佛山图书馆","location":{"longitude":113.133,"latitude":22.962,"address":"佛山市顺德区乐从镇佛山新城","city":"佛山"},"rating":4.3,"durationMin":60,"ticketPrice":0.0,"tags":["图书馆","现代建筑","免费"]},
  {"id":"FSH020","name":"南国桃园","location":{"longitude":113.043,"latitude":23.127,"address":"佛山市南海区狮山镇","city":"佛山"},"rating":4.1,"durationMin":150,"ticketPrice":0.0,"tags":["桃花","度假","免费"]},
  {"id":"FSH021","name":"佛山科学技术馆","location":{"longitude":113.133,"latitude":22.962,"address":"佛山市顺德区乐从镇佛山新城","city":"佛山"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["科普","亲子","免费"]},
  {"id":"FSH022","name":"三水森林公园","location":{"longitude":112.893,"latitude":23.183,"address":"佛山市三水区西南街道","city":"佛山"},"rating":4.1,"durationMin":180,"ticketPrice":0.0,"tags":["森林","自然","免费"]},
  {"id":"FSH023","name":"大旗头古村","location":{"longitude":112.921,"latitude":23.263,"address":"佛山市三水区乐平镇大旗头村","city":"佛山"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["古村落","锅耳屋","清代","免费"]},
  {"id":"FSH024","name":"顺德清晖园博物馆","location":{"longitude":113.256,"latitude":22.838,"address":"佛山市顺德区大良","city":"佛山"},"rating":4.4,"durationMin":90,"ticketPrice":15.0,"tags":["博物馆","岭南园林"]},
  {"id":"FSH025","name":"岭南和园","location":{"longitude":113.211,"latitude":22.860,"address":"佛山市顺德区北滘镇","city":"佛山"},"rating":4.3,"durationMin":120,"ticketPrice":30.0,"tags":["岭南园林","现代建筑"]},
  {"id":"FSH026","name":"佛山电视塔","location":{"longitude":113.124,"latitude":23.018,"address":"佛山市禅城区季华路","city":"佛山"},"rating":4.0,"durationMin":30,"ticketPrice":0.0,"tags":["地标","免费"]},
  {"id":"FSH027","name":"高明盈香生态园","location":{"longitude":112.713,"latitude":22.792,"address":"佛山市高明区荷城街道","city":"佛山"},"rating":4.2,"durationMin":240,"ticketPrice":80.0,"tags":["生态","亲子","农业"]},
  {"id":"FSH028","name":"亚洲艺术公园","location":{"longitude":113.123,"latitude":23.015,"address":"佛山市禅城区文华路","city":"佛山"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["公园","艺术","免费"]},
  {"id":"FSH029","name":"石湾公仔街","location":{"longitude":113.081,"latitude":23.001,"address":"佛山市禅城区石湾镇","city":"佛山"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["陶瓷","购物","免费"]},
  {"id":"FSH030","name":"顺峰山公园","location":{"longitude":113.283,"latitude":22.828,"address":"佛山市顺德区大良南国东路","city":"佛山"},"rating":4.3,"durationMin":150,"ticketPrice":0.0,"tags":["公园","牌坊","免费"]},
])

# ===== 柳州 =====
add("柳州", [
  {"id":"LZH001","name":"柳江百里画廊","location":{"longitude":109.413,"latitude":24.315,"address":"柳州市城中区柳江沿岸","city":"柳州"},"rating":4.4,"durationMin":120,"ticketPrice":0.0,"tags":["柳江","夜景","免费"]},
  {"id":"LZH002","name":"龙潭公园","location":{"longitude":109.429,"latitude":24.288,"address":"柳州市鱼峰区龙潭路43号","city":"柳州"},"rating":4.5,"durationMin":150,"ticketPrice":0.0,"tags":["公园","喀斯特","免费"]},
  {"id":"LZH003","name":"程阳八寨","location":{"longitude":109.638,"latitude":25.891,"address":"柳州市三江县林溪镇程阳村","city":"柳州"},"rating":4.4,"durationMin":300,"ticketPrice":80.0,"tags":["侗族","风雨桥","古村落"]},
  {"id":"LZH004","name":"柳州工业博物馆","location":{"longitude":109.421,"latitude":24.318,"address":"柳州市鱼峰区文昌路","city":"柳州"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["博物馆","工业","免费"]},
  {"id":"LZH005","name":"马鞍山公园","location":{"longitude":109.415,"latitude":24.313,"address":"柳州市鱼峰区屏山大道","city":"柳州"},"rating":4.3,"durationMin":90,"ticketPrice":0.0,"tags":["登山","城市全景","免费"]},
  {"id":"LZH006","name":"柳州博物馆","location":{"longitude":109.413,"latitude":24.315,"address":"柳州市城中区解放北路37号","city":"柳州"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"LZH007","name":"都乐岩","location":{"longitude":109.343,"latitude":24.227,"address":"柳州市柳南区都乐村","city":"柳州"},"rating":4.2,"durationMin":150,"ticketPrice":60.0,"tags":["溶洞","自然"]},
  {"id":"LZH008","name":"立鱼峰风景区","location":{"longitude":109.413,"latitude":24.310,"address":"柳州市鱼峰区鱼峰路","city":"柳州"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["登山","刘三姐","免费"]},
  {"id":"LZH009","name":"三江风雨桥","location":{"longitude":109.600,"latitude":25.780,"address":"柳州市三江县古宜镇","city":"柳州"},"rating":4.4,"durationMin":90,"ticketPrice":0.0,"tags":["侗族","风雨桥","世界遗产预备","免费"]},
  {"id":"LZH010","name":"螺蛳粉小镇","location":{"longitude":109.380,"latitude":24.340,"address":"柳州市柳南区太阳村镇","city":"柳州"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["美食","螺蛳粉","免费"]},
  {"id":"LZH011","name":"柳侯祠","location":{"longitude":109.416,"latitude":24.317,"address":"柳州市城中区文惠路60号","city":"柳州"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["历史文化","柳宗元","免费"]},
  {"id":"LZH012","name":"鹿寨中渡古镇","location":{"longitude":109.750,"latitude":24.500,"address":"柳州市鹿寨县中渡镇","city":"柳州"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["古镇","免费"]},
  {"id":"LZH013","name":"融水双龙沟","location":{"longitude":109.224,"latitude":25.066,"address":"柳州市融水县双龙沟","city":"柳州"},"rating":4.3,"durationMin":240,"ticketPrice":80.0,"tags":["森林","瀑布","苗族"]},
  {"id":"LZH014","name":"石门仙湖","location":{"longitude":109.380,"latitude":24.340,"address":"柳州市融安县大良镇","city":"柳州"},"rating":4.2,"durationMin":180,"ticketPrice":128.0,"tags":["湖泊","玻璃栈道","自然"]},
  {"id":"LZH015","name":"柳州文庙","location":{"longitude":109.420,"latitude":24.308,"address":"柳州市鱼峰区夫子路","city":"柳州"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["儒家","古建筑","免费"]},
  {"id":"LZH016","name":"雀儿山公园","location":{"longitude":109.397,"latitude":24.345,"address":"柳州市柳北区雀儿山路","city":"柳州"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"LZH017","name":"柳州奇石馆","location":{"longitude":109.421,"latitude":24.318,"address":"柳州市鱼峰区文昌路","city":"柳州"},"rating":4.2,"durationMin":60,"ticketPrice":50.0,"tags":["奇石","博物馆"]},
  {"id":"LZH018","name":"柳州夜市（青云路）","location":{"longitude":109.413,"latitude":24.315,"address":"柳州市城中区青云路","city":"柳州"},"rating":4.4,"durationMin":90,"ticketPrice":0.0,"tags":["夜市","美食","螺蛳粉","免费"]},
  {"id":"LZH019","name":"三门江森林公园","location":{"longitude":109.480,"latitude":24.360,"address":"柳州市城中区三门江","city":"柳州"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["森林","免费"]},
  {"id":"LZH020","name":"柳州水上公交","location":{"longitude":109.413,"latitude":24.315,"address":"柳州市柳江沿岸","city":"柳州"},"rating":4.2,"durationMin":60,"ticketPrice":3.0,"tags":["水上巴士","柳江","体验"]},
])

# ===== 承德 =====
add("承德", [
  {"id":"CDE001","name":"避暑山庄","location":{"longitude":117.941,"latitude":40.988,"address":"承德市双桥区避暑山庄","city":"承德"},"rating":4.7,"durationMin":360,"ticketPrice":130.0,"tags":["世界遗产","清代","皇家园林"]},
  {"id":"CDE002","name":"普宁寺","location":{"longitude":117.951,"latitude":41.007,"address":"承德市双桥区普宁路1号","city":"承德"},"rating":4.4,"durationMin":120,"ticketPrice":80.0,"tags":["佛教","世界遗产","大佛"]},
  {"id":"CDE003","name":"普陀宗乘之庙","location":{"longitude":117.935,"latitude":41.003,"address":"承德市双桥区狮子沟镇","city":"承德"},"rating":4.5,"durationMin":150,"ticketPrice":80.0,"tags":["佛教","小布达拉宫","世界遗产"]},
  {"id":"CDE004","name":"磬棰峰","location":{"longitude":117.960,"latitude":40.990,"address":"承德市双桥区磬棰峰","city":"承德"},"rating":4.3,"durationMin":180,"ticketPrice":50.0,"tags":["自然","奇石","登山"]},
  {"id":"CDE005","name":"须弥福寿之庙","location":{"longitude":117.932,"latitude":41.001,"address":"承德市双桥区","city":"承德"},"rating":4.3,"durationMin":90,"ticketPrice":80.0,"tags":["佛教","班禅行宫","世界遗产"]},
  {"id":"CDE006","name":"普乐寺","location":{"longitude":117.957,"latitude":40.998,"address":"承德市双桥区","city":"承德"},"rating":4.2,"durationMin":90,"ticketPrice":80.0,"tags":["佛教","世界遗产"]},
  {"id":"CDE007","name":"安远庙","location":{"longitude":117.947,"latitude":41.000,"address":"承德市双桥区","city":"承德"},"rating":4.1,"durationMin":60,"ticketPrice":10.0,"tags":["佛教","世界遗产"]},
  {"id":"CDE008","name":"塞罕坝森林公园","location":{"longitude":117.271,"latitude":42.424,"address":"承德市围场县塞罕坝","city":"承德"},"rating":4.7,"durationMin":600,"ticketPrice":130.0,"tags":["森林","自然","草原"]},
  {"id":"CDE009","name":"木兰围场","location":{"longitude":117.432,"latitude":42.214,"address":"承德市围场县","city":"承德"},"rating":4.5,"durationMin":600,"ticketPrice":150.0,"tags":["草原","皇家猎场","自然"]},
  {"id":"CDE010","name":"丰宁坝上草原","location":{"longitude":116.240,"latitude":41.355,"address":"承德市丰宁满族自治县大滩镇","city":"承德"},"rating":4.4,"durationMin":600,"ticketPrice":80.0,"tags":["草原","骑马","自然"]},
  {"id":"CDE011","name":"金山岭长城","location":{"longitude":117.252,"latitude":40.673,"address":"承德市滦平县巴克什营镇","city":"承德"},"rating":4.7,"durationMin":300,"ticketPrice":65.0,"tags":["长城","世界遗产","摄影"]},
  {"id":"CDE012","name":"双塔山","location":{"longitude":117.875,"latitude":40.997,"address":"承德市双滦区双塔山镇","city":"承德"},"rating":4.2,"durationMin":120,"ticketPrice":50.0,"tags":["自然","奇观","辽代"]},
  {"id":"CDE013","name":"承德博物馆","location":{"longitude":117.943,"latitude":40.985,"address":"承德市双桥区","city":"承德"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"CDE014","name":"热河文庙","location":{"longitude":117.942,"latitude":40.988,"address":"承德市双桥区西大街","city":"承德"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["儒家","免费"]},
  {"id":"CDE015","name":"御道口草原","location":{"longitude":117.048,"latitude":42.271,"address":"承德市围场县御道口","city":"承德"},"rating":4.4,"durationMin":480,"ticketPrice":60.0,"tags":["草原","自然","森林"]},
  {"id":"CDE016","name":"雾灵山","location":{"longitude":117.484,"latitude":40.603,"address":"承德市兴隆县雾灵山","city":"承德"},"rating":4.3,"durationMin":300,"ticketPrice":80.0,"tags":["登山","自然","燕山主峰"]},
  {"id":"CDE017","name":"京北第一草原","location":{"longitude":116.240,"latitude":41.355,"address":"承德市丰宁县大滩镇","city":"承德"},"rating":4.3,"durationMin":480,"ticketPrice":0.0,"tags":["草原","自然","免费"]},
  {"id":"CDE018","name":"僧冠峰","location":{"longitude":117.930,"latitude":40.980,"address":"承德市双桥区","city":"承德"},"rating":4.1,"durationMin":90,"ticketPrice":30.0,"tags":["登山","佛教"]},
  {"id":"CDE019","name":"夹墙沟","location":{"longitude":117.910,"latitude":40.980,"address":"承德市双桥区","city":"承德"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["自然","免费"]},
  {"id":"CDE020","name":"魁星楼","location":{"longitude":117.945,"latitude":40.983,"address":"承德市双桥区半壁山","city":"承德"},"rating":4.1,"durationMin":60,"ticketPrice":20.0,"tags":["道教","古建筑"]},
  {"id":"CDE021","name":"兴隆溶洞","location":{"longitude":117.484,"latitude":40.418,"address":"承德市兴隆县北水泉乡","city":"承德"},"rating":4.2,"durationMin":120,"ticketPrice":130.0,"tags":["溶洞","自然"]},
  {"id":"CDE022","name":"六里坪森林公园","location":{"longitude":117.484,"latitude":40.603,"address":"承德市兴隆县","city":"承德"},"rating":4.1,"durationMin":240,"ticketPrice":50.0,"tags":["森林","自然"]},
  {"id":"CDE023","name":"唐家湾风景区","location":{"longitude":117.941,"latitude":40.988,"address":"承德市双桥区","city":"承德"},"rating":4.1,"durationMin":120,"ticketPrice":40.0,"tags":["水库","休闲"]},
  {"id":"CDE024","name":"承德冰雪大世界","location":{"longitude":117.941,"latitude":40.988,"address":"承德市双桥区","city":"承德"},"rating":4.0,"durationMin":120,"ticketPrice":100.0,"tags":["冰雪","冬季"]},
  {"id":"CDE025","name":"承德鼎盛王朝康熙大典","location":{"longitude":117.930,"latitude":40.986,"address":"承德市双滦区元宝山","city":"承德"},"rating":4.6,"durationMin":90,"ticketPrice":198.0,"tags":["实景演出","康熙","清代"]},
])

# ===== 日照 =====
add("日照", [
  {"id":"RZH001","name":"万平口海滨风景区","location":{"longitude":119.561,"latitude":35.404,"address":"日照市东港区海曲东路","city":"日照"},"rating":4.4,"durationMin":240,"ticketPrice":0.0,"tags":["海滩","免费"]},
  {"id":"RZH002","name":"日照海滨国家森林公园","location":{"longitude":119.601,"latitude":35.479,"address":"日照市东港区碧海路北首","city":"日照"},"rating":4.3,"durationMin":300,"ticketPrice":60.0,"tags":["森林","海滩","自然"]},
  {"id":"RZH003","name":"五莲山风景区","location":{"longitude":119.287,"latitude":35.712,"address":"日照市五莲县五莲山","city":"日照"},"rating":4.3,"durationMin":240,"ticketPrice":65.0,"tags":["登山","佛教","自然"]},
  {"id":"RZH004","name":"灯塔风景区","location":{"longitude":119.568,"latitude":35.395,"address":"日照市东港区黄海一路","city":"日照"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["灯塔","海滩","免费"]},
  {"id":"RZH005","name":"刘家湾赶海园","location":{"longitude":119.648,"latitude":35.272,"address":"日照市东港区涛雒镇刘家湾","city":"日照"},"rating":4.2,"durationMin":180,"ticketPrice":50.0,"tags":["赶海","亲子","体验"]},
  {"id":"RZH006","name":"九仙山风景区","location":{"longitude":119.287,"latitude":35.712,"address":"日照市五莲县松柏镇","city":"日照"},"rating":4.2,"durationMin":240,"ticketPrice":65.0,"tags":["登山","自然","孙膑"]},
  {"id":"RZH007","name":"日照海洋公园","location":{"longitude":119.561,"latitude":35.404,"address":"日照市东港区碧海路","city":"日照"},"rating":4.3,"durationMin":180,"ticketPrice":180.0,"tags":["海洋馆","亲子"]},
  {"id":"RZH008","name":"日照世帆赛基地","location":{"longitude":119.565,"latitude":35.400,"address":"日照市东港区绿舟南路","city":"日照"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["帆船","运动","免费"]},
  {"id":"RZH009","name":"浮来山","location":{"longitude":118.830,"latitude":35.600,"address":"日照市莒县浮来山","city":"日照"},"rating":4.1,"durationMin":120,"ticketPrice":50.0,"tags":["银杏","古树","佛教"]},
  {"id":"RZH010","name":"日照植物园","location":{"longitude":119.555,"latitude":35.414,"address":"日照市东港区海曲东路","city":"日照"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["植物","免费"]},
  {"id":"RZH011","name":"桃花岛","location":{"longitude":119.607,"latitude":35.488,"address":"日照市东港区桃花岛","city":"日照"},"rating":4.1,"durationMin":120,"ticketPrice":60.0,"tags":["海岛","自然"]},
  {"id":"RZH012","name":"银河公园","location":{"longitude":119.530,"latitude":35.420,"address":"日照市东港区烟台路","city":"日照"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"RZH013","name":"日照博物馆","location":{"longitude":119.528,"latitude":35.417,"address":"日照市东港区烟台路","city":"日照"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"RZH014","name":"竹洞天风景区","location":{"longitude":119.406,"latitude":35.378,"address":"日照市东港区海曲西路西端","city":"日照"},"rating":4.1,"durationMin":120,"ticketPrice":60.0,"tags":["竹林","自然"]},
  {"id":"RZH015","name":"花仙子风景区","location":{"longitude":119.370,"latitude":35.350,"address":"日照市东港区西湖镇","city":"日照"},"rating":4.1,"durationMin":120,"ticketPrice":40.0,"tags":["花海","摄影","春季"]},
  {"id":"RZH016","name":"日照游泳馆","location":{"longitude":119.561,"latitude":35.404,"address":"日照市东港区","city":"日照"},"rating":4.0,"durationMin":120,"ticketPrice":60.0,"tags":["水上运动"]},
  {"id":"RZH017","name":"太阳广场","location":{"longitude":119.565,"latitude":35.400,"address":"日照市东港区青岛路","city":"日照"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["广场","免费"]},
  {"id":"RZH018","name":"日照图书馆","location":{"longitude":119.530,"latitude":35.420,"address":"日照市东港区","city":"日照"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["图书馆","免费"]},
  {"id":"RZH019","name":"白鹭湾艺游小镇","location":{"longitude":119.380,"latitude":35.620,"address":"日照市五莲县潮河镇","city":"日照"},"rating":4.2,"durationMin":120,"ticketPrice":40.0,"tags":["艺术","小镇","生态"]},
  {"id":"RZH020","name":"山海天旅游度假区","location":{"longitude":119.580,"latitude":35.450,"address":"日照市东港区山海天路","city":"日照"},"rating":4.2,"durationMin":240,"ticketPrice":0.0,"tags":["海滩","度假","免费"]},
])

total = sum(len(c['attractions']) for c in data['cities'])
with open('huazai-trip-skills/src/main/resources/attractions/knowledge-base.json','w',encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print(f"\nTotal: {len(data['cities'])} cities, {total} attractions")
print("Done b18")
