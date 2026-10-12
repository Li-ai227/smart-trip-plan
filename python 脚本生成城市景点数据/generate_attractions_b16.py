import json

with open('huazai-trip-skills/src/main/resources/attractions/knowledge-base.json','r',encoding='utf-8') as f:
    data = json.load(f)

existing_ids = set()
for c in data['cities']:
    for a in c['attractions']:
        existing_ids.add(a['id'])

def add(city, attractions):
    added = skipped = 0
    found = False
    for c in data['cities']:
        if c['city'] == city:
            found = True
            for a in attractions:
                if a['id'] in existing_ids: skipped += 1
                else: c['attractions'].append(a); existing_ids.add(a['id']); added += 1
            break
    if not found:
        valid = [a for a in attractions if a['id'] not in existing_ids]
        for a in valid: existing_ids.add(a['id'])
        data['cities'].append({"city": city, "attractions": valid})
        added = len(valid); skipped = len(attractions) - added
    print(f"  {city}: +{added}" + (f" (skip{skipped})" if skipped else ""))

# ===== 北海 =====
add("北海", [
  {"id":"BH001","name":"北海银滩","location":{"longitude":109.139,"latitude":21.440,"address":"北海市银海区银滩大道","city":"北海"},"rating":4.5,"durationMin":300,"ticketPrice":0.0,"tags":["海滩","白沙滩","自然","免费"]},
  {"id":"BH002","name":"涠洲岛","location":{"longitude":109.115,"latitude":21.036,"address":"北海市海城区涠洲镇","city":"北海"},"rating":4.6,"durationMin":600,"ticketPrice":115.0,"tags":["海岛","火山","潜水"]},
  {"id":"BH003","name":"北海老街","location":{"longitude":109.121,"latitude":21.480,"address":"北海市海城区珠海路","city":"北海"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["历史街区","骑楼","美食","免费"]},
  {"id":"BH004","name":"海底世界","location":{"longitude":109.119,"latitude":21.478,"address":"北海市海城区茶亭路27号","city":"北海"},"rating":4.2,"durationMin":150,"ticketPrice":138.0,"tags":["海洋馆","亲子"]},
  {"id":"BH005","name":"北海金海湾红树林","location":{"longitude":109.175,"latitude":21.431,"address":"北海市银海区金海湾","city":"北海"},"rating":4.3,"durationMin":180,"ticketPrice":80.0,"tags":["红树林","湿地","赶海"]},
  {"id":"BH006","name":"侨港风情街","location":{"longitude":109.125,"latitude":21.438,"address":"北海市银海区侨港镇","city":"北海"},"rating":4.4,"durationMin":120,"ticketPrice":0.0,"tags":["美食","越南风情","夜市","免费"]},
  {"id":"BH007","name":"涠洲岛天主教堂","location":{"longitude":109.115,"latitude":21.036,"address":"北海市海城区涠洲岛盛塘村","city":"北海"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["教堂","哥特式","历史建筑"]},
  {"id":"BH008","name":"北海园博园","location":{"longitude":109.198,"latitude":21.468,"address":"北海市银海区南珠大道","city":"北海"},"rating":4.1,"durationMin":180,"ticketPrice":60.0,"tags":["园林","花卉"]},
  {"id":"BH009","name":"冠头岭国家森林公园","location":{"longitude":109.056,"latitude":21.456,"address":"北海市银海区冠头岭","city":"北海"},"rating":4.2,"durationMin":150,"ticketPrice":0.0,"tags":["森林","登山","海景","免费"]},
  {"id":"BH010","name":"北海海洋之窗","location":{"longitude":109.145,"latitude":21.445,"address":"北海市银海区四川路","city":"北海"},"rating":4.1,"durationMin":120,"ticketPrice":118.0,"tags":["海洋馆","亲子"]},
  {"id":"BH011","name":"涠洲岛五彩滩","location":{"longitude":109.125,"latitude":21.028,"address":"北海市海城区涠洲岛","city":"北海"},"rating":4.4,"durationMin":90,"ticketPrice":0.0,"tags":["海滩","日出","火山岩"]},
  {"id":"BH012","name":"涠洲岛鳄鱼山景区","location":{"longitude":109.108,"latitude":21.030,"address":"北海市海城区涠洲岛","city":"北海"},"rating":4.3,"durationMin":180,"ticketPrice":0.0,"tags":["火山","自然","灯塔"]},
  {"id":"BH013","name":"涠洲岛滴水丹屏","location":{"longitude":109.102,"latitude":21.045,"address":"北海市海城区涠洲岛","city":"北海"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["海滩","日落","自然"]},
  {"id":"BH014","name":"涠洲岛石螺口海滩","location":{"longitude":109.098,"latitude":21.050,"address":"北海市海城区涠洲岛","city":"北海"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["海滩","游泳","日落"]},
  {"id":"BH015","name":"北海德国领事馆旧址","location":{"longitude":109.121,"latitude":21.479,"address":"北海市海城区北部湾中路","city":"北海"},"rating":4.0,"durationMin":30,"ticketPrice":0.0,"tags":["近代建筑","历史","免费"]},
  {"id":"BH016","name":"北海水族馆","location":{"longitude":109.119,"latitude":21.478,"address":"北海市海城区茶亭路","city":"北海"},"rating":4.0,"durationMin":60,"ticketPrice":30.0,"tags":["水族馆","亲子"]},
  {"id":"BH017","name":"合浦汉墓博物馆","location":{"longitude":109.208,"latitude":21.662,"address":"北海市合浦县廉州镇","city":"北海"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","汉代","海上丝绸之路","免费"]},
  {"id":"BH018","name":"涠洲岛贝壳沙滩","location":{"longitude":109.129,"latitude":21.045,"address":"北海市海城区涠洲岛","city":"北海"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["海滩","贝壳","赶海"]},
  {"id":"BH019","name":"北部湾广场","location":{"longitude":109.120,"latitude":21.477,"address":"北海市海城区北部湾中路","city":"北海"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["广场","地标","免费"]},
  {"id":"BH020","name":"北海近代建筑群","location":{"longitude":109.121,"latitude":21.479,"address":"北海市海城区","city":"北海"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["近代建筑","世界遗产预备","免费"]},
  {"id":"BH021","name":"外沙海鲜岛","location":{"longitude":109.110,"latitude":21.486,"address":"北海市海城区外沙","city":"北海"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["海鲜","美食","免费"]},
  {"id":"BH022","name":"田野生态旅游观光园","location":{"longitude":109.165,"latitude":21.460,"address":"北海市银海区","city":"北海"},"rating":4.0,"durationMin":120,"ticketPrice":40.0,"tags":["生态","农业"]},
  {"id":"BH023","name":"北海长青公园","location":{"longitude":109.125,"latitude":21.472,"address":"北海市海城区","city":"北海"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"BH024","name":"中山公园","location":{"longitude":109.125,"latitude":21.475,"address":"北海市海城区","city":"北海"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"BH025","name":"涠洲岛火山国家地质公园","location":{"longitude":109.115,"latitude":21.036,"address":"北海市海城区涠洲岛","city":"北海"},"rating":4.4,"durationMin":300,"ticketPrice":0.0,"tags":["地质","火山","国家公园"]},
  {"id":"BH026","name":"普度寺","location":{"longitude":109.056,"latitude":21.455,"address":"北海市银海区冠头岭","city":"北海"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["佛教","免费"]},
  {"id":"BH027","name":"南珠宫","location":{"longitude":109.124,"latitude":21.473,"address":"北海市海城区茶亭路4号","city":"北海"},"rating":4.1,"durationMin":60,"ticketPrice":50.0,"tags":["珍珠","博物馆","南珠"]},
  {"id":"BH028","name":"夏日海滩运动中心","location":{"longitude":109.139,"latitude":21.440,"address":"北海市银海区银滩","city":"北海"},"rating":4.1,"durationMin":180,"ticketPrice":80.0,"tags":["水上运动","帆船","海滩"]},
  {"id":"BH029","name":"北海中山路老街","location":{"longitude":109.123,"latitude":21.478,"address":"北海市海城区中山路","city":"北海"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["历史街区","免费"]},
  {"id":"BH030","name":"侨港海滩","location":{"longitude":109.127,"latitude":21.435,"address":"北海市银海区侨港镇","city":"北海"},"rating":4.2,"durationMin":180,"ticketPrice":0.0,"tags":["海滩","游泳","免费"]},
  {"id":"BH031","name":"涠洲岛南湾海上运动","location":{"longitude":109.118,"latitude":21.033,"address":"北海市海城区涠洲岛南湾","city":"北海"},"rating":4.2,"durationMin":180,"ticketPrice":150.0,"tags":["潜水","帆船","海上运动"]},
  {"id":"BH032","name":"北海市博物馆","location":{"longitude":109.121,"latitude":21.478,"address":"北海市海城区","city":"北海"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"BH033","name":"北海图书馆","location":{"longitude":109.131,"latitude":21.470,"address":"北海市海城区北海大道","city":"北海"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["图书馆","免费"]},
  {"id":"BH034","name":"大墩海渔村","location":{"longitude":109.065,"latitude":21.450,"address":"北海市银海区大墩海","city":"北海"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["渔村","海鲜","免费"]},
  {"id":"BH035","name":"涠洲岛蓝桥","location":{"longitude":109.099,"latitude":21.063,"address":"北海市海城区涠洲岛","city":"北海"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["摄影","废弃码头","小众"]},
  {"id":"BH036","name":"星岛湖","location":{"longitude":109.241,"latitude":21.720,"address":"北海市合浦县星岛湖镇","city":"北海"},"rating":4.2,"durationMin":180,"ticketPrice":100.0,"tags":["湖泊","水浒城","岛屿"]},
  {"id":"BH037","name":"大江埠越南村","location":{"longitude":109.175,"latitude":21.460,"address":"北海市银海区","city":"北海"},"rating":4.0,"durationMin":90,"ticketPrice":30.0,"tags":["越南风情","民俗"]},
  {"id":"BH038","name":"涠洲岛圣母堂","location":{"longitude":109.113,"latitude":21.048,"address":"北海市海城区涠洲岛","city":"北海"},"rating":4.0,"durationMin":30,"ticketPrice":0.0,"tags":["教堂","免费"]},
  {"id":"BH039","name":"北海海滨公园","location":{"longitude":109.118,"latitude":21.478,"address":"北海市海城区","city":"北海"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["公园","海景","免费"]},
  {"id":"BH040","name":"南珠广场","location":{"longitude":109.140,"latitude":21.464,"address":"北海市银海区广东路","city":"北海"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["广场","免费"]},
  {"id":"BH041","name":"北海出口加工区免税店","location":{"longitude":109.131,"latitude":21.467,"address":"北海市海城区","city":"北海"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["购物","免税","免费"]},
  {"id":"BH042","name":"合浦汉闾文化园","location":{"longitude":109.208,"latitude":21.662,"address":"北海市合浦县","city":"北海"},"rating":4.1,"durationMin":120,"ticketPrice":60.0,"tags":["汉代文化","仿古","主题"]},
  {"id":"BH043","name":"北海近代邮电历史陈列馆","location":{"longitude":109.121,"latitude":21.479,"address":"北海市海城区","city":"北海"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["博物馆","邮政","近代","免费"]},
  {"id":"BH044","name":"冠山海高尔夫","location":{"longitude":109.070,"latitude":21.450,"address":"北海市银海区冠头岭","city":"北海"},"rating":4.0,"durationMin":240,"ticketPrice":500.0,"tags":["高尔夫","运动"]},
  {"id":"BH045","name":"涠洲岛三婆庙","location":{"longitude":109.115,"latitude":21.036,"address":"北海市海城区涠洲岛","city":"北海"},"rating":4.0,"durationMin":30,"ticketPrice":0.0,"tags":["妈祖","民俗","免费"]},
  {"id":"BH046","name":"北海帆船基地","location":{"longitude":109.139,"latitude":21.440,"address":"北海市银海区银滩","city":"北海"},"rating":4.1,"durationMin":120,"ticketPrice":180.0,"tags":["帆船","运动"]},
  {"id":"BH047","name":"地角渔港","location":{"longitude":109.102,"latitude":21.494,"address":"北海市海城区地角","city":"北海"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["渔港","海鲜","免费"]},
  {"id":"BH048","name":"北海夜市（长青路）","location":{"longitude":109.125,"latitude":21.472,"address":"北海市海城区长青路","city":"北海"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["夜市","美食","海鲜","免费"]},
  {"id":"BH049","name":"曲樟客家土围城","location":{"longitude":109.456,"latitude":21.716,"address":"北海市合浦县曲樟乡","city":"北海"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["客家文化","古建筑","免费"]},
  {"id":"BH050","name":"涠洲岛斜阳岛","location":{"longitude":109.208,"latitude":21.000,"address":"北海市海城区涠洲镇斜阳岛","city":"北海"},"rating":4.4,"durationMin":480,"ticketPrice":0.0,"tags":["海岛","原始","小众","免费"]},
])

# ===== 十堰（武当山）=====
add("十堰", [
  {"id":"SYW001","name":"武当山风景区","location":{"longitude":111.005,"latitude":32.408,"address":"十堰市丹江口市武当山","city":"十堰"},"rating":4.7,"durationMin":480,"ticketPrice":240.0,"tags":["道教","世界遗产","武术"]},
  {"id":"SYW002","name":"武当山紫霄宫","location":{"longitude":111.015,"latitude":32.418,"address":"十堰市丹江口市武当山","city":"十堰"},"rating":4.5,"durationMin":120,"ticketPrice":15.0,"tags":["道教","古建筑","明代"]},
  {"id":"SYW003","name":"武当山金顶","location":{"longitude":111.005,"latitude":32.408,"address":"十堰市丹江口市武当山天柱峰","city":"十堰"},"rating":4.8,"durationMin":180,"ticketPrice":27.0,"tags":["道教","金殿","云海"]},
  {"id":"SYW004","name":"武当山南岩","location":{"longitude":111.020,"latitude":32.415,"address":"十堰市丹江口市武当山","city":"十堰"},"rating":4.5,"durationMin":150,"ticketPrice":0.0,"tags":["道教","悬崖","龙头香"]},
  {"id":"SYW005","name":"武当山太子坡","location":{"longitude":111.025,"latitude":32.425,"address":"十堰市丹江口市武当山","city":"十堰"},"rating":4.3,"durationMin":90,"ticketPrice":0.0,"tags":["道教","古建筑","九曲黄河墙"]},
  {"id":"SYW006","name":"丹江口水库","location":{"longitude":111.514,"latitude":32.558,"address":"十堰市丹江口市丹江口水库","city":"十堰"},"rating":4.3,"durationMin":240,"ticketPrice":0.0,"tags":["水库","南水北调","自然","免费"]},
  {"id":"SYW007","name":"五龙河风景区","location":{"longitude":110.420,"latitude":32.866,"address":"十堰市郧西县安家乡","city":"十堰"},"rating":4.2,"durationMin":300,"ticketPrice":80.0,"tags":["峡谷","自然","瀑布"]},
  {"id":"SYW008","name":"野人洞","location":{"longitude":110.561,"latitude":31.898,"address":"十堰市房县野人洞","city":"十堰"},"rating":4.0,"durationMin":120,"ticketPrice":60.0,"tags":["溶洞","野人传说","自然"]},
  {"id":"SYW009","name":"十堰博物馆","location":{"longitude":110.788,"latitude":32.621,"address":"十堰市茅箭区北京北路","city":"十堰"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","恐龙","免费"]},
  {"id":"SYW010","name":"武当山逍遥谷","location":{"longitude":111.030,"latitude":32.430,"address":"十堰市丹江口市武当山","city":"十堰"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["自然","猕猴","幽谷"]},
  {"id":"SYW011","name":"武当山琼台","location":{"longitude":111.000,"latitude":32.410,"address":"十堰市丹江口市武当山","city":"十堰"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["道教","索道"]},
  {"id":"SYW012","name":"武当山磨针井","location":{"longitude":111.031,"latitude":32.438,"address":"十堰市丹江口市武当山","city":"十堰"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["道教","传说"]},
  {"id":"SYW013","name":"武当山玉虚宫","location":{"longitude":111.047,"latitude":32.444,"address":"十堰市丹江口市武当山镇","city":"十堰"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["道教","遗址","免费"]},
  {"id":"SYW014","name":"武当山博物馆","location":{"longitude":111.047,"latitude":32.444,"address":"十堰市丹江口市武当山镇","city":"十堰"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","道教","免费"]},
  {"id":"SYW015","name":"武当太极湖","location":{"longitude":111.114,"latitude":32.483,"address":"十堰市丹江口市武当山太极湖","city":"十堰"},"rating":4.2,"durationMin":180,"ticketPrice":80.0,"tags":["湖泊","休闲","水上"]},
  {"id":"SYW016","name":"十堰牛头山森林公园","location":{"longitude":110.758,"latitude":32.601,"address":"十堰市张湾区牛头山","city":"十堰"},"rating":4.1,"durationMin":180,"ticketPrice":0.0,"tags":["森林","登山","免费"]},
  {"id":"SYW017","name":"赛武当风景区","location":{"longitude":110.825,"latitude":32.536,"address":"十堰市茅箭区赛武当","city":"十堰"},"rating":4.1,"durationMin":240,"ticketPrice":40.0,"tags":["自然","登山","峡谷"]},
  {"id":"SYW018","name":"九龙瀑","location":{"longitude":110.720,"latitude":32.880,"address":"十堰市郧阳区南化塘镇","city":"十堰"},"rating":4.2,"durationMin":180,"ticketPrice":80.0,"tags":["瀑布","峡谷","自然"]},
  {"id":"SYW019","name":"虎啸滩","location":{"longitude":110.691,"latitude":32.864,"address":"十堰市郧阳区大柳乡","city":"十堰"},"rating":4.1,"durationMin":180,"ticketPrice":50.0,"tags":["瀑布","自然"]},
  {"id":"SYW020","name":"龙潭河","location":{"longitude":110.342,"latitude":32.890,"address":"十堰市郧西县羊尾镇","city":"十堰"},"rating":4.2,"durationMin":180,"ticketPrice":60.0,"tags":["峡谷","瀑布","自然"]},
  {"id":"SYW021","name":"十堰人民公园","location":{"longitude":110.774,"latitude":32.637,"address":"十堰市张湾区公园路","city":"十堰"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"SYW022","name":"四方山植物园","location":{"longitude":110.771,"latitude":32.650,"address":"十堰市张湾区四方山","city":"十堰"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["植物","登山","免费"]},
  {"id":"SYW023","name":"龙泉寺","location":{"longitude":110.757,"latitude":32.598,"address":"十堰市张湾区","city":"十堰"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["佛教","免费"]},
  {"id":"SYW024","name":"十堰市美术馆","location":{"longitude":110.796,"latitude":32.636,"address":"十堰市茅箭区","city":"十堰"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["美术馆","免费"]},
  {"id":"SYW025","name":"武当山遇真宫","location":{"longitude":111.047,"latitude":32.444,"address":"十堰市丹江口市武当山镇","city":"十堰"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["道教","遗址","免费"]},
  {"id":"SYW026","name":"沧浪海旅游港","location":{"longitude":111.514,"latitude":32.558,"address":"十堰市丹江口市丹江口水库","city":"十堰"},"rating":4.1,"durationMin":240,"ticketPrice":80.0,"tags":["港口","水上","度假"]},
  {"id":"SYW027","name":"武当峡谷漂流","location":{"longitude":111.100,"latitude":32.350,"address":"十堰市丹江口市","city":"十堰"},"rating":4.2,"durationMin":240,"ticketPrice":168.0,"tags":["漂流","夏季","户外"]},
  {"id":"SYW028","name":"黄龙古镇","location":{"longitude":110.564,"latitude":32.649,"address":"十堰市张湾区黄龙镇","city":"十堰"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["古镇","免费"]},
  {"id":"SYW029","name":"上津古城","location":{"longitude":110.250,"latitude":33.171,"address":"十堰市郧西县上津镇","city":"十堰"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["古城","明清","免费"]},
  {"id":"SYW030","name":"武当山索道","location":{"longitude":111.005,"latitude":32.408,"address":"十堰市丹江口市武当山","city":"十堰"},"rating":4.3,"durationMin":30,"ticketPrice":80.0,"tags":["索道","观光"]},
  {"id":"SYW031","name":"十堰市图书馆","location":{"longitude":110.790,"latitude":32.640,"address":"十堰市茅箭区","city":"十堰"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["图书馆","免费"]},
  {"id":"SYW032","name":"白马山","location":{"longitude":110.720,"latitude":32.620,"address":"十堰市张湾区白马山","city":"十堰"},"rating":4.1,"durationMin":180,"ticketPrice":30.0,"tags":["登山","自然","道教"]},
  {"id":"SYW033","name":"偏头山森林公园","location":{"longitude":109.742,"latitude":32.285,"address":"十堰市竹溪县","city":"十堰"},"rating":4.0,"durationMin":150,"ticketPrice":0.0,"tags":["森林","自然","免费"]},
  {"id":"SYW034","name":"女娲山","location":{"longitude":110.241,"latitude":32.302,"address":"十堰市竹山县宝丰镇","city":"十堰"},"rating":4.1,"durationMin":120,"ticketPrice":40.0,"tags":["历史文化","女娲传说"]},
  {"id":"SYW035","name":"楠木寨","location":{"longitude":109.723,"latitude":31.975,"address":"十堰市竹溪县新洲镇","city":"十堰"},"rating":4.1,"durationMin":180,"ticketPrice":60.0,"tags":["金丝楠木","自然","小众"]},
  {"id":"SYW036","name":"郧阳恐龙蛋化石群","location":{"longitude":110.810,"latitude":32.830,"address":"十堰市郧阳区","city":"十堰"},"rating":4.2,"durationMin":90,"ticketPrice":40.0,"tags":["恐龙","化石","地质"]},
  {"id":"SYW037","name":"青龙山恐龙蛋化石博物馆","location":{"longitude":110.810,"latitude":32.830,"address":"十堰市郧阳区","city":"十堰"},"rating":4.1,"durationMin":60,"ticketPrice":30.0,"tags":["博物馆","恐龙蛋","地质"]},
  {"id":"SYW038","name":"房县温泉","location":{"longitude":110.734,"latitude":32.055,"address":"十堰市房县","city":"十堰"},"rating":4.1,"durationMin":240,"ticketPrice":128.0,"tags":["温泉","养生"]},
  {"id":"SYW039","name":"武当山元和观","location":{"longitude":111.040,"latitude":32.435,"address":"十堰市丹江口市武当山镇","city":"十堰"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["道教","古建筑","免费"]},
  {"id":"SYW040","name":"十堰市科技馆","location":{"longitude":110.790,"latitude":32.639,"address":"十堰市茅箭区","city":"十堰"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["科普","亲子","免费"]},
  {"id":"SYW041","name":"桃花湖","location":{"longitude":110.745,"latitude":32.615,"address":"十堰市张湾区","city":"十堰"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["湖泊","免费"]},
  {"id":"SYW042","name":"大川生态农业观光园","location":{"longitude":110.765,"latitude":32.590,"address":"十堰市茅箭区大川镇","city":"十堰"},"rating":4.0,"durationMin":120,"ticketPrice":30.0,"tags":["农业","采摘","亲子"]},
  {"id":"SYW043","name":"武当山冲虚庵","location":{"longitude":111.045,"latitude":32.442,"address":"十堰市丹江口市武当山镇","city":"十堰"},"rating":4.0,"durationMin":30,"ticketPrice":0.0,"tags":["道教","免费"]},
  {"id":"SYW044","name":"十堰老街","location":{"longitude":110.780,"latitude":32.645,"address":"十堰市张湾区","city":"十堰"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["历史街区","免费"]},
  {"id":"SYW045","name":"郧西天河旅游区","location":{"longitude":110.420,"latitude":32.978,"address":"十堰市郧西县","city":"十堰"},"rating":4.2,"durationMin":240,"ticketPrice":80.0,"tags":["七夕文化","自然","主题"]},
  {"id":"SYW046","name":"竹山圣水湖","location":{"longitude":110.100,"latitude":32.220,"address":"十堰市竹山县上庸镇","city":"十堰"},"rating":4.1,"durationMin":180,"ticketPrice":60.0,"tags":["湖泊","古庸国","自然"]},
  {"id":"SYW047","name":"房县野人谷","location":{"longitude":110.561,"latitude":31.898,"address":"十堰市房县","city":"十堰"},"rating":4.2,"durationMin":240,"ticketPrice":80.0,"tags":["峡谷","自然","野人传说"]},
  {"id":"SYW048","name":"十堰艳湖公园","location":{"longitude":110.768,"latitude":32.644,"address":"十堰市张湾区","city":"十堰"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"SYW049","name":"武当不夜城","location":{"longitude":111.114,"latitude":32.483,"address":"十堰市丹江口市太极湖","city":"十堰"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["夜市","武当文化","演出","免费"]},
  {"id":"SYW050","name":"武当山太极剧场","location":{"longitude":111.114,"latitude":32.483,"address":"十堰市丹江口市武当山太极湖","city":"十堰"},"rating":4.2,"durationMin":90,"ticketPrice":198.0,"tags":["演出","太极","武当"]},
])

# ===== 延吉 =====
add("延吉", [
  {"id":"YJ001","name":"长白山北坡景区","location":{"longitude":128.067,"latitude":42.018,"address":"延边州安图县二道白河镇","city":"延吉"},"rating":4.8,"durationMin":480,"ticketPrice":125.0,"tags":["天池","自然","火山","雪山"]},
  {"id":"YJ002","name":"帽儿山国家森林公园","location":{"longitude":129.474,"latitude":42.858,"address":"延吉市延南路","city":"延吉"},"rating":4.3,"durationMin":180,"ticketPrice":0.0,"tags":["森林","登山","城市观景","免费"]},
  {"id":"YJ003","name":"延边博物馆","location":{"longitude":129.487,"latitude":42.904,"address":"延吉市长白山路","city":"延吉"},"rating":4.3,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","朝鲜族文化","免费"]},
  {"id":"YJ004","name":"中国朝鲜族民俗园","location":{"longitude":129.508,"latitude":42.896,"address":"延吉市延南路","city":"延吉"},"rating":4.4,"durationMin":180,"ticketPrice":30.0,"tags":["民俗","朝鲜族","体验"]},
  {"id":"YJ005","name":"延吉人民公园","location":{"longitude":129.506,"latitude":42.908,"address":"延吉市公园路","city":"延吉"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["公园","休闲","免费"]},
  {"id":"YJ006","name":"延吉西市场","location":{"longitude":129.502,"latitude":42.910,"address":"延吉市解放路","city":"延吉"},"rating":4.4,"durationMin":120,"ticketPrice":0.0,"tags":["市场","朝鲜族美食","购物","免费"]},
  {"id":"YJ007","name":"延边大学","location":{"longitude":129.494,"latitude":42.903,"address":"延吉市公园路977号","city":"延吉"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["校园","免费"]},
  {"id":"YJ008","name":"布尔哈通河","location":{"longitude":129.515,"latitude":42.903,"address":"延吉市延河路","city":"延吉"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["河流","散步","免费"]},
  {"id":"YJ009","name":"延吉水上市场","location":{"longitude":129.500,"latitude":42.904,"address":"延吉市参花街","city":"延吉"},"rating":4.5,"durationMin":90,"ticketPrice":0.0,"tags":["早市","朝鲜族美食","市井文化","免费"]},
  {"id":"YJ010","name":"延吉恐龙博物馆","location":{"longitude":129.490,"latitude":42.895,"address":"延吉市","city":"延吉"},"rating":4.1,"durationMin":90,"ticketPrice":50.0,"tags":["博物馆","恐龙","亲子"]},
  {"id":"YJ011","name":"延吉金达莱广场","location":{"longitude":129.500,"latitude":42.900,"address":"延吉市","city":"延吉"},"rating":4.0,"durationMin":30,"ticketPrice":0.0,"tags":["广场","免费"]},
  {"id":"YJ012","name":"延吉青年湖公园","location":{"longitude":129.508,"latitude":42.912,"address":"延吉市","city":"延吉"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"YJ013","name":"延吉朝鲜族料理体验","location":{"longitude":129.505,"latitude":42.908,"address":"延吉市多个冷面/烤肉店","city":"延吉"},"rating":4.5,"durationMin":120,"ticketPrice":0.0,"tags":["美食","冷面","烤肉","免费"]},
  {"id":"YJ014","name":"延吉万达广场","location":{"longitude":129.480,"latitude":42.916,"address":"延吉市延河路","city":"延吉"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["购物","免费"]},
  {"id":"YJ015","name":"帽儿山民俗村","location":{"longitude":129.474,"latitude":42.858,"address":"延吉市帽儿山","city":"延吉"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["民俗村","朝鲜族","免费"]},
  {"id":"YJ016","name":"延吉市少年宫","location":{"longitude":129.505,"latitude":42.900,"address":"延吉市","city":"延吉"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["教育","亲子","免费"]},
  {"id":"YJ017","name":"延吉科技馆","location":{"longitude":129.488,"latitude":42.898,"address":"延吉市","city":"延吉"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["科普","免费"]},
  {"id":"YJ018","name":"延吉铁南基督教堂","location":{"longitude":129.507,"latitude":42.901,"address":"延吉市铁南路","city":"延吉"},"rating":4.0,"durationMin":30,"ticketPrice":0.0,"tags":["教堂","建筑","免费"]},
  {"id":"YJ019","name":"延边革命烈士陵园","location":{"longitude":129.504,"latitude":42.915,"address":"延吉市","city":"延吉"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["红色","纪念","免费"]},
  {"id":"YJ020","name":"延吉阿里郎剧场","location":{"longitude":129.508,"latitude":42.900,"address":"延吉市","city":"延吉"},"rating":4.1,"durationMin":120,"ticketPrice":100.0,"tags":["演出","朝鲜族歌舞"]},
  {"id":"YJ021","name":"小河龙民俗村","location":{"longitude":129.550,"latitude":42.920,"address":"延吉市小营镇","city":"延吉"},"rating":4.0,"durationMin":120,"ticketPrice":0.0,"tags":["民俗村","农家乐","免费"]},
  {"id":"YJ022","name":"延吉市图书馆","location":{"longitude":129.495,"latitude":42.903,"address":"延吉市公园路","city":"延吉"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["图书馆","免费"]},
  {"id":"YJ023","name":"海兰江高尔夫球场","location":{"longitude":129.520,"latitude":42.880,"address":"延吉市","city":"延吉"},"rating":4.0,"durationMin":240,"ticketPrice":400.0,"tags":["高尔夫"]},
  {"id":"YJ024","name":"梦都美滑雪场","location":{"longitude":129.380,"latitude":42.950,"address":"延吉市依兰镇","city":"延吉"},"rating":4.1,"durationMin":300,"ticketPrice":150.0,"tags":["滑雪","冬季","温泉"]},
  {"id":"YJ025","name":"延吉春兴朝鲜族古村落","location":{"longitude":129.430,"latitude":42.950,"address":"延吉市依兰镇春兴村","city":"延吉"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["古村落","朝鲜族","免费"]},
  {"id":"YJ026","name":"图们江边境","location":{"longitude":129.850,"latitude":42.960,"address":"延边州图们市图们江","city":"延吉"},"rating":4.3,"durationMin":180,"ticketPrice":0.0,"tags":["边境","图们江","朝鲜","免费"]},
  {"id":"YJ027","name":"防川风景区","location":{"longitude":130.610,"latitude":42.439,"address":"延边州珲春市敬信镇防川村","city":"延吉"},"rating":4.4,"durationMin":360,"ticketPrice":70.0,"tags":["边境","一眼望三国","自然"]},
  {"id":"YJ028","name":"朝鲜族歌舞团演出","location":{"longitude":129.508,"latitude":42.900,"address":"延吉市","city":"延吉"},"rating":4.3,"durationMin":90,"ticketPrice":100.0,"tags":["歌舞","朝鲜族","非遗"]},
  {"id":"YJ029","name":"延吉东北亚植物园","location":{"longitude":129.480,"latitude":42.860,"address":"延吉市","city":"延吉"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["植物","免费"]},
  {"id":"YJ030","name":"长白山魔界漂流","location":{"longitude":128.078,"latitude":42.035,"address":"延边州安图县二道白河镇","city":"延吉"},"rating":4.3,"durationMin":180,"ticketPrice":180.0,"tags":["漂流","雾凇","冬季"]},
  {"id":"YJ031","name":"长白山温泉","location":{"longitude":128.067,"latitude":42.018,"address":"延边州安图县二道白河镇","city":"延吉"},"rating":4.4,"durationMin":240,"ticketPrice":198.0,"tags":["温泉","火山","养生"]},
  {"id":"YJ032","name":"长白山自然博物馆","location":{"longitude":128.115,"latitude":42.467,"address":"延边州安图县二道白河镇","city":"延吉"},"rating":4.2,"durationMin":90,"ticketPrice":40.0,"tags":["博物馆","自然","长白山"]},
  {"id":"YJ033","name":"美人松公园","location":{"longitude":128.115,"latitude":42.467,"address":"延边州安图县二道白河镇","city":"延吉"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["公园","美人松","免费"]},
  {"id":"YJ034","name":"延吉日落观景台","location":{"longitude":129.474,"latitude":42.858,"address":"延吉市帽儿山山顶","city":"延吉"},"rating":4.3,"durationMin":60,"ticketPrice":0.0,"tags":["日落","城市全景","免费"]},
  {"id":"YJ035","name":"延吉美食攻略（延大对面）","location":{"longitude":129.495,"latitude":42.904,"address":"延吉市公园路延大对面","city":"延吉"},"rating":4.5,"durationMin":120,"ticketPrice":0.0,"tags":["美食","韩式","冷面","免费"]},
  {"id":"YJ036","name":"延吉丰茂串城","location":{"longitude":129.503,"latitude":42.907,"address":"延吉市参花街","city":"延吉"},"rating":4.3,"durationMin":90,"ticketPrice":0.0,"tags":["烧烤","韩式","美食","免费"]},
  {"id":"YJ037","name":"延吉辣白菜体验馆","location":{"longitude":129.508,"latitude":42.896,"address":"延吉市延南路民俗园","city":"延吉"},"rating":4.2,"durationMin":90,"ticketPrice":30.0,"tags":["非遗","韩食","体验"]},
  {"id":"YJ038","name":"延吉市会展中心","location":{"longitude":129.490,"latitude":42.896,"address":"延吉市","city":"延吉"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["展览","免费"]},
  {"id":"YJ039","name":"延吉朝阳川国际机场观景","location":{"longitude":129.439,"latitude":42.881,"address":"延吉市朝阳川镇","city":"延吉"},"rating":4.0,"durationMin":30,"ticketPrice":0.0,"tags":["免费"]},
  {"id":"YJ040","name":"延边长鼓体验","location":{"longitude":129.508,"latitude":42.896,"address":"延吉市延南路民俗园","city":"延吉"},"rating":4.2,"durationMin":60,"ticketPrice":30.0,"tags":["非遗","朝鲜族长鼓","体验"]},
  {"id":"YJ041","name":"和龙仙景台","location":{"longitude":129.008,"latitude":42.553,"address":"延边州和龙市南坪镇","city":"延吉"},"rating":4.2,"durationMin":180,"ticketPrice":60.0,"tags":["自然","火山","登山"]},
  {"id":"YJ042","name":"老里克湖","location":{"longitude":128.400,"latitude":42.480,"address":"延边州和龙市","city":"延吉"},"rating":4.4,"durationMin":300,"ticketPrice":80.0,"tags":["雪景","雪淞","自然"]},
  {"id":"YJ043","name":"龙井明东民俗村","location":{"longitude":129.423,"latitude":42.767,"address":"延边州龙井市","city":"延吉"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["民俗","朝鲜族","免费"]},
  {"id":"YJ044","name":"敦化六鼎山","location":{"longitude":128.202,"latitude":43.370,"address":"延边州敦化市六鼎山","city":"延吉"},"rating":4.3,"durationMin":240,"ticketPrice":80.0,"tags":["佛教","金鼎大佛","世界最高释迦牟尼坐像"]},
  {"id":"YJ045","name":"雁鸣湖湿地","location":{"longitude":128.035,"latitude":43.630,"address":"延边州敦化市雁鸣湖镇","city":"延吉"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["湿地","自然","免费"]},
  {"id":"YJ046","name":"珲春圈河口岸","location":{"longitude":130.535,"latitude":42.566,"address":"延边州珲春市圈河口岸","city":"延吉"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["边境","口岸","免费"]},
  {"id":"YJ047","name":"长白山聚龙温泉群","location":{"longitude":128.067,"latitude":42.018,"address":"延边州安图县长白山","city":"延吉"},"rating":4.4,"durationMin":90,"ticketPrice":0.0,"tags":["温泉","火山","自然奇观"]},
  {"id":"YJ048","name":"长白山绿渊潭","location":{"longitude":128.063,"latitude":42.020,"address":"延边州安图县长白山","city":"延吉"},"rating":4.5,"durationMin":60,"ticketPrice":0.0,"tags":["瀑布","湖泊","自然"]},
  {"id":"YJ049","name":"长白山地下森林","location":{"longitude":128.060,"latitude":42.025,"address":"延边州安图县长白山","city":"延吉"},"rating":4.4,"durationMin":120,"ticketPrice":0.0,"tags":["原始森林","峡谷","自然"]},
  {"id":"YJ050","name":"长白瀑布群","location":{"longitude":128.065,"latitude":42.019,"address":"延边州安图县长白山","city":"延吉"},"rating":4.6,"durationMin":90,"ticketPrice":0.0,"tags":["瀑布","天池","自然"]},
])

# ===== 丹东 =====
add("丹东", [
  {"id":"DD001","name":"鸭绿江断桥","location":{"longitude":124.386,"latitude":40.114,"address":"丹东市振兴区江岸路0号","city":"丹东"},"rating":4.5,"durationMin":90,"ticketPrice":30.0,"tags":["抗美援朝","断桥","鸭绿江"]},
  {"id":"DD002","name":"凤凰山","location":{"longitude":124.078,"latitude":40.427,"address":"丹东市凤城市凤凰山","city":"丹东"},"rating":4.4,"durationMin":300,"ticketPrice":80.0,"tags":["登山","自然","险峻"]},
  {"id":"DD003","name":"抗美援朝纪念馆","location":{"longitude":124.377,"latitude":40.120,"address":"丹东市振兴区山上街7号","city":"丹东"},"rating":4.6,"durationMin":150,"ticketPrice":0.0,"tags":["红色","抗美援朝","爱国","免费"]},
  {"id":"DD004","name":"虎山长城","location":{"longitude":124.515,"latitude":40.225,"address":"丹东市宽甸县虎山镇","city":"丹东"},"rating":4.3,"durationMin":180,"ticketPrice":60.0,"tags":["长城","明长城东端","自然"]},
  {"id":"DD005","name":"鸭绿江公园","location":{"longitude":124.386,"latitude":40.114,"address":"丹东市振兴区滨江中路","city":"丹东"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["公园","鸭绿江","免费"]},
  {"id":"DD006","name":"锦江山公园","location":{"longitude":124.375,"latitude":40.128,"address":"丹东市振兴区锦江山","city":"丹东"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["公园","登山","城市全景","免费"]},
  {"id":"DD007","name":"宽甸天桥沟","location":{"longitude":124.680,"latitude":41.044,"address":"丹东市宽甸县双山子镇","city":"丹东"},"rating":4.3,"durationMin":300,"ticketPrice":120.0,"tags":["自然","滑雪","枫叶"]},
  {"id":"DD008","name":"绿江村","location":{"longitude":125.338,"latitude":40.985,"address":"丹东市宽甸县振江镇绿江村","city":"丹东"},"rating":4.5,"durationMin":480,"ticketPrice":0.0,"tags":["古村落","油菜花","鸭绿江","免费"]},
  {"id":"DD009","name":"中朝友谊桥","location":{"longitude":124.386,"latitude":40.114,"address":"丹东市振兴区","city":"丹东"},"rating":4.3,"durationMin":30,"ticketPrice":0.0,"tags":["中朝友谊","地标","免费"]},
  {"id":"DD010","name":"河口景区","location":{"longitude":124.750,"latitude":40.352,"address":"丹东市宽甸县长甸镇河口村","city":"丹东"},"rating":4.2,"durationMin":240,"ticketPrice":50.0,"tags":["桃花","鸭绿江","边境"]},
  {"id":"DD011","name":"丹东博物馆","location":{"longitude":124.380,"latitude":40.125,"address":"丹东市振兴区","city":"丹东"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"DD012","name":"鸭绿江游船","location":{"longitude":124.386,"latitude":40.114,"address":"丹东市振兴区鸭绿江码头","city":"丹东"},"rating":4.3,"durationMin":90,"ticketPrice":80.0,"tags":["游船","鸭绿江","边境"]},
  {"id":"DD013","name":"五龙山","location":{"longitude":124.265,"latitude":40.195,"address":"丹东市振安区五龙背镇","city":"丹东"},"rating":4.1,"durationMin":180,"ticketPrice":50.0,"tags":["登山","自然","佛教"]},
  {"id":"DD014","name":"五龙背温泉","location":{"longitude":124.265,"latitude":40.195,"address":"丹东市振安区五龙背镇","city":"丹东"},"rating":4.2,"durationMin":240,"ticketPrice":98.0,"tags":["温泉","养生"]},
  {"id":"DD015","name":"青山沟","location":{"longitude":125.114,"latitude":40.962,"address":"丹东市宽甸县青山沟镇","city":"丹东"},"rating":4.3,"durationMin":300,"ticketPrice":100.0,"tags":["自然","瀑布","森林"]},
  {"id":"DD016","name":"黄椅山火山森林公园","location":{"longitude":124.833,"latitude":40.733,"address":"丹东市宽甸县黄椅山","city":"丹东"},"rating":4.1,"durationMin":240,"ticketPrice":60.0,"tags":["火山","森林","自然"]},
  {"id":"DD017","name":"宽甸花脖山","location":{"longitude":125.005,"latitude":41.104,"address":"丹东市宽甸县牛毛坞镇","city":"丹东"},"rating":4.1,"durationMin":300,"ticketPrice":70.0,"tags":["登山","枫叶","自然"]},
  {"id":"DD018","name":"元宝山公园","location":{"longitude":124.393,"latitude":40.131,"address":"丹东市元宝区","city":"丹东"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"DD019","name":"丹东老街","location":{"longitude":124.392,"latitude":40.122,"address":"丹东市元宝区","city":"丹东"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["历史街区","美食","免费"]},
  {"id":"DD020","name":"安东老街","location":{"longitude":124.392,"latitude":40.119,"address":"丹东市振兴区","city":"丹东"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["民国风情","美食","夜市","免费"]},
  {"id":"DD021","name":"鸭绿江湿地观鸟","location":{"longitude":124.300,"latitude":40.020,"address":"丹东市东港市鸭绿江口湿地","city":"丹东"},"rating":4.2,"durationMin":180,"ticketPrice":0.0,"tags":["湿地","候鸟","免费"]},
  {"id":"DD022","name":"大鹿岛","location":{"longitude":123.924,"latitude":39.763,"address":"丹东市东港市孤山镇大鹿岛","city":"丹东"},"rating":4.2,"durationMin":480,"ticketPrice":0.0,"tags":["海岛","甲午海战","海滩","免费"]},
  {"id":"DD023","name":"獐岛","location":{"longitude":123.878,"latitude":39.814,"address":"丹东市东港市北井子镇獐岛","city":"丹东"},"rating":4.2,"durationMin":480,"ticketPrice":0.0,"tags":["海岛","海鲜","日出","免费"]},
  {"id":"DD024","name":"东汤温泉","location":{"longitude":124.246,"latitude":40.589,"address":"丹东市凤城市东汤镇","city":"丹东"},"rating":4.2,"durationMin":240,"ticketPrice":108.0,"tags":["温泉","养生"]},
  {"id":"DD025","name":"蒲石河森林公园","location":{"longitude":124.013,"latitude":40.757,"address":"丹东市凤城市赛马镇","city":"丹东"},"rating":4.2,"durationMin":240,"ticketPrice":80.0,"tags":["森林","枫叶","自然"]},
  {"id":"DD026","name":"九水峡漂流","location":{"longitude":124.378,"latitude":40.301,"address":"丹东市宽甸县虎山镇","city":"丹东"},"rating":4.1,"durationMin":180,"ticketPrice":128.0,"tags":["漂流","夏季"]},
  {"id":"DD027","name":"大梨树生态旅游区","location":{"longitude":124.048,"latitude":40.415,"address":"丹东市凤城市大梨树村","city":"丹东"},"rating":4.2,"durationMin":240,"ticketPrice":70.0,"tags":["生态","农业","社会主义新农村"]},
  {"id":"DD028","name":"丹东市图书馆","location":{"longitude":124.380,"latitude":40.128,"address":"丹东市振兴区","city":"丹东"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["图书馆","免费"]},
  {"id":"DD029","name":"丹东舰爱国主义教育基地","location":{"longitude":124.386,"latitude":40.114,"address":"丹东市振兴区鸭绿江畔","city":"丹东"},"rating":4.1,"durationMin":60,"ticketPrice":30.0,"tags":["军事","爱国","军舰"]},
  {"id":"DD030","name":"珍珠公园","location":{"longitude":124.390,"latitude":40.126,"address":"丹东市振安区","city":"丹东"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"DD031","name":"鸭绿江大道","location":{"longitude":124.386,"latitude":40.114,"address":"丹东市振兴区鸭绿江大道","city":"丹东"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["骑行","散步","免费"]},
  {"id":"DD032","name":"国门公园","location":{"longitude":124.386,"latitude":40.114,"address":"丹东市振兴区","city":"丹东"},"rating":4.0,"durationMin":30,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"DD033","name":"凤城大梨树影视城","location":{"longitude":124.048,"latitude":40.415,"address":"丹东市凤城市大梨树村","city":"丹东"},"rating":4.0,"durationMin":90,"ticketPrice":70.0,"tags":["影视城"]},
  {"id":"DD034","name":"宽甸青山湖","location":{"longitude":125.114,"latitude":40.962,"address":"丹东市宽甸县青山沟镇","city":"丹东"},"rating":4.2,"durationMin":180,"ticketPrice":100.0,"tags":["湖泊","自然","山水"]},
  {"id":"DD035","name":"丹东市美术馆","location":{"longitude":124.380,"latitude":40.125,"address":"丹东市振兴区","city":"丹东"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["美术馆","免费"]},
  {"id":"DD036","name":"鸭绿江美术馆","location":{"longitude":124.386,"latitude":40.114,"address":"丹东市振兴区月亮岛","city":"丹东"},"rating":4.2,"durationMin":90,"ticketPrice":30.0,"tags":["当代艺术","月亮岛"]},
  {"id":"DD037","name":"月亮岛","location":{"longitude":124.387,"latitude":40.111,"address":"丹东市振兴区月亮岛","city":"丹东"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["岛屿","休闲","免费"]},
  {"id":"DD038","name":"丹东夜市（福春街）","location":{"longitude":124.383,"latitude":40.117,"address":"丹东市振兴区福春街","city":"丹东"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["夜市","海鲜","美食","免费"]},
  {"id":"DD039","name":"朝鲜族冷面店集中区","location":{"longitude":124.391,"latitude":40.121,"address":"丹东市振兴区","city":"丹东"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["朝鲜族美食","冷面","免费"]},
  {"id":"DD040","name":"丹东黄蚬子美食","location":{"longitude":124.386,"latitude":40.114,"address":"丹东市振兴区沿江","city":"丹东"},"rating":4.3,"durationMin":90,"ticketPrice":0.0,"tags":["海鲜","黄蚬子","免费"]},
  {"id":"DD041","name":"帽盔山公园","location":{"longitude":124.370,"latitude":40.113,"address":"丹东市振兴区","city":"丹东"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["公园","登山","免费"]},
  {"id":"DD042","name":"青年湖公园","location":{"longitude":124.383,"latitude":40.122,"address":"丹东市振兴区","city":"丹东"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"DD043","name":"汤池温泉","location":{"longitude":124.310,"latitude":40.110,"address":"丹东市振兴区汤池镇","city":"丹东"},"rating":4.1,"durationMin":240,"ticketPrice":88.0,"tags":["温泉"]},
  {"id":"DD044","name":"高丽街","location":{"longitude":124.392,"latitude":40.120,"address":"丹东市振兴区","city":"丹东"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["韩式","购物","免费"]},
  {"id":"DD045","name":"丹东体育馆","location":{"longitude":124.385,"latitude":40.127,"address":"丹东市振兴区","city":"丹东"},"rating":4.0,"durationMin":120,"ticketPrice":80.0,"tags":["体育"]},
  {"id":"DD046","name":"中朝边境一步跨","location":{"longitude":124.515,"latitude":40.225,"address":"丹东市宽甸县虎山镇","city":"丹东"},"rating":4.2,"durationMin":30,"ticketPrice":0.0,"tags":["边境","中朝","免费"]},
  {"id":"DD047","name":"九连城古城遗址","location":{"longitude":124.440,"latitude":40.168,"address":"丹东市振安区九连城镇","city":"丹东"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["遗址","辽金","免费"]},
  {"id":"DD048","name":"宽甸百瀑峡","location":{"longitude":124.750,"latitude":41.020,"address":"丹东市宽甸县","city":"丹东"},"rating":4.2,"durationMin":240,"ticketPrice":60.0,"tags":["瀑布","自然","森林"]},
  {"id":"DD049","name":"凤城赛马天锅古洞","location":{"longitude":124.038,"latitude":40.740,"address":"丹东市凤城市赛马镇","city":"丹东"},"rating":4.1,"durationMin":120,"ticketPrice":60.0,"tags":["溶洞","自然"]},
  {"id":"DD050","name":"满族风情园","location":{"longitude":124.383,"latitude":40.119,"address":"丹东市振兴区","city":"丹东"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["满族文化","民俗","免费"]},
])

total = sum(len(c['attractions']) for c in data['cities'])
with open('huazai-trip-skills/src/main/resources/attractions/knowledge-base.json','w',encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

print(f"\n总计: {len(data['cities'])} 城市, {total} 景点")
print("Done batch 16")
