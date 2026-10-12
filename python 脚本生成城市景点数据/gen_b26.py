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

# b26: 惠州, 江门, 湛江, 铜仁, 林芝, 张掖(补), 武威

add("惠州", [
  {"id":"HUI001","name":"罗浮山","location":{"longitude":114.070,"latitude":23.240,"address":"惠州市博罗县长宁镇","city":"惠州"},"rating":4.5,"durationMin":360,"ticketPrice":60.0,"tags":["道教","自然","登山"]},
  {"id":"HUI002","name":"惠州西湖","location":{"longitude":114.391,"latitude":23.092,"address":"惠州市惠城区环城西二路","city":"惠州"},"rating":4.3,"durationMin":180,"ticketPrice":0.0,"tags":["湖泊","苏轼","免费"]},
  {"id":"HUI003","name":"巽寮湾","location":{"longitude":114.767,"latitude":22.670,"address":"惠州市惠东县巽寮湾","city":"惠州"},"rating":4.4,"durationMin":360,"ticketPrice":0.0,"tags":["海滩","度假","免费"]},
  {"id":"HUI004","name":"双月湾","location":{"longitude":114.886,"latitude":22.602,"address":"惠州市惠东县港口镇","city":"惠州"},"rating":4.5,"durationMin":360,"ticketPrice":0.0,"tags":["海滩","摄影","自然奇观","免费"]},
  {"id":"HUI005","name":"南昆山","location":{"longitude":114.006,"latitude":23.631,"address":"惠州市龙门县南昆山镇","city":"惠州"},"rating":4.3,"durationMin":360,"ticketPrice":30.0,"tags":["森林","避暑","温泉"]},
  {"id":"HUI006","name":"大亚湾","location":{"longitude":114.537,"latitude":22.729,"address":"惠州市惠阳区大亚湾","city":"惠州"},"rating":4.2,"durationMin":240,"ticketPrice":0.0,"tags":["海湾","海滩","免费"]},
  {"id":"HUI007","name":"叶挺故居","location":{"longitude":114.490,"latitude":22.985,"address":"惠州市惠阳区秋长镇","city":"惠州"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["红色","名人故居","免费"]},
  {"id":"HUI008","name":"惠州博物馆","location":{"longitude":114.391,"latitude":23.092,"address":"惠州市惠城区","city":"惠州"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"HUI009","name":"永记生态园","location":{"longitude":114.767,"latitude":22.670,"address":"惠州市惠东县大岭镇","city":"惠州"},"rating":4.1,"durationMin":240,"ticketPrice":80.0,"tags":["生态","亲子","农业"]},
  {"id":"HUI010","name":"红花湖","location":{"longitude":114.379,"latitude":23.075,"address":"惠州市惠城区红花湖路","city":"惠州"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["湖泊","骑行","免费"]},
])

add("江门", [
  {"id":"JIM001","name":"开平碉楼","location":{"longitude":112.574,"latitude":22.332,"address":"江门市开平市塘口镇","city":"江门"},"rating":4.6,"durationMin":300,"ticketPrice":78.0,"tags":["世界遗产","碉楼","华侨文化"]},
  {"id":"JIM002","name":"赤坎古镇","location":{"longitude":112.574,"latitude":22.332,"address":"江门市开平市赤坎镇","city":"江门"},"rating":4.4,"durationMin":180,"ticketPrice":60.0,"tags":["古镇","骑楼","华侨"]},
  {"id":"JIM003","name":"小鸟天堂","location":{"longitude":113.106,"latitude":22.424,"address":"江门市新会区会城天马村","city":"江门"},"rating":4.3,"durationMin":120,"ticketPrice":25.0,"tags":["自然","榕树","巴金"]},
  {"id":"JIM004","name":"圭峰山","location":{"longitude":113.030,"latitude":22.520,"address":"江门市新会区圭峰路","city":"江门"},"rating":4.2,"durationMin":180,"ticketPrice":0.0,"tags":["登山","自然","免费"]},
  {"id":"JIM005","name":"梁启超故居","location":{"longitude":113.082,"latitude":22.474,"address":"江门市新会区茶坑村","city":"江门"},"rating":4.2,"durationMin":90,"ticketPrice":10.0,"tags":["名人故居","近代"]},
  {"id":"JIM006","name":"川山群岛","location":{"longitude":112.780,"latitude":21.660,"address":"江门市台山市川岛镇","city":"江门"},"rating":4.3,"durationMin":480,"ticketPrice":148.0,"tags":["海岛","海滩","度假"]},
  {"id":"JIM007","name":"古劳水乡","location":{"longitude":112.939,"latitude":22.740,"address":"江门市鹤山市古劳镇","city":"江门"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["水乡","免费"]},
  {"id":"JIM008","name":"恩平温泉","location":{"longitude":112.303,"latitude":22.196,"address":"江门市恩平市","city":"江门"},"rating":4.2,"durationMin":240,"ticketPrice":128.0,"tags":["温泉","度假"]},
  {"id":"JIM009","name":"陈皮村","location":{"longitude":113.031,"latitude":22.519,"address":"江门市新会区","city":"江门"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["陈皮","美食","免费"]},
  {"id":"JIM010","name":"江门五邑华侨博物馆","location":{"longitude":113.084,"latitude":22.578,"address":"江门市蓬江区","city":"江门"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["博物馆","华侨","免费"]},
])

add("湛江", [
  {"id":"ZHJ001","name":"湖光岩","location":{"longitude":110.313,"latitude":21.143,"address":"湛江市麻章区湖光镇","city":"湛江"},"rating":4.4,"durationMin":180,"ticketPrice":50.0,"tags":["玛珥湖","世界地质公园","自然"]},
  {"id":"ZHJ002","name":"东海岛","location":{"longitude":110.523,"latitude":21.009,"address":"湛江市麻章区东海岛","city":"湛江"},"rating":4.3,"durationMin":360,"ticketPrice":30.0,"tags":["海岛","海滩","中国最长海滩"]},
  {"id":"ZHJ003","name":"金沙湾","location":{"longitude":110.384,"latitude":21.251,"address":"湛江市赤坎区金沙湾","city":"湛江"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["海滩","免费"]},
  {"id":"ZHJ004","name":"寸金桥公园","location":{"longitude":110.359,"latitude":21.272,"address":"湛江市赤坎区寸金路","city":"湛江"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["公园","免费"]},
  {"id":"ZHJ005","name":"硇洲岛","location":{"longitude":110.606,"latitude":20.915,"address":"湛江市麻章区硇洲镇","city":"湛江"},"rating":4.2,"durationMin":480,"ticketPrice":0.0,"tags":["海岛","灯塔","火山","免费"]},
  {"id":"ZHJ006","name":"特呈岛","location":{"longitude":110.449,"latitude":21.158,"address":"湛江市霞山区特呈岛","city":"湛江"},"rating":4.1,"durationMin":240,"ticketPrice":0.0,"tags":["海岛","渔村","免费"]},
  {"id":"ZHJ007","name":"雷州古城","location":{"longitude":110.088,"latitude":20.912,"address":"湛江市雷州市雷城","city":"湛江"},"rating":4.2,"durationMin":180,"ticketPrice":0.0,"tags":["古城","历史文化","免费"]},
  {"id":"ZHJ008","name":"湛江博物馆","location":{"longitude":110.359,"latitude":21.272,"address":"湛江市赤坎区南方路","city":"湛江"},"rating":4.1,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"ZHJ009","name":"徐闻南极村","location":{"longitude":109.997,"latitude":20.264,"address":"湛江市徐闻县角尾乡","city":"湛江"},"rating":4.3,"durationMin":240,"ticketPrice":0.0,"tags":["中国大陆最南端","灯塔","免费"]},
  {"id":"ZHJ010","name":"湛江海鲜市场","location":{"longitude":110.412,"latitude":21.195,"address":"湛江市霞山区东风市场","city":"湛江"},"rating":4.4,"durationMin":90,"ticketPrice":0.0,"tags":["海鲜","美食","免费"]},
])

add("铜仁", [
  {"id":"TRG001","name":"梵净山","location":{"longitude":108.686,"latitude":27.908,"address":"铜仁市江口县太平镇","city":"铜仁"},"rating":4.7,"durationMin":480,"ticketPrice":100.0,"tags":["世界遗产","佛教","自然"]},
  {"id":"TRG002","name":"九龙洞","location":{"longitude":109.182,"latitude":27.727,"address":"铜仁市碧江区漾头镇","city":"铜仁"},"rating":4.2,"durationMin":120,"ticketPrice":70.0,"tags":["溶洞","自然"]},
  {"id":"TRG003","name":"大明边城","location":{"longitude":109.182,"latitude":27.727,"address":"铜仁市碧江区","city":"铜仁"},"rating":4.1,"durationMin":120,"ticketPrice":80.0,"tags":["明代","历史文化","主题"]},
  {"id":"TRG004","name":"思南石林","location":{"longitude":108.250,"latitude":27.940,"address":"铜仁市思南县长坝镇","city":"铜仁"},"rating":4.2,"durationMin":180,"ticketPrice":60.0,"tags":["石林","喀斯特","自然"]},
  {"id":"TRG005","name":"亚木沟","location":{"longitude":108.686,"latitude":27.908,"address":"铜仁市江口县太平镇","city":"铜仁"},"rating":4.2,"durationMin":180,"ticketPrice":128.0,"tags":["峡谷","原始森林","自然"]},
  {"id":"TRG006","name":"石阡温泉","location":{"longitude":108.228,"latitude":27.516,"address":"铜仁市石阡县","city":"铜仁"},"rating":4.3,"durationMin":240,"ticketPrice":138.0,"tags":["温泉","千年","养生"]},
  {"id":"TRG007","name":"苗王城","location":{"longitude":109.182,"latitude":27.727,"address":"铜仁市松桃县正大镇","city":"铜仁"},"rating":4.2,"durationMin":120,"ticketPrice":70.0,"tags":["苗族","历史文化"]},
  {"id":"TRG008","name":"铜仁古城","location":{"longitude":109.182,"latitude":27.727,"address":"铜仁市碧江区","city":"铜仁"},"rating":4.1,"durationMin":120,"ticketPrice":0.0,"tags":["古城","免费"]},
  {"id":"TRG009","name":"悬崖酒店","location":{"longitude":109.182,"latitude":27.727,"address":"铜仁市碧江区九龙洞","city":"铜仁"},"rating":4.2,"durationMin":120,"ticketPrice":100.0,"tags":["悬崖","特色酒店"]},
  {"id":"TRG010","name":"天生桥峡谷","location":{"longitude":109.182,"latitude":27.727,"address":"铜仁市碧江区川硐镇","city":"铜仁"},"rating":4.2,"durationMin":180,"ticketPrice":80.0,"tags":["峡谷","自然"]},
])

add("林芝", [
  {"id":"LNZ001","name":"雅鲁藏布大峡谷","location":{"longitude":94.911,"latitude":29.564,"address":"林芝市米林县派镇","city":"林芝"},"rating":4.8,"durationMin":600,"ticketPrice":150.0,"tags":["世界最深峡谷","南迦巴瓦","自然"]},
  {"id":"LNZ002","name":"南迦巴瓦峰","location":{"longitude":95.055,"latitude":29.632,"address":"林芝市米林县","city":"林芝"},"rating":4.9,"durationMin":240,"ticketPrice":0.0,"tags":["雪山","中国最美山峰","免费"]},
  {"id":"LNZ003","name":"巴松措","location":{"longitude":93.981,"latitude":30.014,"address":"林芝市工布江达县巴河镇","city":"林芝"},"rating":4.6,"durationMin":240,"ticketPrice":120.0,"tags":["湖泊","雪山","自然"]},
  {"id":"LNZ004","name":"鲁朗林海","location":{"longitude":94.700,"latitude":29.650,"address":"林芝市巴宜区鲁朗镇","city":"林芝"},"rating":4.5,"durationMin":180,"ticketPrice":90.0,"tags":["森林","牧场","瑞士风光"]},
  {"id":"LNZ005","name":"桃花沟","location":{"longitude":94.362,"latitude":29.608,"address":"林芝市巴宜区嘎拉村","city":"林芝"},"rating":4.6,"durationMin":120,"ticketPrice":0.0,"tags":["桃花","春季","免费"]},
  {"id":"LNZ006","name":"尼洋河","location":{"longitude":94.362,"latitude":29.608,"address":"林芝市巴宜区","city":"林芝"},"rating":4.3,"durationMin":90,"ticketPrice":0.0,"tags":["河流","风光","免费"]},
  {"id":"LNZ007","name":"南伊沟","location":{"longitude":94.247,"latitude":29.128,"address":"林芝市米林县南伊乡","city":"林芝"},"rating":4.3,"durationMin":240,"ticketPrice":110.0,"tags":["原始森林","珞巴族","自然"]},
  {"id":"LNZ008","name":"米堆冰川","location":{"longitude":96.499,"latitude":29.549,"address":"林芝市波密县玉普乡","city":"林芝"},"rating":4.6,"durationMin":300,"ticketPrice":50.0,"tags":["冰川","自然","世界级"]},
  {"id":"LNZ009","name":"卡定沟","location":{"longitude":94.362,"latitude":29.608,"address":"林芝市巴宜区卡定沟","city":"林芝"},"rating":4.2,"durationMin":120,"ticketPrice":20.0,"tags":["瀑布","天佛","自然"]},
  {"id":"LNZ010","name":"色季拉山","location":{"longitude":94.637,"latitude":29.612,"address":"林芝市巴宜区","city":"林芝"},"rating":4.4,"durationMin":60,"ticketPrice":0.0,"tags":["观景","南迦巴瓦","免费"]},
])

add("武威", [
  {"id":"WWG001","name":"雷台汉墓","location":{"longitude":102.642,"latitude":37.928,"address":"武威市凉州区雷台东路","city":"武威"},"rating":4.4,"durationMin":120,"ticketPrice":45.0,"tags":["汉代","马踏飞燕","历史文化"]},
  {"id":"WWG002","name":"文庙","location":{"longitude":102.642,"latitude":37.928,"address":"武威市凉州区文庙路","city":"武威"},"rating":4.2,"durationMin":90,"ticketPrice":30.0,"tags":["儒家","明代","陇右学宫之冠"]},
  {"id":"WWG003","name":"白塔寺","location":{"longitude":102.592,"latitude":37.851,"address":"武威市凉州区武南镇","city":"武威"},"rating":4.2,"durationMin":90,"ticketPrice":20.0,"tags":["佛教","元代","西藏归属"]},
  {"id":"WWG004","name":"天梯山石窟","location":{"longitude":102.702,"latitude":37.697,"address":"武威市凉州区张义镇","city":"武威"},"rating":4.4,"durationMin":120,"ticketPrice":30.0,"tags":["石窟","佛教","石窟鼻祖"]},
  {"id":"WWG005","name":"武威博物馆","location":{"longitude":102.642,"latitude":37.928,"address":"武威市凉州区","city":"武威"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["博物馆","西夏","免费"]},
  {"id":"WWG006","name":"海藏寺","location":{"longitude":102.642,"latitude":37.928,"address":"武威市凉州区海藏路","city":"武威"},"rating":4.1,"durationMin":60,"ticketPrice":0.0,"tags":["佛教","免费"]},
  {"id":"WWG007","name":"鸠摩罗什寺","location":{"longitude":102.642,"latitude":37.928,"address":"武威市凉州区北大街","city":"武威"},"rating":4.2,"durationMin":60,"ticketPrice":0.0,"tags":["佛教","鸠摩罗什","免费"]},
  {"id":"WWG008","name":"神州荒漠野生动物园","location":{"longitude":102.642,"latitude":37.928,"address":"武威市凉州区清源镇","city":"武威"},"rating":4.1,"durationMin":180,"ticketPrice":20.0,"tags":["动物园","沙漠动物"]},
  {"id":"WWG009","name":"南城门楼","location":{"longitude":102.642,"latitude":37.928,"address":"武威市凉州区南关中路","city":"武威"},"rating":4.0,"durationMin":30,"ticketPrice":0.0,"tags":["古建筑","免费"]},
  {"id":"WWG010","name":"武威沙漠公园","location":{"longitude":102.819,"latitude":37.960,"address":"武威市凉州区清源镇","city":"武威"},"rating":4.1,"durationMin":180,"ticketPrice":20.0,"tags":["沙漠","主题"]},
])

add("鄂尔多斯", [
  {"id":"EED001","name":"响沙湾","location":{"longitude":109.886,"latitude":40.244,"address":"鄂尔多斯市达拉特旗树林召镇","city":"鄂尔多斯"},"rating":4.5,"durationMin":360,"ticketPrice":130.0,"tags":["沙漠","骆驼","自然"]},
  {"id":"EED002","name":"成吉思汗陵","location":{"longitude":109.793,"latitude":39.372,"address":"鄂尔多斯市伊金霍洛旗","city":"鄂尔多斯"},"rating":4.4,"durationMin":180,"ticketPrice":150.0,"tags":["历史文化","蒙古族","成吉思汗"]},
  {"id":"EED003","name":"鄂尔多斯草原","location":{"longitude":108.656,"latitude":39.110,"address":"鄂尔多斯市杭锦旗锡尼镇","city":"鄂尔多斯"},"rating":4.3,"durationMin":480,"ticketPrice":80.0,"tags":["草原","蒙古族","骑马"]},
  {"id":"EED004","name":"康巴什城区","location":{"longitude":109.781,"latitude":39.608,"address":"鄂尔多斯市康巴什区","city":"鄂尔多斯"},"rating":4.2,"durationMin":120,"ticketPrice":0.0,"tags":["现代建筑","城市风光","免费"]},
  {"id":"EED005","name":"七星湖","location":{"longitude":108.656,"latitude":39.110,"address":"鄂尔多斯市杭锦旗","city":"鄂尔多斯"},"rating":4.2,"durationMin":240,"ticketPrice":100.0,"tags":["湖泊","沙漠","生态"]},
  {"id":"EED006","name":"鄂尔多斯博物馆","location":{"longitude":109.781,"latitude":39.608,"address":"鄂尔多斯市康巴什区文化西路","city":"鄂尔多斯"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"EED007","name":"萨拉乌苏遗址","location":{"longitude":108.656,"latitude":38.020,"address":"鄂尔多斯市乌审旗","city":"鄂尔多斯"},"rating":4.1,"durationMin":180,"ticketPrice":50.0,"tags":["遗址","河套人","旧石器"]},
  {"id":"EED008","name":"乌兰活佛府","location":{"longitude":109.781,"latitude":39.608,"address":"鄂尔多斯市伊金霍洛旗","city":"鄂尔多斯"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["佛教","蒙古族","免费"]},
  {"id":"EED009","name":"九城宫","location":{"longitude":109.781,"latitude":39.608,"address":"鄂尔多斯市东胜区罕台镇","city":"鄂尔多斯"},"rating":4.1,"durationMin":240,"ticketPrice":80.0,"tags":["生态","亲子","滑雪"]},
  {"id":"EED010","name":"准格尔召","location":{"longitude":110.874,"latitude":39.643,"address":"鄂尔多斯市准格尔旗","city":"鄂尔多斯"},"rating":4.1,"durationMin":90,"ticketPrice":30.0,"tags":["佛教","藏传"]},
])

total = sum(len(c['attractions']) for c in data['cities'])
with open('huazai-trip-skills/src/main/resources/attractions/knowledge-base.json','w',encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print(f"\nTotal: {len(data['cities'])} cities, {total} attractions")
print("Done b26")
