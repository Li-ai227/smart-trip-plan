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

add("赣州", [
  {"id":"GZH001","name":"通天岩","location":{"longitude":114.902,"latitude":25.849,"address":"赣州市章贡区水西镇","city":"赣州"},"rating":4.3,"durationMin":180,"ticketPrice":60.0,"tags":["石窟","佛教","丹霞"]},
  {"id":"GZH002","name":"郁孤台","location":{"longitude":114.935,"latitude":25.845,"address":"赣州市章贡区西津路","city":"赣州"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["历史文化","辛弃疾","免费"]},
  {"id":"GZH003","name":"赣州古城墙","location":{"longitude":114.935,"latitude":25.845,"address":"赣州市章贡区八境路","city":"赣州"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["古城墙","宋代","免费"]},
  {"id":"GZH004","name":"八境台","location":{"longitude":114.935,"latitude":25.845,"address":"赣州市章贡区八境路","city":"赣州"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["古建筑","宋代","免费"]},
  {"id":"GZH005","name":"赣州博物馆","location":{"longitude":114.935,"latitude":25.845,"address":"赣州市章贡区兴国路","city":"赣州"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","客家文化","免费"]},
  {"id":"GZH006","name":"古浮桥","location":{"longitude":114.935,"latitude":25.845,"address":"赣州市章贡区建春门外","city":"赣州"},"rating":4.3,"durationMin":60,"ticketPrice":0.0,"tags":["古桥","宋代","免费"]},
  {"id":"GZH007","name":"通天寨","location":{"longitude":116.342,"latitude":26.316,"address":"赣州市石城县琴江镇","city":"赣州"},"rating":4.2,"durationMin":180,"ticketPrice":60.0,"tags":["丹霞","自然","登山"]},
  {"id":"GZH008","name":"瑞金共和国摇篮","location":{"longitude":116.027,"latitude":25.886,"address":"赣州市瑞金市沙洲坝镇","city":"赣州"},"rating":4.4,"durationMin":300,"ticketPrice":0.0,"tags":["红色旅游","中华苏维埃","免费"]},
  {"id":"GZH009","name":"于都长征出发地","location":{"longitude":115.414,"latitude":25.955,"address":"赣州市于都县贡江镇","city":"赣州"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["红色旅游","长征出发","免费"]},
  {"id":"GZH010","name":"三百山","location":{"longitude":115.378,"latitude":25.086,"address":"赣州市安远县三百山镇","city":"赣州"},"rating":4.2,"durationMin":240,"ticketPrice":90.0,"tags":["自然","东江源","登山"]},
])

add("东莞", [
  {"id":"DGG001","name":"可园","location":{"longitude":113.744,"latitude":23.046,"address":"东莞市莞城区可园路32号","city":"东莞"},"rating":4.3,"durationMin":120,"ticketPrice":8.0,"tags":["岭南园林","清代","四大名园"]},
  {"id":"DGG002","name":"鸦片战争博物馆","location":{"longitude":113.653,"latitude":22.816,"address":"东莞市虎门镇解放路88号","city":"东莞"},"rating":4.5,"durationMin":150,"ticketPrice":0.0,"tags":["博物馆","鸦片战争","免费"]},
  {"id":"DGG003","name":"松山湖","location":{"longitude":113.893,"latitude":22.915,"address":"东莞市松山湖高新区","city":"东莞"},"rating":4.3,"durationMin":180,"ticketPrice":0.0,"tags":["湖泊","骑行","免费"]},
  {"id":"DGG004","name":"虎门威远炮台","location":{"longitude":113.635,"latitude":22.797,"address":"东莞市虎门镇威远岛","city":"东莞"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["历史文化","鸦片战争","免费"]},
  {"id":"DGG005","name":"东莞植物园","location":{"longitude":113.752,"latitude":23.021,"address":"东莞市南城区绿色路99号","city":"东莞"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["植物","免费"]},
  {"id":"DGG006","name":"寮步香市古镇","location":{"longitude":113.875,"latitude":22.998,"address":"东莞市寮步镇","city":"东莞"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["古镇","莞香","免费"]},
  {"id":"DGG007","name":"东莞展览馆","location":{"longitude":113.752,"latitude":23.021,"address":"东莞市南城区鸿福路","city":"东莞"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["展览馆","免费"]},
  {"id":"DGG008","name":"大岭山森林公园","location":{"longitude":113.811,"latitude":22.889,"address":"东莞市大岭山镇","city":"东莞"},"rating":4.2,"durationMin":180,"ticketPrice":0.0,"tags":["森林","自然","免费"]},
  {"id":"DGG009","name":"华南MALL","location":{"longitude":113.752,"latitude":23.043,"address":"东莞市万江区万江路","city":"东莞"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["购物","娱乐","免费"]},
  {"id":"DGG010","name":"南社古村","location":{"longitude":113.917,"latitude":23.068,"address":"东莞市茶山镇南社村","city":"东莞"},"rating":4.2,"durationMin":120,"ticketPrice":30.0,"tags":["古村落","明清","岭南"]},
])

add("台州", [
  {"id":"TZH001","name":"神仙居","location":{"longitude":120.703,"latitude":28.680,"address":"台州市仙居县白塔镇","city":"台州"},"rating":4.6,"durationMin":360,"ticketPrice":110.0,"tags":["自然","栈道","云海"]},
  {"id":"TZH002","name":"天台山国清寺","location":{"longitude":121.028,"latitude":29.179,"address":"台州市天台县国清寺","city":"台州"},"rating":4.5,"durationMin":150,"ticketPrice":0.0,"tags":["佛教","天台宗","隋代","免费"]},
  {"id":"TZH003","name":"临海古城墙","location":{"longitude":121.117,"latitude":28.845,"address":"台州市临海市古城","city":"台州"},"rating":4.4,"durationMin":180,"ticketPrice":65.0,"tags":["古城墙","明代","江南长城"]},
  {"id":"TZH004","name":"大陈岛","location":{"longitude":121.910,"latitude":28.454,"address":"台州市椒江区大陈镇","city":"台州"},"rating":4.3,"durationMin":480,"ticketPrice":0.0,"tags":["海岛","红色旅游","免费"]},
  {"id":"TZH005","name":"天台石梁飞瀑","location":{"longitude":121.028,"latitude":29.179,"address":"台州市天台县石梁镇","city":"台州"},"rating":4.4,"durationMin":150,"ticketPrice":60.0,"tags":["瀑布","自然","石梁"]},
  {"id":"TZH006","name":"温岭长屿硐天","location":{"longitude":121.409,"latitude":28.310,"address":"台州市温岭市新河镇","city":"台州"},"rating":4.3,"durationMin":240,"ticketPrice":95.0,"tags":["石窟","采石遗址","自然"]},
  {"id":"TZH007","name":"紫阳古街","location":{"longitude":121.117,"latitude":28.845,"address":"台州市临海市古城紫阳街","city":"台州"},"rating":4.3,"durationMin":90,"ticketPrice":0.0,"tags":["历史街区","美食","千年","免费"]},
  {"id":"TZH008","name":"玉环大鹿岛","location":{"longitude":121.413,"latitude":28.121,"address":"台州市玉环市大鹿岛","city":"台州"},"rating":4.2,"durationMin":300,"ticketPrice":60.0,"tags":["海岛","森林","自然"]},
  {"id":"TZH009","name":"台州海洋世界","location":{"longitude":121.418,"latitude":28.656,"address":"台州市椒江区广场中路","city":"台州"},"rating":4.1,"durationMin":150,"ticketPrice":160.0,"tags":["海洋馆","亲子"]},
  {"id":"TZH010","name":"蛇蟠岛","location":{"longitude":121.592,"latitude":29.157,"address":"台州市三门县蛇蟠乡","city":"台州"},"rating":4.2,"durationMin":240,"ticketPrice":100.0,"tags":["海岛","石窟","海盗文化"]},
])

add("赤峰", [
  {"id":"CHF001","name":"克什克腾石阵","location":{"longitude":117.549,"latitude":43.258,"address":"赤峰市克什克腾旗","city":"赤峰"},"rating":4.5,"durationMin":240,"ticketPrice":140.0,"tags":["石林","世界遗产","自然"]},
  {"id":"CHF002","name":"乌兰布统草原","location":{"longitude":117.249,"latitude":42.556,"address":"赤峰市克什克腾旗乌兰布统","city":"赤峰"},"rating":4.5,"durationMin":480,"ticketPrice":120.0,"tags":["草原","影视基地","秋季"]},
  {"id":"CHF003","name":"玉龙沙湖","location":{"longitude":119.022,"latitude":42.957,"address":"赤峰市翁牛特旗乌丹镇","city":"赤峰"},"rating":4.3,"durationMin":240,"ticketPrice":80.0,"tags":["沙漠","湖泊","自然"]},
  {"id":"CHF004","name":"达里诺尔湖","location":{"longitude":116.662,"latitude":43.367,"address":"赤峰市克什克腾旗达里诺尔","city":"赤峰"},"rating":4.3,"durationMin":240,"ticketPrice":120.0,"tags":["湖泊","候鸟","草原"]},
  {"id":"CHF005","name":"红山国家森林公园","location":{"longitude":118.959,"latitude":42.287,"address":"赤峰市红山区","city":"赤峰"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["森林","红山文化","免费"]},
  {"id":"CHF006","name":"喀喇沁亲王府","location":{"longitude":118.609,"latitude":41.927,"address":"赤峰市喀喇沁旗王爷府镇","city":"赤峰"},"rating":4.2,"durationMin":120,"ticketPrice":50.0,"tags":["蒙古王府","清代","历史文化"]},
  {"id":"CHF007","name":"黄岗梁","location":{"longitude":117.549,"latitude":43.258,"address":"赤峰市克什克腾旗黄岗梁","city":"赤峰"},"rating":4.2,"durationMin":240,"ticketPrice":0.0,"tags":["森林","大兴安岭最高峰","免费"]},
  {"id":"CHF008","name":"召庙","location":{"longitude":119.380,"latitude":43.980,"address":"赤峰市巴林左旗","city":"赤峰"},"rating":4.1,"durationMin":120,"ticketPrice":50.0,"tags":["辽代石窟","佛教","历史文化"]},
  {"id":"CHF009","name":"赤峰博物馆","location":{"longitude":118.959,"latitude":42.287,"address":"赤峰市红山区新华路","city":"赤峰"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["博物馆","红山文化","免费"]},
  {"id":"CHF010","name":"阿斯哈图石林","location":{"longitude":117.549,"latitude":43.258,"address":"赤峰市克什克腾旗巴彦查干","city":"赤峰"},"rating":4.5,"durationMin":240,"ticketPrice":140.0,"tags":["石林","花岗岩","世界遗产"]},
])

add("喀什", [
  {"id":"KAS001","name":"喀什古城","location":{"longitude":75.990,"latitude":39.469,"address":"喀什市人民东路","city":"喀什"},"rating":4.7,"durationMin":240,"ticketPrice":0.0,"tags":["古城","维吾尔族","历史文化","免费"]},
  {"id":"KAS002","name":"艾提尕尔清真寺","location":{"longitude":75.990,"latitude":39.469,"address":"喀什市解放北路","city":"喀什"},"rating":4.4,"durationMin":60,"ticketPrice":45.0,"tags":["伊斯兰","中国最大清真寺"]},
  {"id":"KAS003","name":"香妃墓","location":{"longitude":75.990,"latitude":39.469,"address":"喀什市浩罕乡","city":"喀什"},"rating":4.2,"durationMin":90,"ticketPrice":30.0,"tags":["历史建筑","香妃","伊斯兰"]},
  {"id":"KAS004","name":"喀什大巴扎","location":{"longitude":75.990,"latitude":39.469,"address":"喀什市阔纳克巴扎路","city":"喀什"},"rating":4.5,"durationMin":180,"ticketPrice":0.0,"tags":["巴扎","购物","中亚风情","免费"]},
  {"id":"KAS005","name":"高台民居","location":{"longitude":75.990,"latitude":39.469,"address":"喀什市吐曼路","city":"喀什"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["民居","维吾尔","千年","免费"]},
  {"id":"KAS006","name":"喀什博物馆","location":{"longitude":75.990,"latitude":39.469,"address":"喀什市多来特巴格路","city":"喀什"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","西域文化","免费"]},
  {"id":"KAS007","name":"卡拉库里湖","location":{"longitude":75.055,"latitude":38.455,"address":"克州阿克陶县布伦口乡","city":"喀什"},"rating":4.7,"durationMin":240,"ticketPrice":50.0,"tags":["湖泊","慕士塔格","高原"]},
  {"id":"KAS008","name":"帕米尔高原","location":{"longitude":75.225,"latitude":38.155,"address":"塔什库尔干县","city":"喀什"},"rating":4.8,"durationMin":600,"ticketPrice":0.0,"tags":["高原","慕士塔格","免费"]},
  {"id":"KAS009","name":"石头城","location":{"longitude":75.225,"latitude":37.775,"address":"塔什库尔干县城北","city":"喀什"},"rating":4.3,"durationMin":90,"ticketPrice":30.0,"tags":["古城遗址","唐代","高原"]},
  {"id":"KAS010","name":"慕士塔格冰川公园","location":{"longitude":75.055,"latitude":38.282,"address":"克州阿克陶县","city":"喀什"},"rating":4.7,"durationMin":300,"ticketPrice":100.0,"tags":["冰川","雪山","登山"]},
])

add("唐山", [
  {"id":"TAS001","name":"清东陵","location":{"longitude":117.648,"latitude":40.186,"address":"唐山市遵化市马兰峪镇","city":"唐山"},"rating":4.6,"durationMin":300,"ticketPrice":162.0,"tags":["世界遗产","清代皇陵","历史文化"]},
  {"id":"TAS002","name":"唐山南湖公园","location":{"longitude":118.152,"latitude":39.617,"address":"唐山市路南区建设南路","city":"唐山"},"rating":4.3,"durationMin":180,"ticketPrice":0.0,"tags":["公园","湖泊","免费"]},
  {"id":"TAS003","name":"滦州古城","location":{"longitude":118.702,"latitude":39.743,"address":"唐山市滦州市古城街道","city":"唐山"},"rating":4.2,"durationMin":180,"ticketPrice":0.0,"tags":["古城","明清","免费"]},
  {"id":"TAS004","name":"唐山抗震纪念馆","location":{"longitude":118.180,"latitude":39.630,"address":"唐山市路南区新华东道","city":"唐山"},"rating":4.4,"durationMin":90,"ticketPrice":0.0,"tags":["抗震","纪念","免费"]},
  {"id":"TAS005","name":"李大钊故居","location":{"longitude":118.964,"latitude":39.543,"address":"唐山市乐亭县大黑坨村","city":"唐山"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["红色旅游","名人故居","免费"]},
  {"id":"TAS006","name":"唐山工业博物馆","location":{"longitude":118.180,"latitude":39.630,"address":"唐山市路北区龙泽南路","city":"唐山"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","工业","免费"]},
  {"id":"TAS007","name":"山叶口景区","location":{"longitude":118.592,"latitude":40.005,"address":"唐山市迁安市大五里乡","city":"唐山"},"rating":4.1,"durationMin":240,"ticketPrice":60.0,"tags":["峡谷","自然","漂流"]},
  {"id":"TAS008","name":"唐山湾国际旅游岛","location":{"longitude":118.767,"latitude":39.147,"address":"唐山市乐亭县渤海湾","city":"唐山"},"rating":4.2,"durationMin":480,"ticketPrice":120.0,"tags":["海岛","海滩","度假"]},
  {"id":"TAS009","name":"唐山植物园","location":{"longitude":118.152,"latitude":39.617,"address":"唐山市路南区建设南路","city":"唐山"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["植物","免费"]},
  {"id":"TAS010","name":"月坨岛","location":{"longitude":118.853,"latitude":39.140,"address":"唐山市乐亭县月坨岛","city":"唐山"},"rating":4.2,"durationMin":480,"ticketPrice":110.0,"tags":["海岛","木屋","温泉"]},
])

total = sum(len(c['attractions']) for c in data['cities'])
with open('huazai-trip-skills/src/main/resources/attractions/knowledge-base.json','w',encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print(f"\nTotal: {len(data['cities'])} cities, {total} attractions")
print("Done b22")
