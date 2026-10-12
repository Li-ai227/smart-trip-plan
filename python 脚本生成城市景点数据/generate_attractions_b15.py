import json

with open('huazai-trip-skills/src/main/resources/attractions/knowledge-base.json','r',encoding='utf-8') as f:
    data = json.load(f)

existing_ids = set()
for c in data['cities']:
    for a in c['attractions']:
        existing_ids.add(a['id'])

print(f"已有景点ID: {len(existing_ids)}")

def add(city_name, attractions):
    added = 0
    skipped = 0
    found = False
    for c in data['cities']:
        if c['city'] == city_name:
            found = True
            for a in attractions:
                if a['id'] in existing_ids:
                    skipped += 1
                else:
                    c['attractions'].append(a)
                    existing_ids.add(a['id'])
                    added += 1
            break
    if not found:
        valid = [a for a in attractions if a['id'] not in existing_ids]
        skipped = len(attractions) - len(valid)
        for a in valid:
            existing_ids.add(a['id'])
        data['cities'].append({"city": city_name, "attractions": valid})
        added = len(valid)
    print(f"  {city_name}: +{added}" + (f" (跳过{skipped}重复)" if skipped else ""))

# ===== 秦皇岛 =====
add("秦皇岛", [
  {"id":"QHD001","name":"山海关风景区","location":{"longitude":119.762,"latitude":40.009,"address":"秦皇岛市山海关区","city":"秦皇岛"},"rating":4.5,"durationMin":240,"ticketPrice":50.0,"tags":["长城","历史文化","天下第一关"]},
  {"id":"QHD002","name":"北戴河海滨","location":{"longitude":119.496,"latitude":39.820,"address":"秦皇岛市北戴河区海滨","city":"秦皇岛"},"rating":4.5,"durationMin":300,"ticketPrice":0.0,"tags":["海滩","避暑","自然","免费"]},
  {"id":"QHD003","name":"老龙头","location":{"longitude":119.799,"latitude":39.968,"address":"秦皇岛市山海关区老龙头","city":"秦皇岛"},"rating":4.4,"durationMin":120,"ticketPrice":50.0,"tags":["长城","大海交汇","历史文化"]},
  {"id":"QHD004","name":"鸽子窝公园","location":{"longitude":119.525,"latitude":39.850,"address":"秦皇岛市北戴河区鸽赤路","city":"秦皇岛"},"rating":4.3,"durationMin":120,"ticketPrice":25.0,"tags":["海滩","日出","观鸟"]},
  {"id":"QHD005","name":"秦皇岛野生动物园","location":{"longitude":119.522,"latitude":39.866,"address":"秦皇岛市北戴河区滨海大道","city":"秦皇岛"},"rating":4.2,"durationMin":240,"ticketPrice":100.0,"tags":["动物园","亲子","野生动物"]},
  {"id":"QHD006","name":"南戴河国际娱乐中心","location":{"longitude":119.403,"latitude":39.784,"address":"秦皇岛市抚宁区南戴河","city":"秦皇岛"},"rating":4.1,"durationMin":360,"ticketPrice":120.0,"tags":["海滩","游乐","度假"]},
  {"id":"QHD007","name":"秦皇求仙入海处","location":{"longitude":119.613,"latitude":39.922,"address":"秦皇岛市海港区南山街","city":"秦皇岛"},"rating":4.1,"durationMin":90,"ticketPrice":30.0,"tags":["历史文化","秦始皇","传说"]},
  {"id":"QHD008","name":"角山长城","location":{"longitude":119.740,"latitude":40.035,"address":"秦皇岛市山海关区角山路","city":"秦皇岛"},"rating":4.3,"durationMin":180,"ticketPrice":30.0,"tags":["长城","登山","自然"]},
  {"id":"QHD009","name":"孟姜女庙","location":{"longitude":119.755,"latitude":40.020,"address":"秦皇岛市山海关区孟姜女路","city":"秦皇岛"},"rating":4.1,"durationMin":60,"ticketPrice":25.0,"tags":["历史文化","传说","长城"]},
  {"id":"QHD010","name":"碧螺塔海上酒吧公园","location":{"longitude":119.528,"latitude":39.858,"address":"秦皇岛市北戴河区东海滩路","city":"秦皇岛"},"rating":4.2,"durationMin":180,"ticketPrice":30.0,"tags":["海滩","酒吧","夜景"]},
  {"id":"QHD011","name":"老虎石海上公园","location":{"longitude":119.488,"latitude":39.822,"address":"秦皇岛市北戴河区中海滩路","city":"秦皇岛"},"rating":4.2,"durationMin":120,"ticketPrice":8.0,"tags":["海滩","游泳","夏季"]},
  {"id":"QHD012","name":"联峰山公园","location":{"longitude":119.505,"latitude":39.836,"address":"秦皇岛市北戴河区联峰路","city":"秦皇岛"},"rating":4.2,"durationMin":150,"ticketPrice":20.0,"tags":["登山","自然","观海"]},
  {"id":"QHD013","name":"秦皇岛博物馆","location":{"longitude":119.602,"latitude":39.938,"address":"秦皇岛市海港区文化路","city":"秦皇岛"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","历史文化","免费"]},
  {"id":"QHD014","name":"怪楼奇园","location":{"longitude":119.519,"latitude":39.845,"address":"秦皇岛市北戴河区","city":"秦皇岛"},"rating":4.0,"durationMin":90,"ticketPrice":20.0,"tags":["奇观","建筑","小众"]},
  {"id":"QHD015","name":"祖山风景区","location":{"longitude":119.434,"latitude":40.144,"address":"秦皇岛市青龙县祖山镇","city":"秦皇岛"},"rating":4.3,"durationMin":300,"ticketPrice":80.0,"tags":["自然","登山","花岗岩地貌"]},
  {"id":"QHD016","name":"燕塞湖","location":{"longitude":119.750,"latitude":40.020,"address":"秦皇岛市山海关区燕塞湖","city":"秦皇岛"},"rating":4.2,"durationMin":120,"ticketPrice":40.0,"tags":["湖泊","自然","峡谷"]},
  {"id":"QHD017","name":"秦皇岛植物园","location":{"longitude":119.543,"latitude":39.865,"address":"秦皇岛市北戴河区","city":"秦皇岛"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["植物","免费"]},
  {"id":"QHD018","name":"长城博物馆","location":{"longitude":119.762,"latitude":40.009,"address":"秦皇岛市山海关区","city":"秦皇岛"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","长城文化","免费"]},
  {"id":"QHD019","name":"黄金海岸","location":{"longitude":119.340,"latitude":39.669,"address":"秦皇岛市昌黎县黄金海岸","city":"秦皇岛"},"rating":4.4,"durationMin":300,"ticketPrice":0.0,"tags":["海滩","沙丘","自然","免费"]},
  {"id":"QHD020","name":"葡萄沟","location":{"longitude":119.284,"latitude":39.728,"address":"秦皇岛市昌黎县葡萄沟","city":"秦皇岛"},"rating":4.2,"durationMin":120,"ticketPrice":30.0,"tags":["采摘","农业","葡萄"]},
  {"id":"QHD021","name":"沙雕海洋乐园","location":{"longitude":119.347,"latitude":39.672,"address":"秦皇岛市昌黎县黄金海岸","city":"秦皇岛"},"rating":4.2,"durationMin":240,"ticketPrice":135.0,"tags":["海滩","沙雕","亲子"]},
  {"id":"QHD022","name":"渔岛海洋温泉","location":{"longitude":119.298,"latitude":39.620,"address":"秦皇岛市昌黎县","city":"秦皇岛"},"rating":4.1,"durationMin":300,"ticketPrice":188.0,"tags":["温泉","海滩","度假"]},
  {"id":"QHD023","name":"秦皇岛港","location":{"longitude":119.613,"latitude":39.921,"address":"秦皇岛市海港区","city":"秦皇岛"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["港口","工业","免费"]},
  {"id":"QHD024","name":"奥林匹克公园","location":{"longitude":119.526,"latitude":39.857,"address":"秦皇岛市北戴河区滨海大道","city":"秦皇岛"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["公园","体育","免费"]},
  {"id":"QHD025","name":"浅水湾浴场","location":{"longitude":119.539,"latitude":39.870,"address":"秦皇岛市北戴河区","city":"秦皇岛"},"rating":4.1,"durationMin":180,"ticketPrice":0.0,"tags":["海滩","游泳","免费"]},
  {"id":"QHD026","name":"秦皇岛森林体育公园","location":{"longitude":119.578,"latitude":39.946,"address":"秦皇岛市海港区","city":"秦皇岛"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["公园","森林","免费"]},
  {"id":"QHD027","name":"海底世界","location":{"longitude":119.497,"latitude":39.820,"address":"秦皇岛市北戴河区","city":"秦皇岛"},"rating":4.1,"durationMin":120,"ticketPrice":100.0,"tags":["海洋馆","亲子"]},
  {"id":"QHD028","name":"仙螺岛","location":{"longitude":119.428,"latitude":39.791,"address":"秦皇岛市抚宁区南戴河","city":"秦皇岛"},"rating":4.1,"durationMin":120,"ticketPrice":45.0,"tags":["海岛","索道","游乐"]},
  {"id":"QHD029","name":"戴河生态园","location":{"longitude":119.478,"latitude":39.814,"address":"秦皇岛市北戴河区","city":"秦皇岛"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["生态","公园","免费"]},
  {"id":"QHD030","name":"碣石山","location":{"longitude":119.199,"latitude":39.710,"address":"秦皇岛市昌黎县碣石山","city":"秦皇岛"},"rating":4.2,"durationMin":180,"ticketPrice":40.0,"tags":["登山","历史文化","曹操"]},
  {"id":"QHD031","name":"五峰山","location":{"longitude":119.218,"latitude":39.722,"address":"秦皇岛市昌黎县五峰山","city":"秦皇岛"},"rating":4.1,"durationMin":150,"ticketPrice":30.0,"tags":["登山","李大钊","红色"]},
  {"id":"QHD032","name":"天马山","location":{"longitude":119.098,"latitude":39.893,"address":"秦皇岛市抚宁区天马山","city":"秦皇岛"},"rating":4.1,"durationMin":120,"ticketPrice":30.0,"tags":["登山","道教","自然"]},
  {"id":"QHD033","name":"秦行宫遗址","location":{"longitude":119.523,"latitude":39.845,"address":"秦皇岛市北戴河区","city":"秦皇岛"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["遗址","秦代","免费"]},
  {"id":"QHD034","name":"秦皇植物园","location":{"longitude":119.562,"latitude":39.949,"address":"秦皇岛市海港区","city":"秦皇岛"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["植物","免费"]},
  {"id":"QHD035","name":"圆明山旅游区","location":{"longitude":119.640,"latitude":39.955,"address":"秦皇岛市海港区","city":"秦皇岛"},"rating":4.0,"durationMin":120,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"QHD036","name":"紫云山滑雪场","location":{"longitude":119.618,"latitude":39.990,"address":"秦皇岛市海港区","city":"秦皇岛"},"rating":4.0,"durationMin":240,"ticketPrice":120.0,"tags":["滑雪","冬季"]},
  {"id":"QHD037","name":"北戴河艺术村","location":{"longitude":119.471,"latitude":39.836,"address":"秦皇岛市北戴河区","city":"秦皇岛"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["文创","民宿","免费"]},
  {"id":"QHD038","name":"秦皇岛图书馆","location":{"longitude":119.601,"latitude":39.938,"address":"秦皇岛市海港区","city":"秦皇岛"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["图书馆","免费"]},
  {"id":"QHD039","name":"秦皇求仙栈桥","location":{"longitude":119.616,"latitude":39.915,"address":"秦皇岛市海港区","city":"秦皇岛"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["栈桥","海景","免费"]},
  {"id":"QHD040","name":"金海湾森林公园","location":{"longitude":119.565,"latitude":39.903,"address":"秦皇岛市海港区","city":"秦皇岛"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["森林","公园","免费"]},
  {"id":"QHD041","name":"洋河水库","location":{"longitude":119.350,"latitude":39.790,"address":"秦皇岛市抚宁区","city":"秦皇岛"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["水库","自然","免费"]},
  {"id":"QHD042","name":"天女小镇","location":{"longitude":119.435,"latitude":40.145,"address":"秦皇岛市青龙县祖山镇","city":"秦皇岛"},"rating":4.1,"durationMin":180,"ticketPrice":60.0,"tags":["特色小镇","自然","户外"]},
  {"id":"QHD043","name":"冰塘峪长城风情大峡谷","location":{"longitude":119.310,"latitude":40.080,"address":"秦皇岛市抚宁区大新寨镇","city":"秦皇岛"},"rating":4.3,"durationMin":240,"ticketPrice":80.0,"tags":["峡谷","长城","自然"]},
  {"id":"QHD044","name":"乱刀峪","location":{"longitude":119.480,"latitude":40.050,"address":"秦皇岛市抚宁区","city":"秦皇岛"},"rating":4.1,"durationMin":180,"ticketPrice":50.0,"tags":["自然","奇石","登山"]},
  {"id":"QHD045","name":"董家口长城","location":{"longitude":119.680,"latitude":40.100,"address":"秦皇岛市抚宁区董家口","city":"秦皇岛"},"rating":4.2,"durationMin":180,"ticketPrice":30.0,"tags":["长城","古战场","自然"]},
  {"id":"QHD046","name":"板厂峪长城","location":{"longitude":119.650,"latitude":40.080,"address":"秦皇岛市抚宁区板厂峪","city":"秦皇岛"},"rating":4.2,"durationMin":240,"ticketPrice":50.0,"tags":["长城","自然","徒步"]},
  {"id":"QHD047","name":"柳江国家地质公园","location":{"longitude":119.564,"latitude":40.022,"address":"秦皇岛市海港区石门寨镇","city":"秦皇岛"},"rating":4.2,"durationMin":180,"ticketPrice":60.0,"tags":["地质","自然","化石"]},
  {"id":"QHD048","name":"鸽子窝观鸟湿地","location":{"longitude":119.525,"latitude":39.850,"address":"秦皇岛市北戴河区鸽赤路","city":"秦皇岛"},"rating":4.3,"durationMin":90,"ticketPrice":25.0,"tags":["湿地","观鸟","候鸟"]},
  {"id":"QHD049","name":"北戴河近代别墅群","location":{"longitude":119.496,"latitude":39.822,"address":"秦皇岛市北戴河区","city":"秦皇岛"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["近代建筑","历史","免费"]},
  {"id":"QHD050","name":"秦皇岛夜市（太阳城）","location":{"longitude":119.605,"latitude":39.937,"address":"秦皇岛市海港区太阳城","city":"秦皇岛"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["夜市","美食","海鲜","免费"]},
])

# ===== 镇江 =====
add("镇江", [
  {"id":"ZJ001","name":"金山寺","location":{"longitude":119.417,"latitude":32.214,"address":"镇江市润州区金山路62号","city":"镇江"},"rating":4.5,"durationMin":150,"ticketPrice":65.0,"tags":["佛教","白蛇传","历史文化"]},
  {"id":"ZJ002","name":"焦山","location":{"longitude":119.450,"latitude":32.231,"address":"镇江市京口区东吴路","city":"镇江"},"rating":4.4,"durationMin":180,"ticketPrice":65.0,"tags":["岛屿","书法","碑林"]},
  {"id":"ZJ003","name":"北固山","location":{"longitude":119.456,"latitude":32.222,"address":"镇江市京口区东吴路3号","city":"镇江"},"rating":4.3,"durationMin":120,"ticketPrice":40.0,"tags":["历史文化","三国","刘备招亲"]},
  {"id":"ZJ004","name":"西津渡古街","location":{"longitude":119.434,"latitude":32.214,"address":"镇江市润州区西津渡街","city":"镇江"},"rating":4.5,"durationMin":120,"ticketPrice":0.0,"tags":["历史街区","古渡口","元代","免费"]},
  {"id":"ZJ005","name":"镇江博物馆","location":{"longitude":119.434,"latitude":32.214,"address":"镇江市润州区伯先路85号","city":"镇江"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","历史文化","免费"]},
  {"id":"ZJ006","name":"茅山风景区","location":{"longitude":119.303,"latitude":31.782,"address":"镇江市句容市茅山镇","city":"镇江"},"rating":4.4,"durationMin":360,"ticketPrice":100.0,"tags":["道教","第八洞天","自然"]},
  {"id":"ZJ007","name":"宝华山","location":{"longitude":119.092,"latitude":32.156,"address":"镇江市句容市宝华镇","city":"镇江"},"rating":4.3,"durationMin":240,"ticketPrice":50.0,"tags":["佛教","律宗第一山","自然"]},
  {"id":"ZJ008","name":"南山风景区","location":{"longitude":119.438,"latitude":32.183,"address":"镇江市润州区南山","city":"镇江"},"rating":4.3,"durationMin":180,"ticketPrice":40.0,"tags":["自然","森林公园","城市绿肺"]},
  {"id":"ZJ009","name":"镇江中国醋文化博物馆","location":{"longitude":119.482,"latitude":32.171,"address":"镇江市丹徒区恒顺大道","city":"镇江"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","醋文化","非遗","免费"]},
  {"id":"ZJ010","name":"赛珍珠故居","location":{"longitude":119.434,"latitude":32.214,"address":"镇江市润州区登云山路","city":"镇江"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["名人故居","诺贝尔文学奖","免费"]},
  {"id":"ZJ011","name":"京口宝鼎","location":{"longitude":119.464,"latitude":32.212,"address":"镇江市京口区","city":"镇江"},"rating":4.0,"durationMin":30,"ticketPrice":0.0,"tags":["地标","免费"]},
  {"id":"ZJ012","name":"镇江古运河风光带","location":{"longitude":119.435,"latitude":32.210,"address":"镇江市润州区古运河","city":"镇江"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["运河","历史","免费"]},
  {"id":"ZJ013","name":"招隐寺","location":{"longitude":119.440,"latitude":32.182,"address":"镇江市润州区南山招隐寺","city":"镇江"},"rating":4.1,"durationMin":90,"ticketPrice":40.0,"tags":["佛教","南朝","隐士文化"]},
  {"id":"ZJ014","name":"竹林寺","location":{"longitude":119.441,"latitude":32.183,"address":"镇江市润州区南山竹林寺","city":"镇江"},"rating":4.1,"durationMin":60,"ticketPrice":40.0,"tags":["佛教","自然"]},
  {"id":"ZJ015","name":"米芾书法公园","location":{"longitude":119.400,"latitude":32.140,"address":"镇江市丹徒区米芾路","city":"镇江"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["书法","艺术","免费"]},
  {"id":"ZJ016","name":"镇江大市口商圈","location":{"longitude":119.446,"latitude":32.206,"address":"镇江市京口区大市口","city":"镇江"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["购物","商业","免费"]},
  {"id":"ZJ017","name":"镇江科技馆","location":{"longitude":119.445,"latitude":32.205,"address":"镇江市京口区","city":"镇江"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["科普","亲子","免费"]},
  {"id":"ZJ018","name":"句容宝华山隆昌寺","location":{"longitude":119.092,"latitude":32.156,"address":"镇江市句容市宝华镇宝华山","city":"镇江"},"rating":4.3,"durationMin":120,"ticketPrice":50.0,"tags":["佛教","律宗","古刹","清代"]},
  {"id":"ZJ019","name":"镇江金山湖","location":{"longitude":119.415,"latitude":32.216,"address":"镇江市润州区金山湖畔","city":"镇江"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["湖泊","休闲","免费"]},
  {"id":"ZJ020","name":"镇江方志馆","location":{"longitude":119.445,"latitude":32.208,"address":"镇江市京口区","city":"镇江"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["博物馆","方志","免费"]},
  {"id":"ZJ021","name":"鹤林寺","location":{"longitude":119.441,"latitude":32.186,"address":"镇江市润州区","city":"镇江"},"rating":4.0,"durationMin":60,"ticketPrice":40.0,"tags":["佛教","古刹"]},
  {"id":"ZJ022","name":"润扬长江大桥","location":{"longitude":119.387,"latitude":32.217,"address":"镇江市润州区","city":"镇江"},"rating":4.1,"durationMin":30,"ticketPrice":0.0,"tags":["现代建筑","大桥","免费"]},
  {"id":"ZJ023","name":"丹阳眼镜城","location":{"longitude":119.581,"latitude":32.002,"address":"镇江市丹阳市","city":"镇江"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["购物","眼镜","免费"]},
  {"id":"ZJ024","name":"季子庙","location":{"longitude":119.684,"latitude":31.960,"address":"镇江市丹阳市延陵镇","city":"镇江"},"rating":4.1,"durationMin":60,"ticketPrice":20.0,"tags":["历史文化","春秋","季札"]},
  {"id":"ZJ025","name":"天地石刻园","location":{"longitude":119.581,"latitude":32.002,"address":"镇江市丹阳市","city":"镇江"},"rating":4.0,"durationMin":90,"ticketPrice":40.0,"tags":["石刻","艺术","六朝"]},
  {"id":"ZJ026","name":"扬中园博园","location":{"longitude":119.815,"latitude":32.230,"address":"镇江市扬中市","city":"镇江"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["园林","免费"]},
  {"id":"ZJ027","name":"圌山风景区","location":{"longitude":119.689,"latitude":32.210,"address":"镇江市京口区大路镇","city":"镇江"},"rating":4.1,"durationMin":180,"ticketPrice":30.0,"tags":["登山","自然","长江"]},
  {"id":"ZJ028","name":"镇江万达广场","location":{"longitude":119.432,"latitude":32.196,"address":"镇江市润州区黄山西路","city":"镇江"},"rating":4.0,"durationMin":120,"ticketPrice":0.0,"tags":["购物","免费"]},
  {"id":"ZJ029","name":"伯先公园","location":{"longitude":119.435,"latitude":32.213,"address":"镇江市润州区伯先路","city":"镇江"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"ZJ030","name":"河滨公园","location":{"longitude":119.443,"latitude":32.207,"address":"镇江市京口区","city":"镇江"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"ZJ031","name":"白娘子爱情文化园","location":{"longitude":119.413,"latitude":32.218,"address":"镇江市润州区金山湖畔","city":"镇江"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["主题公园","白蛇传","免费"]},
  {"id":"ZJ032","name":"镇江焦山碑林","location":{"longitude":119.450,"latitude":32.231,"address":"镇江市京口区焦山","city":"镇江"},"rating":4.3,"durationMin":90,"ticketPrice":65.0,"tags":["碑林","书法","南朝"]},
  {"id":"ZJ033","name":"镇江三山风景区","location":{"longitude":119.446,"latitude":32.220,"address":"镇江市京口区","city":"镇江"},"rating":4.5,"durationMin":360,"ticketPrice":120.0,"tags":["自然","长江","金山焦山北固山"]},
  {"id":"ZJ034","name":"镇江长江路滨江风光带","location":{"longitude":119.440,"latitude":32.228,"address":"镇江市润州区长江路","city":"镇江"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["江景","散步","免费"]},
  {"id":"ZJ035","name":"南徐森林公园","location":{"longitude":119.420,"latitude":32.198,"address":"镇江市润州区","city":"镇江"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["森林","公园","免费"]},
  {"id":"ZJ036","name":"清晖园","location":{"longitude":119.453,"latitude":32.207,"address":"镇江市京口区","city":"镇江"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["园林","免费"]},
  {"id":"ZJ037","name":"镇江民间艺术馆","location":{"longitude":119.434,"latitude":32.214,"address":"镇江市润州区伯先路","city":"镇江"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["非遗","民间艺术","免费"]},
  {"id":"ZJ038","name":"茅山新四军纪念馆","location":{"longitude":119.303,"latitude":31.782,"address":"镇江市句容市茅山镇","city":"镇江"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["红色旅游","抗日战争","免费"]},
  {"id":"ZJ039","name":"镇江丁卯桥","location":{"longitude":119.497,"latitude":32.163,"address":"镇江市京口区丁卯桥路","city":"镇江"},"rating":4.0,"durationMin":30,"ticketPrice":0.0,"tags":["古桥","唐代","免费"]},
  {"id":"ZJ040","name":"古运河历史文化街区","location":{"longitude":119.436,"latitude":32.212,"address":"镇江市润州区京口闸","city":"镇江"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["历史街区","运河","免费"]},
  {"id":"ZJ041","name":"冷遹纪念馆","location":{"longitude":119.435,"latitude":32.216,"address":"镇江市润州区","city":"镇江"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["名人","辛亥","免费"]},
  {"id":"ZJ042","name":"镇江南郊风景区","location":{"longitude":119.436,"latitude":32.180,"address":"镇江市润州区南郊","city":"镇江"},"rating":4.2,"durationMin":180,"ticketPrice":40.0,"tags":["自然","山林","休闲"]},
  {"id":"ZJ043","name":"江苏大学校园","location":{"longitude":119.519,"latitude":32.203,"address":"镇江市京口区学府路301号","city":"镇江"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["校园","教育","免费"]},
  {"id":"ZJ044","name":"镇江影剧院","location":{"longitude":119.445,"latitude":32.206,"address":"镇江市京口区","city":"镇江"},"rating":4.0,"durationMin":120,"ticketPrice":80.0,"tags":["演出","文化"]},
  {"id":"ZJ045","name":"丹阳水晶山旅游区","location":{"longitude":119.720,"latitude":32.000,"address":"镇江市丹阳市","city":"镇江"},"rating":4.0,"durationMin":120,"ticketPrice":50.0,"tags":["自然","度假","体育"]},
  {"id":"ZJ046","name":"句容赤山湖湿地公园","location":{"longitude":119.088,"latitude":31.922,"address":"镇江市句容市赤山湖","city":"镇江"},"rating":4.1,"durationMin":150,"ticketPrice":0.0,"tags":["湿地","自然","免费"]},
  {"id":"ZJ047","name":"恒顺醋坊体验馆","location":{"longitude":119.482,"latitude":32.171,"address":"镇江市丹徒区","city":"镇江"},"rating":4.1,"durationMin":90,"ticketPrice":30.0,"tags":["非遗","醋文化","体验"]},
  {"id":"ZJ048","name":"镇江夜市（西津渡）","location":{"longitude":119.434,"latitude":32.214,"address":"镇江市润州区西津渡","city":"镇江"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["夜市","美食","历史街区","免费"]},
  {"id":"ZJ049","name":"金山湖荷花池","location":{"longitude":119.415,"latitude":32.216,"address":"镇江市润州区金山湖畔","city":"镇江"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["荷花","夏季","免费"]},
  {"id":"ZJ050","name":"镇江京口闸遗址","location":{"longitude":119.436,"latitude":32.213,"address":"镇江市润州区京口闸","city":"镇江"},"rating":4.0,"durationMin":30,"ticketPrice":0.0,"tags":["遗址","水利","免费"]},
])

# ===== 常州 =====
add("常州", [
  {"id":"CZ001","name":"中华恐龙园","location":{"longitude":120.010,"latitude":31.826,"address":"常州市新北区汉江路1号","city":"常州"},"rating":4.6,"durationMin":480,"ticketPrice":260.0,"tags":["主题乐园","恐龙","亲子"]},
  {"id":"CZ002","name":"天目湖旅游度假区","location":{"longitude":119.456,"latitude":31.319,"address":"常州市溧阳市天目湖镇","city":"常州"},"rating":4.5,"durationMin":360,"ticketPrice":180.0,"tags":["湖泊","度假","自然"]},
  {"id":"CZ003","name":"南山竹海","location":{"longitude":119.515,"latitude":31.175,"address":"常州市溧阳市戴埠镇","city":"常州"},"rating":4.5,"durationMin":300,"ticketPrice":90.0,"tags":["竹海","自然","登山"]},
  {"id":"CZ004","name":"淹城春秋乐园","location":{"longitude":119.943,"latitude":31.706,"address":"常州市武进区武宜南路588号","city":"常州"},"rating":4.3,"durationMin":300,"ticketPrice":190.0,"tags":["主题乐园","春秋文化","遗址"]},
  {"id":"CZ005","name":"常州博物馆","location":{"longitude":119.963,"latitude":31.810,"address":"常州市新北区龙城大道1288号","city":"常州"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["博物馆","历史文化","免费"]},
  {"id":"CZ006","name":"天宁禅寺","location":{"longitude":119.970,"latitude":31.776,"address":"常州市天宁区延陵中路636号","city":"常州"},"rating":4.4,"durationMin":90,"ticketPrice":20.0,"tags":["佛教","天宁宝塔","古刹"]},
  {"id":"CZ007","name":"红梅公园","location":{"longitude":119.974,"latitude":31.776,"address":"常州市天宁区罗汉路1号","city":"常州"},"rating":4.3,"durationMin":90,"ticketPrice":0.0,"tags":["公园","红梅阁","免费"]},
  {"id":"CZ008","name":"环球动漫嬉戏谷","location":{"longitude":119.997,"latitude":31.533,"address":"常州市武进区太湖湾旅游度假区","city":"常州"},"rating":4.2,"durationMin":420,"ticketPrice":230.0,"tags":["主题乐园","动漫","亲子"]},
  {"id":"CZ009","name":"茅山森林世界","location":{"longitude":119.380,"latitude":31.760,"address":"常州市金坛区薛埠镇","city":"常州"},"rating":4.2,"durationMin":300,"ticketPrice":150.0,"tags":["森林","度假","亲子"]},
  {"id":"CZ010","name":"春秋淹城遗址","location":{"longitude":119.943,"latitude":31.706,"address":"常州市武进区淹城中路","city":"常州"},"rating":4.2,"durationMin":120,"ticketPrice":20.0,"tags":["遗址","春秋","历史文化"]},
  {"id":"CZ011","name":"常州运河五号创意街区","location":{"longitude":119.952,"latitude":31.787,"address":"常州市钟楼区三堡街141号","city":"常州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["文创","运河","工业遗产","免费"]},
  {"id":"CZ012","name":"溧阳御水温泉","location":{"longitude":119.515,"latitude":31.175,"address":"常州市溧阳市戴埠镇","city":"常州"},"rating":4.4,"durationMin":240,"ticketPrice":218.0,"tags":["温泉","度假","南山"]},
  {"id":"CZ013","name":"常州青枫公园","location":{"longitude":119.935,"latitude":31.799,"address":"常州市钟楼区","city":"常州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"CZ014","name":"常州市规划馆","location":{"longitude":119.963,"latitude":31.810,"address":"常州市新北区龙城大道","city":"常州"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["规划","城市","免费"]},
  {"id":"CZ015","name":"圩墩遗址公园","location":{"longitude":120.005,"latitude":31.737,"address":"常州市武进区戚墅堰","city":"常州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["遗址","新石器","免费"]},
  {"id":"CZ016","name":"武进新天地公园","location":{"longitude":119.951,"latitude":31.727,"address":"常州市武进区湖塘镇","city":"常州"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"CZ017","name":"常州梳篦博物馆","location":{"longitude":119.960,"latitude":31.782,"address":"常州市钟楼区勤业路","city":"常州"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["博物馆","非遗","梳篦","免费"]},
  {"id":"CZ018","name":"金坛茅山乾元观","location":{"longitude":119.380,"latitude":31.760,"address":"常州市金坛区薛埠镇茅山","city":"常州"},"rating":4.1,"durationMin":120,"ticketPrice":30.0,"tags":["道教","茅山","古观"]},
  {"id":"CZ019","name":"常州大剧院","location":{"longitude":119.963,"latitude":31.810,"address":"常州市新北区龙城大道","city":"常州"},"rating":4.1,"durationMin":120,"ticketPrice":100.0,"tags":["艺术","演出"]},
  {"id":"CZ020","name":"东坡公园","location":{"longitude":119.975,"latitude":31.772,"address":"常州市天宁区延陵东路","city":"常州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["公园","苏东坡","历史文化","免费"]},
  {"id":"CZ021","name":"紫荆公园","location":{"longitude":119.985,"latitude":31.795,"address":"常州市天宁区","city":"常州"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"CZ022","name":"常州美术馆","location":{"longitude":119.965,"latitude":31.808,"address":"常州市新北区","city":"常州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["美术馆","免费"]},
  {"id":"CZ023","name":"瞿秋白纪念馆","location":{"longitude":119.957,"latitude":31.785,"address":"常州市钟楼区延陵西路188号","city":"常州"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["名人故居","红色","免费"]},
  {"id":"CZ024","name":"常州动物园","location":{"longitude":120.010,"latitude":31.826,"address":"常州市新北区汉江路1号恐龙园内","city":"常州"},"rating":4.1,"durationMin":120,"ticketPrice":260.0,"tags":["动物园","亲子"]},
  {"id":"CZ025","name":"天目湖水世界","location":{"longitude":119.456,"latitude":31.319,"address":"常州市溧阳市天目湖镇","city":"常州"},"rating":4.2,"durationMin":300,"ticketPrice":150.0,"tags":["水上乐园","夏季","亲子"]},
  {"id":"CZ026","name":"中华孝道园","location":{"longitude":120.044,"latitude":31.450,"address":"常州市武进区雪堰镇","city":"常州"},"rating":4.2,"durationMin":180,"ticketPrice":100.0,"tags":["孝文化","主题","亲子"]},
  {"id":"CZ027","name":"常州动漫嬉戏谷水上乐园","location":{"longitude":119.997,"latitude":31.533,"address":"常州市武进区太湖湾","city":"常州"},"rating":4.1,"durationMin":300,"ticketPrice":160.0,"tags":["水上乐园","夏季"]},
  {"id":"CZ028","name":"长荡湖旅游度假区","location":{"longitude":119.578,"latitude":31.650,"address":"常州市金坛区长荡湖","city":"常州"},"rating":4.2,"durationMin":240,"ticketPrice":60.0,"tags":["湖泊","大闸蟹","湿地"]},
  {"id":"CZ029","name":"常州荆川公园","location":{"longitude":119.944,"latitude":31.774,"address":"常州市钟楼区清潭路","city":"常州"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"CZ030","name":"常州图书馆","location":{"longitude":119.963,"latitude":31.810,"address":"常州市新北区龙城大道","city":"常州"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["图书馆","免费"]},
  {"id":"CZ031","name":"常州科教城","location":{"longitude":119.966,"latitude":31.706,"address":"常州市武进区科教城","city":"常州"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["教育","科技","免费"]},
  {"id":"CZ032","name":"新龙生态林","location":{"longitude":119.981,"latitude":31.845,"address":"常州市新北区","city":"常州"},"rating":4.2,"durationMin":180,"ticketPrice":0.0,"tags":["森林","生态","免费"]},
  {"id":"CZ033","name":"宋剑湖湿地公园","location":{"longitude":120.037,"latitude":31.757,"address":"常州市武进区遥观镇","city":"常州"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["湿地","自然","免费"]},
  {"id":"CZ034","name":"常州荷园","location":{"longitude":119.976,"latitude":31.802,"address":"常州市钟楼区","city":"常州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["荷花","公园","免费"]},
  {"id":"CZ035","name":"滆湖低碳公园","location":{"longitude":119.880,"latitude":31.700,"address":"常州市武进区西太湖","city":"常州"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["低碳","湿地","免费"]},
  {"id":"CZ036","name":"常州吾悦广场","location":{"longitude":119.956,"latitude":31.783,"address":"常州市钟楼区延陵西路","city":"常州"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["购物","美食","免费"]},
  {"id":"CZ037","name":"三勤生态园","location":{"longitude":119.997,"latitude":31.690,"address":"常州市武进区","city":"常州"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["生态","农业","免费"]},
  {"id":"CZ038","name":"东坡故居（藤花旧馆）","location":{"longitude":119.975,"latitude":31.772,"address":"常州市天宁区","city":"常州"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["名人故居","宋代","免费"]},
  {"id":"CZ039","name":"常州花博园","location":{"longitude":119.870,"latitude":31.690,"address":"常州市武进区西太湖","city":"常州"},"rating":4.1,"durationMin":180,"ticketPrice":80.0,"tags":["花卉","园艺","展览"]},
  {"id":"CZ040","name":"太湖湾露营谷","location":{"longitude":120.040,"latitude":31.490,"address":"常州市武进区雪堰镇太湖湾","city":"常州"},"rating":4.2,"durationMin":240,"ticketPrice":100.0,"tags":["露营","自然","亲子"]},
  {"id":"CZ041","name":"金坛城南公园","location":{"longitude":119.568,"latitude":31.720,"address":"常州市金坛区","city":"常州"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"CZ042","name":"武进博物馆","location":{"longitude":119.951,"latitude":31.727,"address":"常州市武进区湖塘镇","city":"常州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"CZ043","name":"常州民防科普馆","location":{"longitude":119.965,"latitude":31.808,"address":"常州市新北区","city":"常州"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["科普","免费"]},
  {"id":"CZ044","name":"常州非物质文化遗产展示馆","location":{"longitude":119.965,"latitude":31.810,"address":"常州市新北区","city":"常州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["非遗","博物馆","免费"]},
  {"id":"CZ045","name":"西太湖影视基地","location":{"longitude":119.870,"latitude":31.690,"address":"常州市武进区西太湖","city":"常州"},"rating":4.0,"durationMin":180,"ticketPrice":80.0,"tags":["影视","民国风情"]},
  {"id":"CZ046","name":"焦溪古镇","location":{"longitude":119.954,"latitude":31.830,"address":"常州市新北区焦溪镇","city":"常州"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["古镇","历史文化","免费"]},
  {"id":"CZ047","name":"杨桥古村","location":{"longitude":119.945,"latitude":31.735,"address":"常州市武进区前黄镇","city":"常州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["古村落","历史文化","免费"]},
  {"id":"CZ048","name":"常州江南环球港","location":{"longitude":119.982,"latitude":31.831,"address":"常州市新北区通江中路","city":"常州"},"rating":4.2,"durationMin":180,"ticketPrice":0.0,"tags":["购物","摩天轮","亲子","免费"]},
  {"id":"CZ049","name":"一号农场（金坛）","location":{"longitude":119.380,"latitude":31.760,"address":"常州市金坛区薛埠镇","city":"常州"},"rating":4.1,"durationMin":180,"ticketPrice":50.0,"tags":["有机农业","亲子","体验"]},
  {"id":"CZ050","name":"溧阳博物馆","location":{"longitude":119.488,"latitude":31.420,"address":"常州市溧阳市南大街","city":"常州"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","焦尾琴传说","免费"]},
])

print(f"\n本次: {sum(1 for c in data['cities'] if c['city'] in ['秦皇岛','镇江','常州'])} 新增城市")
total = sum(len(c['attractions']) for c in data['cities'])
print(f"总计: {len(data['cities'])} 城市, {total} 景点")

with open('huazai-trip-skills/src/main/resources/attractions/knowledge-base.json','w',encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print("Done batch 15")
