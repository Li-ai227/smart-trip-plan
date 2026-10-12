import json

# 10 个全新的旅游城市，每个 50 个热门景点
NEW = {}
C = 0

def add(city, attractions):
    global C
    NEW[city] = attractions
    C += len(attractions)
    print(f"  {city}: {len(attractions)} 景点")

# ===== 郑州 =====
add("郑州", [
  {"id":"ZZ001","name":"嵩山少林寺","location":{"longitude":112.937,"latitude":34.509,"address":"郑州市登封市嵩山少林寺","city":"郑州"},"rating":4.7,"durationMin":300,"ticketPrice":100.0,"tags":["佛教","武术","世界遗产"]},
  {"id":"ZZ002","name":"河南博物院","location":{"longitude":113.666,"latitude":34.789,"address":"郑州市金水区农业路8号","city":"郑州"},"rating":4.6,"durationMin":180,"ticketPrice":0.0,"tags":["博物馆","历史文化","免费"]},
  {"id":"ZZ003","name":"黄河风景名胜区","location":{"longitude":113.522,"latitude":34.948,"address":"郑州市惠济区黄河大堤","city":"郑州"},"rating":4.4,"durationMin":240,"ticketPrice":60.0,"tags":["黄河","自然","炎黄文化"]},
  {"id":"ZZ004","name":"嵩阳书院","location":{"longitude":112.987,"latitude":34.468,"address":"郑州市登封市嵩山南麓","city":"郑州"},"rating":4.3,"durationMin":120,"ticketPrice":80.0,"tags":["历史文化","儒家","宋代四大书院"]},
  {"id":"ZZ005","name":"中岳庙","location":{"longitude":113.015,"latitude":34.458,"address":"郑州市登封市嵩山太室山","city":"郑州"},"rating":4.3,"durationMin":150,"ticketPrice":80.0,"tags":["道教","古建筑","世界遗产"]},
  {"id":"ZZ006","name":"郑州二七纪念塔","location":{"longitude":113.665,"latitude":34.753,"address":"郑州市二七区二七广场","city":"郑州"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["地标","爱国主义","免费"]},
  {"id":"ZZ007","name":"嵩岳寺塔","location":{"longitude":112.965,"latitude":34.496,"address":"郑州市登封市嵩山","city":"郑州"},"rating":4.3,"durationMin":60,"ticketPrice":80.0,"tags":["古建筑","北魏","世界遗产"]},
  {"id":"ZZ008","name":"黄帝故里","location":{"longitude":113.739,"latitude":34.397,"address":"郑州市新郑市轩辕路1号","city":"郑州"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["历史文化","炎黄文化","免费"]},
  {"id":"ZZ009","name":"郑州植物园","location":{"longitude":113.547,"latitude":34.753,"address":"郑州市中原区中原西路","city":"郑州"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["植物","自然","免费"]},
  {"id":"ZZ010","name":"康百万庄园","location":{"longitude":112.993,"latitude":34.743,"address":"郑州市巩义市康店镇","city":"郑州"},"rating":4.2,"durationMin":120,"ticketPrice":75.0,"tags":["历史文化","清代民居","豫商"]},
  {"id":"ZZ011","name":"郑州博物馆","location":{"longitude":113.631,"latitude":34.754,"address":"郑州市中原区嵩山南路168号","city":"郑州"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["博物馆","历史文化","免费"]},
  {"id":"ZZ012","name":"郑州动物园","location":{"longitude":113.693,"latitude":34.796,"address":"郑州市金水区花园路","city":"郑州"},"rating":4.1,"durationMin":180,"ticketPrice":30.0,"tags":["动物园","亲子"]},
  {"id":"ZZ013","name":"郑州方特欢乐世界","location":{"longitude":113.899,"latitude":34.727,"address":"郑州市中牟县郑开大道","city":"郑州"},"rating":4.3,"durationMin":480,"ticketPrice":280.0,"tags":["主题乐园","亲子"]},
  {"id":"ZZ014","name":"商城遗址","location":{"longitude":113.682,"latitude":34.748,"address":"郑州市管城区城南路","city":"郑州"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["历史文化","商代","遗址","免费"]},
  {"id":"ZZ015","name":"世纪欢乐园","location":{"longitude":113.732,"latitude":34.730,"address":"郑州市管城区石化路1号","city":"郑州"},"rating":4.0,"durationMin":300,"ticketPrice":160.0,"tags":["主题乐园","亲子"]},
  {"id":"ZZ016","name":"北宋皇陵","location":{"longitude":112.992,"latitude":34.687,"address":"郑州市巩义市杜甫路","city":"郑州"},"rating":4.1,"durationMin":120,"ticketPrice":30.0,"tags":["历史文化","宋代","皇陵"]},
  {"id":"ZZ017","name":"郑州海洋馆","location":{"longitude":113.645,"latitude":34.801,"address":"郑州市金水区国基路","city":"郑州"},"rating":4.2,"durationMin":180,"ticketPrice":130.0,"tags":["海洋馆","亲子"]},
  {"id":"ZZ018","name":"郑州文庙","location":{"longitude":113.687,"latitude":34.745,"address":"郑州市管城区东大街","city":"郑州"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["儒家","古建筑","免费"]},
  {"id":"ZZ019","name":"始祖山风景区","location":{"longitude":113.601,"latitude":34.424,"address":"郑州市新郑市始祖山","city":"郑州"},"rating":4.1,"durationMin":180,"ticketPrice":30.0,"tags":["自然","登山","炎黄文化"]},
  {"id":"ZZ020","name":"郑州城隍庙","location":{"longitude":113.681,"latitude":34.751,"address":"郑州市管城区商城路","city":"郑州"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["道教","古建筑","免费"]},
  {"id":"ZZ021","name":"郑州科学技术馆","location":{"longitude":113.631,"latitude":34.735,"address":"郑州市中原区嵩山南路","city":"郑州"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["科普","亲子","免费"]},
  {"id":"ZZ022","name":"绿博园","location":{"longitude":113.930,"latitude":34.733,"address":"郑州市中牟县郑开大道","city":"郑州"},"rating":4.2,"durationMin":180,"ticketPrice":30.0,"tags":["园林","植物","花卉"]},
  {"id":"ZZ023","name":"郑州人民公园","location":{"longitude":113.657,"latitude":34.763,"address":"郑州市二七区铭功路","city":"郑州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["公园","休闲","免费"]},
  {"id":"ZZ024","name":"青龙山慈云寺","location":{"longitude":113.095,"latitude":34.647,"address":"郑州市巩义市民权村","city":"郑州"},"rating":4.1,"durationMin":120,"ticketPrice":40.0,"tags":["佛教","古刹","自然"]},
  {"id":"ZZ025","name":"嵩山滑雪场","location":{"longitude":112.928,"latitude":34.477,"address":"郑州市登封市嵩山","city":"郑州"},"rating":4.1,"durationMin":240,"ticketPrice":180.0,"tags":["滑雪","冬季","户外"]},
  {"id":"ZZ026","name":"河南省地质博物馆","location":{"longitude":113.740,"latitude":34.777,"address":"郑州市郑东新区金水东路18号","city":"郑州"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["博物馆","地质","恐龙","免费"]},
  {"id":"ZZ027","name":"如意湖","location":{"longitude":113.736,"latitude":34.781,"address":"郑州市郑东新区CBD","city":"郑州"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["湖泊","城市风光","免费"]},
  {"id":"ZZ028","name":"郑州雕塑公园","location":{"longitude":113.600,"latitude":34.786,"address":"郑州市中原区化工路","city":"郑州"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["公园","艺术","免费"]},
  {"id":"ZZ029","name":"河南省艺术中心","location":{"longitude":113.737,"latitude":34.780,"address":"郑州市郑东新区商务内环路","city":"郑州"},"rating":4.2,"durationMin":120,"ticketPrice":100.0,"tags":["艺术","演出","现代建筑"]},
  {"id":"ZZ030","name":"新密伏羲大峡谷","location":{"longitude":113.378,"latitude":34.518,"address":"郑州市新密市伏羲山","city":"郑州"},"rating":4.3,"durationMin":240,"ticketPrice":60.0,"tags":["峡谷","自然","漂流"]},
  {"id":"ZZ031","name":"黄河国家湿地公园","location":{"longitude":113.661,"latitude":34.926,"address":"郑州市惠济区黄河大堤","city":"郑州"},"rating":4.2,"durationMin":180,"ticketPrice":0.0,"tags":["湿地","自然","候鸟","免费"]},
  {"id":"ZZ032","name":"郑州市森林公园","location":{"longitude":113.743,"latitude":34.797,"address":"郑州市郑东新区龙湖内环路","city":"郑州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["公园","森林","免费"]},
  {"id":"ZZ033","name":"碧沙岗公园","location":{"longitude":113.637,"latitude":34.757,"address":"郑州市中原区建设东路","city":"郑州"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["公园","休闲","免费"]},
  {"id":"ZZ034","name":"豫西抗日纪念馆","location":{"longitude":113.640,"latitude":34.750,"address":"郑州市中原区","city":"郑州"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["爱国主义","纪念馆","免费"]},
  {"id":"ZZ035","name":"郑州文博城","location":{"longitude":113.710,"latitude":34.755,"address":"郑州市金水区农业路","city":"郑州"},"rating":4.0,"durationMin":120,"ticketPrice":0.0,"tags":["文创","购物","免费"]},
  {"id":"ZZ036","name":"月季公园","location":{"longitude":113.633,"latitude":34.780,"address":"郑州市中原区嵩山北路","city":"郑州"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["公园","花卉","免费"]},
  {"id":"ZZ037","name":"大河村遗址博物馆","location":{"longitude":113.758,"latitude":34.845,"address":"郑州市金水区中州大道","city":"郑州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","新石器时代","免费"]},
  {"id":"ZZ038","name":"郑州纺织工业博物馆","location":{"longitude":113.651,"latitude":34.763,"address":"郑州市中原区棉纺路","city":"郑州"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["工业遗产","博物馆","免费"]},
  {"id":"ZZ039","name":"郑州紫荆山公园","location":{"longitude":113.687,"latitude":34.765,"address":"郑州市金水区金水路","city":"郑州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["公园","休闲","免费"]},
  {"id":"ZZ040","name":"古荥大运河文化区","location":{"longitude":113.543,"latitude":34.883,"address":"郑州市惠济区古荥镇","city":"郑州"},"rating":4.1,"durationMin":180,"ticketPrice":0.0,"tags":["运河","历史文化","免费"]},
  {"id":"ZZ041","name":"大观音寺","location":{"longitude":113.633,"latitude":34.781,"address":"郑州市金水区国基路","city":"郑州"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["佛教","古刹","免费"]},
  {"id":"ZZ042","name":"郑州绿谷公园","location":{"longitude":113.572,"latitude":34.782,"address":"郑州市高新技术开发区","city":"郑州"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["公园","休闲","免费"]},
  {"id":"ZZ043","name":"巩义石窟寺","location":{"longitude":112.982,"latitude":34.756,"address":"郑州市巩义市河洛镇","city":"郑州"},"rating":4.2,"durationMin":90,"ticketPrice":30.0,"tags":["石窟","佛教","北魏"]},
  {"id":"ZZ044","name":"郑州龙湖","location":{"longitude":113.741,"latitude":34.797,"address":"郑州市郑东新区龙湖","city":"郑州"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["湖泊","休闲","免费"]},
  {"id":"ZZ045","name":"银基国际旅游度假区","location":{"longitude":113.388,"latitude":34.502,"address":"郑州市新密市刘寨镇","city":"郑州"},"rating":4.4,"durationMin":480,"ticketPrice":218.0,"tags":["主题乐园","亲子","度假"]},
  {"id":"ZZ046","name":"中原福塔","location":{"longitude":113.741,"latitude":34.732,"address":"郑州市管城区航海东路","city":"郑州"},"rating":4.1,"durationMin":90,"ticketPrice":98.0,"tags":["电视塔","高空","城市全景"]},
  {"id":"ZZ047","name":"洞林湖风景区","location":{"longitude":113.467,"latitude":34.685,"address":"郑州市荥阳市贾峪镇","city":"郑州"},"rating":4.1,"durationMin":180,"ticketPrice":0.0,"tags":["湖泊","自然","免费"]},
  {"id":"ZZ048","name":"樱桃沟风景区","location":{"longitude":113.598,"latitude":34.668,"address":"郑州市二七区侯寨乡","city":"郑州"},"rating":4.2,"durationMin":180,"ticketPrice":30.0,"tags":["采摘","春季","亲子"]},
  {"id":"ZZ049","name":"郑州自然博物馆","location":{"longitude":113.695,"latitude":34.786,"address":"郑州市金水区文化路","city":"郑州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","自然","免费"]},
  {"id":"ZZ050","name":"登封观星台","location":{"longitude":113.074,"latitude":34.467,"address":"郑州市登封市告成镇","city":"郑州"},"rating":4.3,"durationMin":90,"ticketPrice":40.0,"tags":["古建筑","元代","天文","世界遗产"]},
])

# ===== 温州 =====
add("温州", [
  {"id":"WZ001","name":"雁荡山风景区","location":{"longitude":121.062,"latitude":28.355,"address":"温州市乐清市雁荡山","city":"温州"},"rating":4.6,"durationMin":480,"ticketPrice":160.0,"tags":["世界地质公园","自然","瀑布"]},
  {"id":"WZ002","name":"楠溪江","location":{"longitude":120.691,"latitude":28.153,"address":"温州市永嘉县楠溪江风景区","city":"温州"},"rating":4.5,"durationMin":360,"ticketPrice":80.0,"tags":["漂流","古村落","自然"]},
  {"id":"WZ003","name":"江心屿","location":{"longitude":120.652,"latitude":28.023,"address":"温州市鹿城区瓯江中","city":"温州"},"rating":4.3,"durationMin":150,"ticketPrice":30.0,"tags":["岛屿","古建筑","瓯江"]},
  {"id":"WZ004","name":"温州博物馆","location":{"longitude":120.682,"latitude":28.002,"address":"温州市鹿城区市府路491号","city":"温州"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["博物馆","历史文化","免费"]},
  {"id":"WZ005","name":"百丈漈瀑布","location":{"longitude":120.096,"latitude":27.856,"address":"温州市文成县百丈漈镇","city":"温州"},"rating":4.6,"durationMin":180,"ticketPrice":65.0,"tags":["瀑布","自然","中华第一高瀑"]},
  {"id":"WZ006","name":"洞头岛","location":{"longitude":121.158,"latitude":27.838,"address":"温州市洞头区","city":"温州"},"rating":4.3,"durationMin":300,"ticketPrice":0.0,"tags":["海岛","海滩","免费"]},
  {"id":"WZ007","name":"南麂岛","location":{"longitude":121.073,"latitude":27.462,"address":"温州市平阳县南麂镇","city":"温州"},"rating":4.5,"durationMin":600,"ticketPrice":100.0,"tags":["海岛","海洋保护区","自然"]},
  {"id":"WZ008","name":"泽雅风景区","location":{"longitude":120.469,"latitude":28.017,"address":"温州市瓯海区泽雅镇","city":"温州"},"rating":4.2,"durationMin":240,"ticketPrice":60.0,"tags":["自然","瀑布","古法造纸"]},
  {"id":"WZ009","name":"温州乐园","location":{"longitude":120.705,"latitude":28.009,"address":"温州市瓯海区茶山街道","city":"温州"},"rating":4.1,"durationMin":360,"ticketPrice":180.0,"tags":["主题乐园","亲子"]},
  {"id":"WZ010","name":"永昌堡","location":{"longitude":120.766,"latitude":27.910,"address":"温州市龙湾区永昌镇","city":"温州"},"rating":4.0,"durationMin":90,"ticketPrice":15.0,"tags":["古建筑","明代","抗倭"]},
  {"id":"WZ011","name":"灵峰景区（雁荡山）","location":{"longitude":121.063,"latitude":28.355,"address":"温州市乐清市雁荡山","city":"温州"},"rating":4.5,"durationMin":180,"ticketPrice":50.0,"tags":["自然","奇峰","夜景"]},
  {"id":"WZ012","name":"大龙湫瀑布","location":{"longitude":121.071,"latitude":28.374,"address":"温州市乐清市雁荡山","city":"温州"},"rating":4.6,"durationMin":120,"ticketPrice":50.0,"tags":["瀑布","自然","天下第一瀑"]},
  {"id":"WZ013","name":"灵岩景区","location":{"longitude":121.065,"latitude":28.360,"address":"温州市乐清市雁荡山","city":"温州"},"rating":4.4,"durationMin":150,"ticketPrice":50.0,"tags":["自然","古刹","飞渡表演"]},
  {"id":"WZ014","name":"方洞景区","location":{"longitude":121.060,"latitude":28.352,"address":"温州市乐清市雁荡山","city":"温州"},"rating":4.2,"durationMin":120,"ticketPrice":40.0,"tags":["自然","栈道","峡谷"]},
  {"id":"WZ015","name":"瑞安寨寮溪","location":{"longitude":120.412,"latitude":27.717,"address":"温州市瑞安市高楼镇","city":"温州"},"rating":4.2,"durationMin":240,"ticketPrice":80.0,"tags":["自然","瀑布","度假"]},
  {"id":"WZ016","name":"玉苍山","location":{"longitude":120.420,"latitude":27.499,"address":"温州市苍南县桥墩镇","city":"温州"},"rating":4.1,"durationMin":240,"ticketPrice":55.0,"tags":["自然","登山","奇石"]},
  {"id":"WZ017","name":"泰顺廊桥","location":{"longitude":119.888,"latitude":27.486,"address":"温州市泰顺县泗溪镇","city":"温州"},"rating":4.4,"durationMin":180,"ticketPrice":0.0,"tags":["古建筑","廊桥","世界遗产预备","免费"]},
  {"id":"WZ018","name":"渔寮沙滩","location":{"longitude":120.530,"latitude":27.297,"address":"温州市苍南县马站镇","city":"温州"},"rating":4.2,"durationMin":240,"ticketPrice":40.0,"tags":["海滩","自然","夏季"]},
  {"id":"WZ019","name":"温州动物园","location":{"longitude":120.650,"latitude":27.997,"address":"温州市瓯海区景山","city":"温州"},"rating":4.0,"durationMin":150,"ticketPrice":30.0,"tags":["动物园","亲子"]},
  {"id":"WZ020","name":"温州科技馆","location":{"longitude":120.688,"latitude":28.000,"address":"温州市鹿城区市府路","city":"温州"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["科普","亲子","免费"]},
  {"id":"WZ021","name":"五马街商业区","location":{"longitude":120.658,"latitude":28.013,"address":"温州市鹿城区五马街","city":"温州"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["购物","历史街区","美食","免费"]},
  {"id":"WZ022","name":"温州非遗馆","location":{"longitude":120.683,"latitude":28.006,"address":"温州市鹿城区","city":"温州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["非遗","文化体验","免费"]},
  {"id":"WZ023","name":"铜铃山森林公园","location":{"longitude":119.964,"latitude":27.824,"address":"温州市文成县铜铃山镇","city":"温州"},"rating":4.2,"durationMin":240,"ticketPrice":70.0,"tags":["森林","自然","瀑布"]},
  {"id":"WZ024","name":"乐清湾","location":{"longitude":121.056,"latitude":28.110,"address":"温州市乐清市乐清湾","city":"温州"},"rating":4.1,"durationMin":180,"ticketPrice":0.0,"tags":["海湾","海鲜","免费"]},
  {"id":"WZ025","name":"温州大学城","location":{"longitude":120.699,"latitude":27.922,"address":"温州市瓯海区茶山高教园区","city":"温州"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["校园","文化","免费"]},
  {"id":"WZ026","name":"乌岩岭自然保护区","location":{"longitude":119.712,"latitude":27.673,"address":"温州市泰顺县乌岩岭","city":"温州"},"rating":4.2,"durationMin":300,"ticketPrice":60.0,"tags":["自然保护区","黄腹角雉","原始森林"]},
  {"id":"WZ027","name":"温州世纪公园","location":{"longitude":120.698,"latitude":27.995,"address":"温州市鹿城区","city":"温州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["公园","休闲","免费"]},
  {"id":"WZ028","name":"天顶湖","location":{"longitude":120.076,"latitude":27.831,"address":"温州市文成县百丈漈镇","city":"温州"},"rating":4.2,"durationMin":120,"ticketPrice":40.0,"tags":["湖泊","自然","度假"]},
  {"id":"WZ029","name":"杨府山公园","location":{"longitude":120.720,"latitude":28.014,"address":"温州市鹿城区","city":"温州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["公园","登山","免费"]},
  {"id":"WZ030","name":"温州市图书馆","location":{"longitude":120.686,"latitude":28.002,"address":"温州市鹿城区市府路506号","city":"温州"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["图书馆","文化","免费"]},
  {"id":"WZ031","name":"仙岩风景区","location":{"longitude":120.672,"latitude":27.876,"address":"温州市瓯海区仙岩镇","city":"温州"},"rating":4.1,"durationMin":180,"ticketPrice":40.0,"tags":["自然","朱自清","梅雨潭"]},
  {"id":"WZ032","name":"温州乐园海洋馆","location":{"longitude":120.705,"latitude":28.009,"address":"温州市瓯海区茶山","city":"温州"},"rating":4.1,"durationMin":180,"ticketPrice":160.0,"tags":["海洋馆","亲子"]},
  {"id":"WZ033","name":"中雁荡山","location":{"longitude":120.987,"latitude":28.120,"address":"温州市乐清市白石镇","city":"温州"},"rating":4.2,"durationMin":240,"ticketPrice":50.0,"tags":["自然","登山","湖泊"]},
  {"id":"WZ034","name":"三垟湿地","location":{"longitude":120.722,"latitude":27.974,"address":"温州市瓯海区三垟街道","city":"温州"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["湿地","自然","免费"]},
  {"id":"WZ035","name":"温州美术馆","location":{"longitude":120.684,"latitude":28.000,"address":"温州市鹿城区","city":"温州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["美术馆","文化","免费"]},
  {"id":"WZ036","name":"苍南碗窑古村","location":{"longitude":120.360,"latitude":27.379,"address":"温州市苍南县桥墩镇碗窑村","city":"温州"},"rating":4.3,"durationMin":120,"ticketPrice":30.0,"tags":["古村落","陶瓷文化","清代"]},
  {"id":"WZ037","name":"南雁荡山","location":{"longitude":120.239,"latitude":27.629,"address":"温州市平阳县南雁镇","city":"温州"},"rating":4.2,"durationMin":240,"ticketPrice":40.0,"tags":["自然","儒释道","瀑布"]},
  {"id":"WZ038","name":"温州印象南塘","location":{"longitude":120.675,"latitude":27.999,"address":"温州市鹿城区南塘街","city":"温州"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["历史街区","美食","夜景","免费"]},
  {"id":"WZ039","name":"瑶溪风景区","location":{"longitude":120.769,"latitude":27.944,"address":"温州市龙湾区瑶溪镇","city":"温州"},"rating":4.1,"durationMin":150,"ticketPrice":0.0,"tags":["自然","溪流","免费"]},
  {"id":"WZ040","name":"温州钟书阁","location":{"longitude":120.688,"latitude":28.001,"address":"温州市鹿城区","city":"温州"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["书店","打卡","免费"]},
  {"id":"WZ041","name":"永嘉书院","location":{"longitude":120.687,"latitude":28.209,"address":"温州市永嘉县沙头镇","city":"温州"},"rating":4.3,"durationMin":150,"ticketPrice":50.0,"tags":["历史文化","书院","楠溪江"]},
  {"id":"WZ042","name":"四海山森林公园","location":{"longitude":120.782,"latitude":28.361,"address":"温州市永嘉县张溪乡","city":"温州"},"rating":4.1,"durationMin":180,"ticketPrice":40.0,"tags":["森林","自然","避暑"]},
  {"id":"WZ043","name":"温州米房创意园","location":{"longitude":120.672,"latitude":28.017,"address":"温州市鹿城区","city":"温州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["文创","美食","免费"]},
  {"id":"WZ044","name":"苍南福德湾","location":{"longitude":120.453,"latitude":27.340,"address":"温州市苍南县矾山镇福德湾村","city":"温州"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["古村落","矾矿文化","世界遗产预备","免费"]},
  {"id":"WZ045","name":"温州大剧院","location":{"longitude":120.688,"latitude":28.003,"address":"温州市鹿城区市府路","city":"温州"},"rating":4.2,"durationMin":120,"ticketPrice":120.0,"tags":["艺术","演出","现代建筑"]},
  {"id":"WZ046","name":"金溪水库风景区","location":{"longitude":120.557,"latitude":28.336,"address":"温州市永嘉县金溪镇","city":"温州"},"rating":4.1,"durationMin":180,"ticketPrice":0.0,"tags":["水库","自然","免费"]},
  {"id":"WZ047","name":"会昌河水上巴士","location":{"longitude":120.665,"latitude":28.018,"address":"温州市鹿城区会昌河","city":"温州"},"rating":4.1,"durationMin":60,"ticketPrice":15.0,"tags":["水上巴士","城市风光"]},
  {"id":"WZ048","name":"温州马鞍池公园","location":{"longitude":120.665,"latitude":28.008,"address":"温州市鹿城区马鞍池西路","city":"温州"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["公园","休闲","免费"]},
  {"id":"WZ049","name":"雁荡山灵峰夜景","location":{"longitude":121.063,"latitude":28.355,"address":"温州市乐清市雁荡山灵峰","city":"温州"},"rating":4.6,"durationMin":90,"ticketPrice":50.0,"tags":["夜景","奇峰","剪影"]},
  {"id":"WZ050","name":"平阳南雁会文书院","location":{"longitude":120.242,"latitude":27.634,"address":"温州市平阳县南雁镇","city":"温州"},"rating":4.1,"durationMin":90,"ticketPrice":40.0,"tags":["历史文化","宋代","理学"]},
])

# ===== 舟山 =====
add("舟山", [
  {"id":"ZS001","name":"普陀山风景区","location":{"longitude":122.390,"latitude":30.004,"address":"舟山市普陀区普陀山","city":"舟山"},"rating":4.8,"durationMin":600,"ticketPrice":160.0,"tags":["佛教","海岛","观音道场"]},
  {"id":"ZS002","name":"朱家尖南沙","location":{"longitude":122.438,"latitude":29.872,"address":"舟山市普陀区朱家尖南沙","city":"舟山"},"rating":4.5,"durationMin":300,"ticketPrice":75.0,"tags":["海滩","沙雕","自然"]},
  {"id":"ZS003","name":"东极岛","location":{"longitude":122.691,"latitude":30.192,"address":"舟山市普陀区东极镇","city":"舟山"},"rating":4.7,"durationMin":600,"ticketPrice":0.0,"tags":["海岛","后会无期","日出","免费"]},
  {"id":"ZS004","name":"嵊泗列岛","location":{"longitude":122.451,"latitude":30.716,"address":"舟山市嵊泗县","city":"舟山"},"rating":4.4,"durationMin":600,"ticketPrice":0.0,"tags":["海岛","渔村","免费"]},
  {"id":"ZS005","name":"桃花岛","location":{"longitude":122.288,"latitude":29.835,"address":"舟山市普陀区桃花镇","city":"舟山"},"rating":4.3,"durationMin":360,"ticketPrice":58.0,"tags":["海岛","金庸","射雕"]},
  {"id":"ZS006","name":"大青山国家公园","location":{"longitude":122.432,"latitude":29.872,"address":"舟山市普陀区朱家尖大青山","city":"舟山"},"rating":4.3,"durationMin":240,"ticketPrice":100.0,"tags":["自然","海岸","自驾"]},
  {"id":"ZS007","name":"舟山博物馆","location":{"longitude":122.207,"latitude":29.985,"address":"舟山市定海区海天大道610号","city":"舟山"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["博物馆","海洋文化","免费"]},
  {"id":"ZS008","name":"普陀山南海观音","location":{"longitude":122.386,"latitude":29.987,"address":"舟山市普陀区普陀山观音跳","city":"舟山"},"rating":4.8,"durationMin":90,"ticketPrice":160.0,"tags":["佛教","观音","地标"]},
  {"id":"ZS009","name":"法雨禅寺","location":{"longitude":122.393,"latitude":29.997,"address":"舟山市普陀区普陀山","city":"舟山"},"rating":4.6,"durationMin":90,"ticketPrice":160.0,"tags":["佛教","古刹","明代"]},
  {"id":"ZS010","name":"慧济禅寺","location":{"longitude":122.388,"latitude":30.005,"address":"舟山市普陀区普陀山佛顶山","city":"舟山"},"rating":4.5,"durationMin":90,"ticketPrice":160.0,"tags":["佛教","古刹","山顶"]},
  {"id":"ZS011","name":"不肯去观音院","location":{"longitude":122.391,"latitude":30.001,"address":"舟山市普陀区普陀山紫竹林","city":"舟山"},"rating":4.5,"durationMin":60,"ticketPrice":160.0,"tags":["佛教","观音","唐代"]},
  {"id":"ZS012","name":"普济禅寺","location":{"longitude":122.389,"latitude":30.005,"address":"舟山市普陀区普陀山","city":"舟山"},"rating":4.6,"durationMin":90,"ticketPrice":160.0,"tags":["佛教","古刹","普陀山主寺"]},
  {"id":"ZS013","name":"紫竹林","location":{"longitude":122.390,"latitude":30.001,"address":"舟山市普陀区普陀山紫竹林","city":"舟山"},"rating":4.4,"durationMin":60,"ticketPrice":160.0,"tags":["佛教","竹林","自然"]},
  {"id":"ZS014","name":"朱家尖观音文化苑","location":{"longitude":122.436,"latitude":29.870,"address":"舟山市普陀区朱家尖","city":"舟山"},"rating":4.3,"durationMin":180,"ticketPrice":45.0,"tags":["佛教","文化体验"]},
  {"id":"ZS015","name":"嵊山枸杞岛","location":{"longitude":122.774,"latitude":30.716,"address":"舟山市嵊泗县枸杞乡","city":"舟山"},"rating":4.6,"durationMin":600,"ticketPrice":0.0,"tags":["海岛","摄影","日出","免费"]},
  {"id":"ZS016","name":"沈家门渔港","location":{"longitude":122.306,"latitude":29.945,"address":"舟山市普陀区沈家门","city":"舟山"},"rating":4.3,"durationMin":90,"ticketPrice":0.0,"tags":["渔港","海鲜","夜市","免费"]},
  {"id":"ZS017","name":"定海古城","location":{"longitude":122.106,"latitude":30.019,"address":"舟山市定海区","city":"舟山"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["古城","历史文化","免费"]},
  {"id":"ZS018","name":"南沙沙雕艺术广场","location":{"longitude":122.438,"latitude":29.872,"address":"舟山市普陀区朱家尖","city":"舟山"},"rating":4.2,"durationMin":90,"ticketPrice":75.0,"tags":["沙雕","艺术","海滩"]},
  {"id":"ZS019","name":"朱家尖乌石塘","location":{"longitude":122.445,"latitude":29.880,"address":"舟山市普陀区朱家尖乌石塘","city":"舟山"},"rating":4.2,"durationMin":90,"ticketPrice":40.0,"tags":["海滩","乌石","自然"]},
  {"id":"ZS020","name":"岱山岛","location":{"longitude":122.204,"latitude":30.277,"address":"舟山市岱山县","city":"舟山"},"rating":4.2,"durationMin":480,"ticketPrice":0.0,"tags":["海岛","渔村","免费"]},
  {"id":"ZS021","name":"花鸟岛","location":{"longitude":122.682,"latitude":30.851,"address":"舟山市嵊泗县花鸟乡","city":"舟山"},"rating":4.6,"durationMin":600,"ticketPrice":0.0,"tags":["海岛","灯塔","荧光海","免费"]},
  {"id":"ZS022","name":"普陀山千步沙","location":{"longitude":122.392,"latitude":30.002,"address":"舟山市普陀区普陀山千步沙","city":"舟山"},"rating":4.4,"durationMin":120,"ticketPrice":160.0,"tags":["海滩","自然"]},
  {"id":"ZS023","name":"观音法界","location":{"longitude":122.439,"latitude":29.883,"address":"舟山市普陀区朱家尖香莲路","city":"舟山"},"rating":4.5,"durationMin":180,"ticketPrice":90.0,"tags":["佛教","文化","现代建筑"]},
  {"id":"ZS024","name":"舟山鸦片战争遗址公园","location":{"longitude":122.104,"latitude":30.017,"address":"舟山市定海区","city":"舟山"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["爱国主义","历史遗迹","免费"]},
  {"id":"ZS025","name":"东港海滨公园","location":{"longitude":122.320,"latitude":29.955,"address":"舟山市普陀区东港","city":"舟山"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["公园","海景","免费"]},
  {"id":"ZS026","name":"朱家尖白沙岛","location":{"longitude":122.471,"latitude":29.906,"address":"舟山市普陀区白沙乡","city":"舟山"},"rating":4.3,"durationMin":240,"ticketPrice":0.0,"tags":["海岛","海钓","小众","免费"]},
  {"id":"ZS027","name":"秀山岛滑泥公园","location":{"longitude":122.170,"latitude":30.178,"address":"舟山市岱山县秀山乡","city":"舟山"},"rating":4.2,"durationMin":180,"ticketPrice":80.0,"tags":["泥浴","亲子","海岛"]},
  {"id":"ZS028","name":"蚂蚁岛","location":{"longitude":122.271,"latitude":29.884,"address":"舟山市普陀区蚂蚁岛","city":"舟山"},"rating":4.1,"durationMin":240,"ticketPrice":0.0,"tags":["海岛","渔村","免费"]},
  {"id":"ZS029","name":"洛迦山","location":{"longitude":122.443,"latitude":29.974,"address":"舟山市普陀区洛迦山","city":"舟山"},"rating":4.3,"durationMin":240,"ticketPrice":160.0,"tags":["佛教","海岛","观音"]},
  {"id":"ZS030","name":"海山公园","location":{"longitude":122.105,"latitude":30.020,"address":"舟山市定海区","city":"舟山"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"ZS031","name":"衢山岛","location":{"longitude":122.310,"latitude":30.421,"address":"舟山市岱山县衢山镇","city":"舟山"},"rating":4.2,"durationMin":480,"ticketPrice":0.0,"tags":["海岛","风车","盐田","免费"]},
  {"id":"ZS032","name":"舟山海洋文化艺术中心","location":{"longitude":122.207,"latitude":29.984,"address":"舟山市定海区海天大道","city":"舟山"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["文化","艺术","免费"]},
  {"id":"ZS033","name":"白沙岛海钓基地","location":{"longitude":122.471,"latitude":29.906,"address":"舟山市普陀区白沙乡","city":"舟山"},"rating":4.2,"durationMin":300,"ticketPrice":0.0,"tags":["海钓","休闲","免费"]},
  {"id":"ZS034","name":"东海大峡谷","location":{"longitude":122.087,"latitude":30.051,"address":"舟山市定海区双桥镇","city":"舟山"},"rating":4.1,"durationMin":240,"ticketPrice":50.0,"tags":["峡谷","自然","漂流"]},
  {"id":"ZS035","name":"定海老街","location":{"longitude":122.108,"latitude":30.020,"address":"舟山市定海区中大街","city":"舟山"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["历史街区","美食","免费"]},
  {"id":"ZS036","name":"茶人谷","location":{"longitude":122.087,"latitude":30.051,"address":"舟山市定海区双桥镇","city":"舟山"},"rating":4.1,"durationMin":150,"ticketPrice":50.0,"tags":["茶园","自然","生态"]},
  {"id":"ZS037","name":"东沙古镇","location":{"longitude":122.145,"latitude":30.268,"address":"舟山市岱山县东沙镇","city":"舟山"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["古镇","渔文化","免费"]},
  {"id":"ZS038","name":"鹿栏晴沙","location":{"longitude":122.201,"latitude":30.284,"address":"舟山市岱山县岱东镇","city":"舟山"},"rating":4.1,"durationMin":180,"ticketPrice":30.0,"tags":["海滩","自然"]},
  {"id":"ZS039","name":"摩星山","location":{"longitude":122.198,"latitude":30.271,"address":"舟山市岱山县","city":"舟山"},"rating":4.0,"durationMin":120,"ticketPrice":30.0,"tags":["登山","佛教","自然"]},
  {"id":"ZS040","name":"舟山新城滨海公园","location":{"longitude":122.211,"latitude":29.991,"address":"舟山市定海区临城街道","city":"舟山"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["公园","海景","免费"]},
  {"id":"ZS041","name":"朱家尖国际游艇俱乐部","location":{"longitude":122.438,"latitude":29.882,"address":"舟山市普陀区朱家尖","city":"舟山"},"rating":4.1,"durationMin":120,"ticketPrice":500.0,"tags":["游艇","海上运动"]},
  {"id":"ZS042","name":"中大街海洋文化夜市","location":{"longitude":122.108,"latitude":30.020,"address":"舟山市定海区中大街","city":"舟山"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["夜市","海鲜","免费"]},
  {"id":"ZS043","name":"舟山名人馆","location":{"longitude":122.106,"latitude":30.018,"address":"舟山市定海区","city":"舟山"},"rating":4.0,"durationMin":60,"ticketPrice":0.0,"tags":["博物馆","名人","免费"]},
  {"id":"ZS044","name":"普陀山佛顶山索道","location":{"longitude":122.388,"latitude":30.004,"address":"舟山市普陀区普陀山","city":"舟山"},"rating":4.2,"durationMin":30,"ticketPrice":40.0,"tags":["索道","观光"]},
  {"id":"ZS045","name":"桃花峪景区","location":{"longitude":122.286,"latitude":29.828,"address":"舟山市普陀区桃花镇","city":"舟山"},"rating":4.2,"durationMin":120,"ticketPrice":58.0,"tags":["自然","海岸","射雕"]},
  {"id":"ZS046","name":"安期峰","location":{"longitude":122.285,"latitude":29.834,"address":"舟山市普陀区桃花镇","city":"舟山"},"rating":4.0,"durationMin":120,"ticketPrice":58.0,"tags":["登山","自然"]},
  {"id":"ZS047","name":"塔湾金沙","location":{"longitude":122.298,"latitude":29.828,"address":"舟山市普陀区桃花镇","city":"舟山"},"rating":4.1,"durationMin":120,"ticketPrice":58.0,"tags":["海滩","自然"]},
  {"id":"ZS048","name":"射雕英雄传旅游城","location":{"longitude":122.287,"latitude":29.831,"address":"舟山市普陀区桃花镇","city":"舟山"},"rating":4.0,"durationMin":90,"ticketPrice":50.0,"tags":["影视城","金庸","主题"]},
  {"id":"ZS049","name":"舟山普陀大剧院","location":{"longitude":122.315,"latitude":29.953,"address":"舟山市普陀区东港海洲路","city":"舟山"},"rating":4.1,"durationMin":120,"ticketPrice":100.0,"tags":["艺术","演出"]},
  {"id":"ZS050","name":"莲花岛雕塑公园","location":{"longitude":122.323,"latitude":29.956,"address":"舟山市普陀区东港街道","city":"舟山"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["雕塑","艺术","海景","免费"]},
])

print(f"\n总计新增: {len(NEW)} 个城市, {C} 个景点")

with open('huazai-trip-skills/src/main/resources/attractions/knowledge-base.json','r',encoding='utf-8') as f:
    data = json.load(f)

existing = {c['city'] for c in data['cities']}
print(f"已有城市: {len(existing)}")
print(f"新增城市: {set(NEW.keys()) - existing}")
print(f"扩充城市: {set(NEW.keys()) & existing}")

for city_name, attractions in NEW.items():
    if city_name in existing:
        for c in data['cities']:
            if c['city'] == city_name:
                c['attractions'].extend(attractions)
                break
    else:
        data['cities'].append({"city": city_name, "attractions": attractions})

total = sum(len(c['attractions']) for c in data['cities'])
with open('huazai-trip-skills/src/main/resources/attractions/knowledge-base.json','w',encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

print(f"操作完成: {len(data['cities'])} 城市, {total} 景点, 新增 {C} 景点")
print("Done batch 13: 郑州/温州/舟山")
