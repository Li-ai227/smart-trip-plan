import json

NEW = {
"北京": [
  {"id":"BJ051","name":"国家大剧院","location":{"longitude":116.389,"latitude":39.910,"address":"北京市西城区西长安街2号","city":"北京"},"rating":4.5,"durationMin":120,"ticketPrice":40.0,"tags":["艺术","现代建筑","演出"]},
  {"id":"BJ052","name":"北京汽车博物馆","location":{"longitude":116.312,"latitude":39.838,"address":"北京市丰台区南四环西路126号","city":"北京"},"rating":4.3,"durationMin":120,"ticketPrice":30.0,"tags":["博物馆","科技","亲子"]},
  {"id":"BJ053","name":"红螺寺","location":{"longitude":116.629,"latitude":40.376,"address":"北京市怀柔区红螺东路2号","city":"北京"},"rating":4.4,"durationMin":150,"ticketPrice":54.0,"tags":["宗教","古刹","自然"]},
  {"id":"BJ054","name":"青龙峡","location":{"longitude":116.674,"latitude":40.447,"address":"北京市怀柔区怀北镇大水峪村","city":"北京"},"rating":4.3,"durationMin":240,"ticketPrice":45.0,"tags":["峡谷","自然","户外"]},
  {"id":"BJ055","name":"延庆百里画廊","location":{"longitude":116.162,"latitude":40.547,"address":"北京市延庆区千家店镇","city":"北京"},"rating":4.5,"durationMin":300,"ticketPrice":0.0,"tags":["自然","自驾","免费"]},
],
"上海": [
  {"id":"SH051","name":"上海中心大厦观光厅","location":{"longitude":121.506,"latitude":31.235,"address":"上海市浦东新区陆家嘴银城中路501号","city":"上海"},"rating":4.6,"durationMin":60,"ticketPrice":180.0,"tags":["观光","高空","现代建筑"]},
  {"id":"SH052","name":"上海影视乐园","location":{"longitude":121.319,"latitude":31.064,"address":"上海市松江区车墩镇北松公路4915号","city":"上海"},"rating":4.2,"durationMin":180,"ticketPrice":80.0,"tags":["影视","民国风情","亲子"]},
  {"id":"SH053","name":"东平国家森林公园","location":{"longitude":121.482,"latitude":31.674,"address":"上海市崇明区东平镇","city":"上海"},"rating":4.3,"durationMin":240,"ticketPrice":70.0,"tags":["森林","自然","骑行"]},
  {"id":"SH054","name":"1933老场坊","location":{"longitude":121.492,"latitude":31.259,"address":"上海市虹口区溧阳路611号","city":"上海"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["文创","历史建筑","小众","免费"]},
  {"id":"SH055","name":"南翔古镇","location":{"longitude":121.317,"latitude":31.296,"address":"上海市嘉定区南翔镇","city":"上海"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["古镇","美食","免费"]},
],
"广州": [
  {"id":"GZ051","name":"海心沙亚运公园","location":{"longitude":113.325,"latitude":23.114,"address":"广州市天河区临江大道","city":"广州"},"rating":4.3,"durationMin":60,"ticketPrice":0.0,"tags":["公园","现代建筑","免费"]},
  {"id":"GZ052","name":"余荫山房","location":{"longitude":113.395,"latitude":22.996,"address":"广州市番禺区南村镇","city":"广州"},"rating":4.5,"durationMin":120,"ticketPrice":18.0,"tags":["园林","岭南文化","清代"]},
  {"id":"GZ053","name":"从化温泉","location":{"longitude":113.593,"latitude":23.542,"address":"广州市从化区温泉镇","city":"广州"},"rating":4.3,"durationMin":300,"ticketPrice":138.0,"tags":["温泉","休闲","度假"]},
  {"id":"GZ054","name":"广州艺术博物院","location":{"longitude":113.309,"latitude":23.146,"address":"广州市越秀区麓湖路13号","city":"广州"},"rating":4.4,"durationMin":120,"ticketPrice":0.0,"tags":["艺术","博物馆","免费"]},
  {"id":"GZ055","name":"南沙湿地公园","location":{"longitude":113.618,"latitude":22.715,"address":"广州市南沙区万顷沙镇","city":"广州"},"rating":4.3,"durationMin":180,"ticketPrice":50.0,"tags":["湿地","自然","候鸟"]},
],
"深圳": [
  {"id":"SZ051","name":"深圳湾万象城","location":{"longitude":113.944,"latitude":22.518,"address":"深圳市南山区科苑南路2888号","city":"深圳"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["购物","美食","免费"]},
  {"id":"SZ052","name":"观澜山水田园","location":{"longitude":114.058,"latitude":22.722,"address":"深圳市龙华区观澜街道","city":"深圳"},"rating":4.2,"durationMin":240,"ticketPrice":88.0,"tags":["田园","亲子","休闲"]},
  {"id":"SZ053","name":"深圳湾体育中心","location":{"longitude":113.950,"latitude":22.518,"address":"深圳市南山区滨海大道3001号","city":"深圳"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["体育","现代建筑","免费"]},
  {"id":"SZ054","name":"凤凰山森林公园","location":{"longitude":113.851,"latitude":22.681,"address":"深圳市宝安区福永街道","city":"深圳"},"rating":4.3,"durationMin":180,"ticketPrice":0.0,"tags":["登山","自然","免费"]},
  {"id":"SZ055","name":"深圳珠宝博物馆","location":{"longitude":114.124,"latitude":22.571,"address":"深圳市罗湖区水贝一路","city":"深圳"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","珠宝","免费"]},
],
"成都": [
  {"id":"CD051","name":"金沙遗址博物馆","location":{"longitude":104.011,"latitude":30.681,"address":"成都市青羊区金沙遗址路2号","city":"成都"},"rating":4.7,"durationMin":150,"ticketPrice":80.0,"tags":["博物馆","历史文化","商周"]},
  {"id":"CD052","name":"成都大熊猫繁育研究基地(扩建区)","location":{"longitude":104.149,"latitude":30.738,"address":"成都市成华区熊猫大道1375号","city":"成都"},"rating":4.8,"durationMin":240,"ticketPrice":55.0,"tags":["大熊猫","亲子","自然"]},
  {"id":"CD053","name":"青城后山","location":{"longitude":103.560,"latitude":30.910,"address":"成都市都江堰市青城山镇","city":"成都"},"rating":4.5,"durationMin":300,"ticketPrice":20.0,"tags":["登山","自然","道教"]},
  {"id":"CD054","name":"成都博物馆新馆","location":{"longitude":104.063,"latitude":30.660,"address":"成都市青羊区小河街1号","city":"成都"},"rating":4.6,"durationMin":150,"ticketPrice":0.0,"tags":["博物馆","历史文化","免费"]},
  {"id":"CD055","name":"洛带古镇","location":{"longitude":104.325,"latitude":30.637,"address":"成都市龙泉驿区洛带镇","city":"成都"},"rating":4.2,"durationMin":180,"ticketPrice":30.0,"tags":["古镇","客家文化","美食"]},
],
"昆明": [
  {"id":"KM004","name":"云南民族村","location":{"longitude":102.659,"latitude":24.968,"address":"昆明市西山区滇池路1310号","city":"昆明"},"rating":4.3,"durationMin":240,"ticketPrice":90.0,"tags":["民族文化","主题乐园","云南风情"]},
  {"id":"KM005","name":"金殿风景区","location":{"longitude":102.763,"latitude":25.084,"address":"昆明市盘龙区金殿路","city":"昆明"},"rating":4.2,"durationMin":120,"ticketPrice":30.0,"tags":["道教","历史文化","铜殿"]},
  {"id":"KM006","name":"大观楼","location":{"longitude":102.677,"latitude":25.029,"address":"昆明市西山区大观路284号","city":"昆明"},"rating":4.2,"durationMin":90,"ticketPrice":20.0,"tags":["历史文化","园林","滇池"]},
  {"id":"KM007","name":"翠湖公园","location":{"longitude":102.705,"latitude":25.048,"address":"昆明市五华区翠湖南路67号","city":"昆明"},"rating":4.3,"durationMin":90,"ticketPrice":0.0,"tags":["公园","湖泊","海鸥","免费"]},
  {"id":"KM008","name":"安宁温泉","location":{"longitude":102.464,"latitude":24.938,"address":"昆明市安宁市温泉镇","city":"昆明"},"rating":4.2,"durationMin":240,"ticketPrice":128.0,"tags":["温泉","休闲","度假"]},
  {"id":"KM009","name":"九乡风景区","location":{"longitude":103.370,"latitude":24.957,"address":"昆明市宜良县九乡彝族回族乡","city":"昆明"},"rating":4.4,"durationMin":240,"ticketPrice":90.0,"tags":["溶洞","峡谷","自然"]},
  {"id":"KM010","name":"昆明世博园","location":{"longitude":102.761,"latitude":25.071,"address":"昆明市盘龙区世博路10号","city":"昆明"},"rating":4.1,"durationMin":180,"ticketPrice":70.0,"tags":["园林","植物","世界园艺"]},
  {"id":"KM011","name":"云南铁路博物馆","location":{"longitude":102.720,"latitude":25.034,"address":"昆明市盘龙区北京路913号","city":"昆明"},"rating":4.1,"durationMin":90,"ticketPrice":10.0,"tags":["博物馆","铁路","历史文化"]},
  {"id":"KM012","name":"海埂公园","location":{"longitude":102.666,"latitude":24.964,"address":"昆明市西山区滇池路1318号","city":"昆明"},"rating":4.3,"durationMin":90,"ticketPrice":0.0,"tags":["公园","滇池","免费"]},
  {"id":"KM013","name":"滇池西山森林公园","location":{"longitude":102.631,"latitude":24.960,"address":"昆明市西山区滇池西岸","city":"昆明"},"rating":4.5,"durationMin":300,"ticketPrice":40.0,"tags":["登山","滇池","自然","石窟"]},
  {"id":"KM014","name":"云南陆军讲武堂","location":{"longitude":102.704,"latitude":25.050,"address":"昆明市五华区翠湖西路22号","city":"昆明"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["历史文化","爱国主义","免费"]},
  {"id":"KM015","name":"东川红土地","location":{"longitude":103.082,"latitude":25.908,"address":"昆明市东川区红土地镇","city":"昆明"},"rating":4.6,"durationMin":480,"ticketPrice":0.0,"tags":["自然","摄影","丹霞","免费"]},
  {"id":"KM016","name":"轿子雪山","location":{"longitude":102.816,"latitude":25.931,"address":"昆明市禄劝县轿子雪山风景区","city":"昆明"},"rating":4.4,"durationMin":360,"ticketPrice":65.0,"tags":["雪山","登山","自然"]},
  {"id":"KM017","name":"晋宁盘龙寺","location":{"longitude":102.658,"latitude":24.700,"address":"昆明市晋宁区盘龙山","city":"昆明"},"rating":4.1,"durationMin":120,"ticketPrice":15.0,"tags":["佛教","古刹","历史文化"]},
  {"id":"KM018","name":"石林-乃古石林","location":{"longitude":103.330,"latitude":24.750,"address":"昆明市石林县","city":"昆明"},"rating":4.3,"durationMin":180,"ticketPrice":175.0,"tags":["喀斯特","自然","世界遗产"]},
  {"id":"KM019","name":"昆明动物园","location":{"longitude":102.710,"latitude":25.046,"address":"昆明市五华区青年路92号","city":"昆明"},"rating":4.1,"durationMin":150,"ticketPrice":20.0,"tags":["亲子","动物园"]},
  {"id":"KM020","name":"黑龙潭公园","location":{"longitude":102.745,"latitude":25.116,"address":"昆明市盘龙区黑龙潭","city":"昆明"},"rating":4.2,"durationMin":120,"ticketPrice":15.0,"tags":["公园","古建筑","梅花"]},
  {"id":"KM021","name":"云南野生动物园","location":{"longitude":102.789,"latitude":25.085,"address":"昆明市盘龙区沣源路","city":"昆明"},"rating":4.2,"durationMin":240,"ticketPrice":100.0,"tags":["动物园","亲子","自然"]},
  {"id":"KM022","name":"海东湿地公园","location":{"longitude":102.737,"latitude":24.937,"address":"昆明市官渡区环湖东路","city":"昆明"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["湿地","自然","免费"]},
  {"id":"KM023","name":"圆通寺","location":{"longitude":102.711,"latitude":25.050,"address":"昆明市五华区圆通街30号","city":"昆明"},"rating":4.2,"durationMin":60,"ticketPrice":6.0,"tags":["佛教","古刹","历史文化"]},
  {"id":"KM024","name":"云南省博物馆新馆","location":{"longitude":102.670,"latitude":24.960,"address":"昆明市官渡区广福路6393号","city":"昆明"},"rating":4.4,"durationMin":150,"ticketPrice":0.0,"tags":["博物馆","历史文化","免费"]},
  {"id":"KM025","name":"安宁八街古镇","location":{"longitude":102.395,"latitude":24.946,"address":"昆明市安宁市八街镇","city":"昆明"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["古镇","历史文化","免费"]},
  {"id":"KM026","name":"宜良九乡张口洞","location":{"longitude":103.366,"latitude":24.953,"address":"昆明市宜良县九乡","city":"昆明"},"rating":4.1,"durationMin":120,"ticketPrice":90.0,"tags":["溶洞","自然"]},
  {"id":"KM027","name":"寻甸凤龙湾","location":{"longitude":103.207,"latitude":25.549,"address":"昆明市寻甸县","city":"昆明"},"rating":4.2,"durationMin":240,"ticketPrice":60.0,"tags":["湖泊","自然","度假"]},
  {"id":"KM028","name":"轿子仙宫溶洞","location":{"longitude":102.827,"latitude":25.944,"address":"昆明市禄劝县","city":"昆明"},"rating":4.1,"durationMin":90,"ticketPrice":50.0,"tags":["溶洞","自然","小众"]},
  {"id":"KM029","name":"云南省科技馆","location":{"longitude":102.706,"latitude":25.038,"address":"昆明市五华区翠湖西路1号","city":"昆明"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["科普","亲子","免费"]},
  {"id":"KM030","name":"西华园","location":{"longitude":102.674,"latitude":25.033,"address":"昆明市西山区西华路","city":"昆明"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["公园","园林","免费"]},
  {"id":"KM031","name":"昙华寺","location":{"longitude":102.739,"latitude":25.038,"address":"昆明市盘龙区昙华路533号","city":"昆明"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["佛教","古建筑","免费"]},
  {"id":"KM032","name":"昆明花鸟市场","location":{"longitude":102.706,"latitude":25.043,"address":"昆明市五华区甬道街","city":"昆明"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["购物","花鸟","市井文化","免费"]},
  {"id":"KM033","name":"禄丰恐龙谷","location":{"longitude":102.076,"latitude":25.138,"address":"楚雄州禄丰市恐龙山镇","city":"昆明"},"rating":4.3,"durationMin":240,"ticketPrice":120.0,"tags":["博物馆","恐龙","亲子"]},
  {"id":"KM034","name":"阳宗海旅游度假区","location":{"longitude":102.960,"latitude":24.967,"address":"昆明市宜良县阳宗海","city":"昆明"},"rating":4.2,"durationMin":240,"ticketPrice":0.0,"tags":["湖泊","度假","免费"]},
  {"id":"KM035","name":"石龙坝水电站博物馆","location":{"longitude":102.533,"latitude":25.045,"address":"昆明市西山区海口镇","city":"昆明"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["工业遗产","博物馆","免费"]},
  {"id":"KM036","name":"昆明瀑布公园","location":{"longitude":102.749,"latitude":25.121,"address":"昆明市盘龙区龙泉路","city":"昆明"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["公园","瀑布","免费"]},
  {"id":"KM037","name":"石林长湖","location":{"longitude":103.395,"latitude":24.710,"address":"昆明市石林县长湖镇","city":"昆明"},"rating":4.2,"durationMin":180,"ticketPrice":10.0,"tags":["湖泊","自然","露营"]},
  {"id":"KM038","name":"寻甸北大营草场","location":{"longitude":103.114,"latitude":25.526,"address":"昆明市寻甸县北大营","city":"昆明"},"rating":4.1,"durationMin":240,"ticketPrice":0.0,"tags":["草原","自然","免费"]},
  {"id":"KM039","name":"东寺塔与西寺塔","location":{"longitude":102.710,"latitude":25.033,"address":"昆明市西山区东寺街","city":"昆明"},"rating":4.1,"durationMin":30,"ticketPrice":0.0,"tags":["古建筑","唐代","历史文化","免费"]},
  {"id":"KM040","name":"滇池南岸沙滩公园","location":{"longitude":102.667,"latitude":24.804,"address":"昆明市晋宁区环湖南路","city":"昆明"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["沙滩","滇池","免费"]},
  {"id":"KM041","name":"西山龙门石窟","location":{"longitude":102.629,"latitude":24.959,"address":"昆明市西山区西山风景区内","city":"昆明"},"rating":4.5,"durationMin":120,"ticketPrice":40.0,"tags":["石窟","历史文化","登山"]},
  {"id":"KM042","name":"北大营万亩草场","location":{"longitude":103.042,"latitude":25.549,"address":"昆明市寻甸县河口镇","city":"昆明"},"rating":4.1,"durationMin":180,"ticketPrice":0.0,"tags":["草原","自然","露营","免费"]},
  {"id":"KM043","name":"安宁法华寺石窟","location":{"longitude":102.417,"latitude":24.991,"address":"昆明市安宁市连然街道","city":"昆明"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["石窟","佛教","免费"]},
  {"id":"KM044","name":"捞鱼河湿地公园","location":{"longitude":102.700,"latitude":24.916,"address":"昆明市呈贡区环湖东路","city":"昆明"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["湿地","自然","免费"]},
  {"id":"KM045","name":"呈贡斗南花市","location":{"longitude":102.787,"latitude":24.886,"address":"昆明市呈贡区斗南街道","city":"昆明"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["花卉","购物","亚洲最大花市","免费"]},
  {"id":"KM046","name":"官渡古镇","location":{"longitude":102.749,"latitude":24.973,"address":"昆明市官渡区官渡古镇","city":"昆明"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["古镇","历史文化","美食","免费"]},
  {"id":"KM047","name":"大观公园","location":{"longitude":102.677,"latitude":25.029,"address":"昆明市西山区大观路284号","city":"昆明"},"rating":4.3,"durationMin":150,"ticketPrice":26.0,"tags":["园林","湖泊","历史文化"]},
  {"id":"KM048","name":"昆明融创文旅城","location":{"longitude":102.663,"latitude":25.014,"address":"昆明市西山区碧鸡路2999号","city":"昆明"},"rating":4.2,"durationMin":300,"ticketPrice":188.0,"tags":["主题乐园","亲子","商业"]},
  {"id":"KM049","name":"昆明花之城","location":{"longitude":102.745,"latitude":25.088,"address":"昆明市盘龙区金瓦路8188号","city":"昆明"},"rating":4.1,"durationMin":120,"ticketPrice":50.0,"tags":["花卉","购物","温室"]},
  {"id":"KM050","name":"抚仙湖（昆明出发）","location":{"longitude":102.890,"latitude":24.521,"address":"云南省玉溪市澄江市抚仙湖","city":"昆明"},"rating":4.6,"durationMin":480,"ticketPrice":0.0,"tags":["湖泊","自然","度假","免费"]},
],
"丽江": [
  {"id":"LJ004","name":"玉龙雪山冰川公园","location":{"longitude":100.176,"latitude":27.098,"address":"丽江市玉龙县玉龙雪山景区","city":"丽江"},"rating":4.8,"durationMin":360,"ticketPrice":140.0,"tags":["雪山","冰川","登山"]},
  {"id":"LJ005","name":"束河古镇","location":{"longitude":100.207,"latitude":26.919,"address":"丽江市古城区束河街道","city":"丽江"},"rating":4.4,"durationMin":180,"ticketPrice":0.0,"tags":["古镇","茶马古道","慢生活","免费"]},
  {"id":"LJ006","name":"泸沽湖（云南侧）","location":{"longitude":100.785,"latitude":27.660,"address":"丽江市宁蒗县泸沽湖","city":"丽江"},"rating":4.7,"durationMin":600,"ticketPrice":100.0,"tags":["湖泊","摩梭文化","自然"]},
  {"id":"LJ007","name":"拉市海湿地","location":{"longitude":100.142,"latitude":26.863,"address":"丽江市玉龙县拉市镇","city":"丽江"},"rating":4.3,"durationMin":180,"ticketPrice":30.0,"tags":["湿地","候鸟","骑马"]},
  {"id":"LJ008","name":"白沙古镇","location":{"longitude":100.223,"latitude":26.945,"address":"丽江市玉龙县白沙镇","city":"丽江"},"rating":4.4,"durationMin":120,"ticketPrice":0.0,"tags":["古镇","纳西文化","白沙壁画","免费"]},
  {"id":"LJ009","name":"虎跳峡（丽江段）","location":{"longitude":100.136,"latitude":27.170,"address":"丽江市玉龙县龙蟠乡","city":"丽江"},"rating":4.6,"durationMin":180,"ticketPrice":65.0,"tags":["峡谷","金沙江","徒步"]},
  {"id":"LJ010","name":"蓝月谷","location":{"longitude":100.181,"latitude":27.098,"address":"丽江市玉龙县玉龙雪山脚下","city":"丽江"},"rating":4.7,"durationMin":120,"ticketPrice":140.0,"tags":["湖泊","雪山","摄影","蓝宝石"]},
  {"id":"LJ011","name":"老君山（黎明景区）","location":{"longitude":99.694,"latitude":26.853,"address":"丽江市玉龙县黎明乡","city":"丽江"},"rating":4.3,"durationMin":360,"ticketPrice":105.0,"tags":["丹霞","自然","登山"]},
  {"id":"LJ012","name":"黑龙潭公园","location":{"longitude":100.236,"latitude":26.887,"address":"丽江市古城区民主路1号","city":"丽江"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["公园","园林","雪山倒影","免费"]},
  {"id":"LJ013","name":"木府","location":{"longitude":100.235,"latitude":26.874,"address":"丽江市古城区光义街49号","city":"丽江"},"rating":4.3,"durationMin":90,"ticketPrice":40.0,"tags":["历史文化","纳西王宫","明代"]},
  {"id":"LJ014","name":"狮子山万古楼","location":{"longitude":100.233,"latitude":26.876,"address":"丽江市古城区狮子山","city":"丽江"},"rating":4.2,"durationMin":60,"ticketPrice":50.0,"tags":["观景","古建筑","古城全景"]},
  {"id":"LJ015","name":"文海","location":{"longitude":100.165,"latitude":26.984,"address":"丽江市玉龙县白沙镇文海村","city":"丽江"},"rating":4.3,"durationMin":180,"ticketPrice":0.0,"tags":["高山湖泊","自然","小众","免费"]},
  {"id":"LJ016","name":"指云寺","location":{"longitude":100.106,"latitude":26.844,"address":"丽江市玉龙县拉市镇","city":"丽江"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["佛教","古刹","免费"]},
  {"id":"LJ017","name":"玉水寨","location":{"longitude":100.190,"latitude":27.016,"address":"丽江市玉龙县白沙镇玉龙村","city":"丽江"},"rating":4.2,"durationMin":120,"ticketPrice":50.0,"tags":["纳西文化","东巴文化","自然"]},
  {"id":"LJ018","name":"长江第一湾","location":{"longitude":99.927,"latitude":26.893,"address":"丽江市玉龙县石鼓镇","city":"丽江"},"rating":4.3,"durationMin":90,"ticketPrice":0.0,"tags":["自然","金沙江","奇观","免费"]},
  {"id":"LJ019","name":"观音峡","location":{"longitude":100.186,"latitude":26.784,"address":"丽江市古城区七河镇","city":"丽江"},"rating":4.2,"durationMin":150,"ticketPrice":80.0,"tags":["峡谷","瀑布","自然"]},
  {"id":"LJ020","name":"宝山石头城","location":{"longitude":100.394,"latitude":27.488,"address":"丽江市玉龙县宝山乡","city":"丽江"},"rating":4.4,"durationMin":480,"ticketPrice":0.0,"tags":["古村落","悬崖之城","纳西文化","免费"]},
  {"id":"LJ021","name":"丽江宋城旅游区","location":{"longitude":100.221,"latitude":26.881,"address":"丽江市古城区玉泉路","city":"丽江"},"rating":4.1,"durationMin":180,"ticketPrice":280.0,"tags":["主题乐园","演出","千古情"]},
  {"id":"LJ022","name":"九子海","location":{"longitude":100.289,"latitude":27.023,"address":"丽江市玉龙县大具乡","city":"丽江"},"rating":4.2,"durationMin":180,"ticketPrice":0.0,"tags":["高山湖泊","自然","徒步","免费"]},
  {"id":"LJ023","name":"泸沽湖里格岛","location":{"longitude":100.781,"latitude":27.669,"address":"丽江市宁蒗县泸沽湖","city":"丽江"},"rating":4.8,"durationMin":240,"ticketPrice":100.0,"tags":["湖泊","摄影","摩梭文化"]},
  {"id":"LJ024","name":"东巴谷","location":{"longitude":100.208,"latitude":27.032,"address":"丽江市玉龙县","city":"丽江"},"rating":4.2,"durationMin":120,"ticketPrice":60.0,"tags":["东巴文化","峡谷","自然"]},
  {"id":"LJ025","name":"甘海子","location":{"longitude":100.213,"latitude":27.067,"address":"丽江市玉龙县玉龙雪山","city":"丽江"},"rating":4.3,"durationMin":60,"ticketPrice":140.0,"tags":["高山草甸","雪山","摄影"]},
  {"id":"LJ026","name":"石鼓镇","location":{"longitude":99.857,"latitude":26.832,"address":"丽江市玉龙县石鼓镇","city":"丽江"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["古镇","长江第一湾","红军","免费"]},
  {"id":"LJ027","name":"南溪湿地","location":{"longitude":100.210,"latitude":26.810,"address":"丽江市古城区","city":"丽江"},"rating":4.0,"durationMin":90,"ticketPrice":0.0,"tags":["湿地","自然","免费"]},
  {"id":"LJ028","name":"太安油菜花海","location":{"longitude":100.105,"latitude":26.834,"address":"丽江市玉龙县太安乡","city":"丽江"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["花海","春季","摄影","免费"]},
  {"id":"LJ029","name":"丽江市博物馆","location":{"longitude":100.245,"latitude":26.885,"address":"丽江市古城区教育路","city":"丽江"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","纳西文化","东巴文化","免费"]},
  {"id":"LJ030","name":"洛克故居","location":{"longitude":100.220,"latitude":26.942,"address":"丽江市玉龙县白沙镇玉湖村","city":"丽江"},"rating":4.1,"durationMin":60,"ticketPrice":15.0,"tags":["名人故居","历史文化","纳西学"]},
  {"id":"LJ031","name":"千龟山","location":{"longitude":99.695,"latitude":26.854,"address":"丽江市玉龙县黎明乡","city":"丽江"},"rating":4.4,"durationMin":240,"ticketPrice":105.0,"tags":["丹霞","自然","地质奇观"]},
  {"id":"LJ032","name":"孝尊木雕艺术馆","location":{"longitude":100.205,"latitude":26.915,"address":"丽江市玉龙县束河古镇","city":"丽江"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["木雕","非遗","文化","免费"]},
  {"id":"LJ033","name":"纳西饮食文化街","location":{"longitude":100.232,"latitude":26.875,"address":"丽江市古城区五一街","city":"丽江"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["美食","纳西美食","免费"]},
  {"id":"LJ034","name":"花马街","location":{"longitude":100.229,"latitude":26.885,"address":"丽江市古城区花马街","city":"丽江"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["美食","餐饮","夜市","免费"]},
  {"id":"LJ035","name":"玉湖村","location":{"longitude":100.220,"latitude":26.942,"address":"丽江市玉龙县白沙镇玉湖村","city":"丽江"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["古村落","纳西文化","雪山脚下","免费"]},
  {"id":"LJ036","name":"丽江古城-四方街","location":{"longitude":100.233,"latitude":26.873,"address":"丽江市古城区四方街","city":"丽江"},"rating":4.5,"durationMin":60,"ticketPrice":0.0,"tags":["古城","历史文化","夜市","免费"]},
  {"id":"LJ037","name":"云杉坪","location":{"longitude":100.215,"latitude":27.075,"address":"丽江市玉龙县玉龙雪山","city":"丽江"},"rating":4.4,"durationMin":120,"ticketPrice":140.0,"tags":["高山草甸","雪山","摄影"]},
  {"id":"LJ038","name":"牦牛坪","location":{"longitude":100.228,"latitude":27.085,"address":"丽江市玉龙县玉龙雪山","city":"丽江"},"rating":4.3,"durationMin":150,"ticketPrice":140.0,"tags":["高山草甸","牦牛","自然"]},
  {"id":"LJ039","name":"玉柱擎天","location":{"longitude":100.209,"latitude":26.979,"address":"丽江市玉龙县白沙镇","city":"丽江"},"rating":4.1,"durationMin":90,"ticketPrice":40.0,"tags":["自然","瀑布","登山"]},
  {"id":"LJ040","name":"大研古城大水车","location":{"longitude":100.232,"latitude":26.877,"address":"丽江市古城区大水车","city":"丽江"},"rating":4.4,"durationMin":30,"ticketPrice":0.0,"tags":["地标","古城","免费"]},
  {"id":"LJ041","name":"沱江鱼府（丽江美食体验）","location":{"longitude":100.230,"latitude":26.883,"address":"丽江市古城区祥和路","city":"丽江"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["美食","淡水鱼","火锅","免费"]},
  {"id":"LJ042","name":"金茂璞修雪山酒店观景台","location":{"longitude":100.215,"latitude":27.030,"address":"丽江市玉龙县甘海子","city":"丽江"},"rating":4.5,"durationMin":60,"ticketPrice":0.0,"tags":["观景","玉龙雪山","摄影","免费"]},
  {"id":"LJ043","name":"忠义市场","location":{"longitude":100.231,"latitude":26.869,"address":"丽江市古城区长水路","city":"丽江"},"rating":4.3,"durationMin":90,"ticketPrice":0.0,"tags":["菜市场","市井文化","美食","免费"]},
  {"id":"LJ044","name":"红太阳广场","location":{"longitude":100.228,"latitude":26.885,"address":"丽江市古城区民主路","city":"丽江"},"rating":4.0,"durationMin":30,"ticketPrice":0.0,"tags":["广场","市民生活","免费"]},
  {"id":"LJ045","name":"丽水金沙演艺","location":{"longitude":100.227,"latitude":26.881,"address":"丽江市古城区民主路","city":"丽江"},"rating":4.2,"durationMin":90,"ticketPrice":280.0,"tags":["演出","民族风情","纳西文化"]},
  {"id":"LJ046","name":"三眼井","location":{"longitude":100.233,"latitude":26.872,"address":"丽江市古城区光义街","city":"丽江"},"rating":4.1,"durationMin":30,"ticketPrice":0.0,"tags":["历史遗迹","纳西民俗","免费"]},
  {"id":"LJ047","name":"科贡坊","location":{"longitude":100.233,"latitude":26.874,"address":"丽江市古城区四方街旁","city":"丽江"},"rating":4.0,"durationMin":30,"ticketPrice":0.0,"tags":["古建筑","历史文化","免费"]},
  {"id":"LJ048","name":"文昌宫","location":{"longitude":100.234,"latitude":26.874,"address":"丽江市古城区狮子山","city":"丽江"},"rating":4.0,"durationMin":30,"ticketPrice":0.0,"tags":["道教","古建筑","免费"]},
  {"id":"LJ049","name":"普济寺","location":{"longitude":100.217,"latitude":26.887,"address":"丽江市古城区束河街道","city":"丽江"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["佛教","古刹","免费"]},
  {"id":"LJ050","name":"玉龙雪山4680米观景台","location":{"longitude":100.175,"latitude":27.097,"address":"丽江市玉龙县玉龙雪山景区","city":"丽江"},"rating":4.8,"durationMin":30,"ticketPrice":140.0,"tags":["雪山","观景","极限体验"]},
],
}

with open('huazai-trip-skills/src/main/resources/attractions/knowledge-base.json','r',encoding='utf-8') as f:
    data = json.load(f)

existing_cities = {c['city'] for c in data['cities']}
print(f"已存在城市: {len(existing_cities)}")

added = {}
for city_name, attractions in NEW.items():
    if city_name in existing_cities:
        for c in data['cities']:
            if c['city'] == city_name:
                c['attractions'].extend(attractions)
                added[city_name] = len(attractions)
                break
    else:
        new_entry = {
            "city": city_name,
            "attractions": attractions
        }
        data['cities'].append(new_entry)
        added[city_name] = len(attractions)

total_before = sum(len(c['attractions']) for c in data['cities'])
with open('huazai-trip-skills/src/main/resources/attractions/knowledge-base.json','w',encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

city_names = sorted([c['city'] for c in data['cities']])
print(f"城市总数: {len(city_names)}")
print(f"景点总数: {total_before}")
print(f"本批新增: {sum(added.values())} 个景点")
print(f"涉及城市: {list(added.keys())}")
print("Done batch 12")
