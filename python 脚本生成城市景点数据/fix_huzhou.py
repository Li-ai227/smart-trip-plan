import json
with open('huazai-trip-skills/src/main/resources/attractions/knowledge-base.json','r',encoding='utf-8') as f:
    data = json.load(f)

# Remove empty 湖州 city
for i, c in enumerate(data['cities']):
    if c['city'] == '湖州' and len(c['attractions']) == 0:
        del data['cities'][i]
        print('Removed empty 湖州')
        break

ids = set()
for c in data['cities']:
    for a in c['attractions']: ids.add(a['id'])

attrs = [
  {"id":"HUZ001","name":"南浔古镇","location":{"longitude":120.431,"latitude":30.870,"address":"湖州市南浔区南浔镇","city":"湖州"},"rating":4.5,"durationMin":300,"ticketPrice":100.0,"tags":["古镇","水乡","中西建筑"]},
  {"id":"HUZ002","name":"莫干山风景区","location":{"longitude":119.876,"latitude":30.618,"address":"湖州市德清县莫干山镇","city":"湖州"},"rating":4.7,"durationMin":480,"ticketPrice":80.0,"tags":["避暑","民宿","自然"]},
  {"id":"HUZ003","name":"安吉竹海","location":{"longitude":119.718,"latitude":30.546,"address":"湖州市安吉县天荒坪镇","city":"湖州"},"rating":4.6,"durationMin":240,"ticketPrice":80.0,"tags":["竹海","自然","卧虎藏龙"]},
  {"id":"HUZ004","name":"太湖旅游度假区","location":{"longitude":120.101,"latitude":30.953,"address":"湖州市吴兴区太湖路","city":"湖州"},"rating":4.3,"durationMin":240,"ticketPrice":0.0,"tags":["太湖","度假","免费"]},
  {"id":"HUZ005","name":"安吉凯蒂猫家园","location":{"longitude":119.718,"latitude":30.546,"address":"湖州市安吉县天使大道1号","city":"湖州"},"rating":4.2,"durationMin":360,"ticketPrice":290.0,"tags":["主题乐园","亲子","HelloKitty"]},
  {"id":"HUZ006","name":"飞英塔","location":{"longitude":120.098,"latitude":30.872,"address":"湖州市吴兴区塔下街","city":"湖州"},"rating":4.2,"durationMin":60,"ticketPrice":20.0,"tags":["古建筑","唐代","塔中塔"]},
  {"id":"HUZ007","name":"衣裳街","location":{"longitude":120.095,"latitude":30.870,"address":"湖州市吴兴区衣裳街","city":"湖州"},"rating":4.3,"durationMin":120,"ticketPrice":0.0,"tags":["历史街区","美食","免费"]},
  {"id":"HUZ008","name":"莲花庄","location":{"longitude":120.097,"latitude":30.861,"address":"湖州市吴兴区莲花庄路","city":"湖州"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["园林","赵孟頫","免费"]},
  {"id":"HUZ009","name":"湖州博物馆","location":{"longitude":120.088,"latitude":30.868,"address":"湖州市吴兴区仁皇山路","city":"湖州"},"rating":4.2,"durationMin":90,"ticketPrice":0.0,"tags":["博物馆","免费"]},
  {"id":"HUZ010","name":"安吉江南天池","location":{"longitude":119.588,"latitude":30.469,"address":"湖州市安吉县天荒坪镇","city":"湖州"},"rating":4.2,"durationMin":180,"ticketPrice":80.0,"tags":["天池","星空","滑雪"]},
  {"id":"HUZ011","name":"安吉中南百草园","location":{"longitude":119.680,"latitude":30.630,"address":"湖州市安吉县递铺镇","city":"湖州"},"rating":4.3,"durationMin":300,"ticketPrice":150.0,"tags":["动物园","植物园","亲子"]},
  {"id":"HUZ012","name":"莫干山裸心谷","location":{"longitude":119.876,"latitude":30.618,"address":"湖州市德清县莫干山镇","city":"湖州"},"rating":4.6,"durationMin":480,"ticketPrice":500.0,"tags":["度假","民宿","奢华"]},
  {"id":"HUZ013","name":"长兴仙山湖","location":{"longitude":119.756,"latitude":30.960,"address":"湖州市长兴县泗安镇","city":"湖州"},"rating":4.2,"durationMin":240,"ticketPrice":80.0,"tags":["湖泊","湿地","自然"]},
  {"id":"HUZ014","name":"德清下渚湖湿地","location":{"longitude":120.021,"latitude":30.518,"address":"湖州市德清县三合乡","city":"湖州"},"rating":4.3,"durationMin":180,"ticketPrice":80.0,"tags":["湿地","自然","候鸟"]},
  {"id":"HUZ015","name":"安吉云上草原","location":{"longitude":119.588,"latitude":30.469,"address":"湖州市安吉县山川乡","city":"湖州"},"rating":4.4,"durationMin":360,"ticketPrice":280.0,"tags":["高山草原","户外","亲子"]},
  {"id":"HUZ016","name":"长兴大唐贡茶院","location":{"longitude":119.764,"latitude":31.024,"address":"湖州市长兴县水口乡","city":"湖州"},"rating":4.2,"durationMin":120,"ticketPrice":75.0,"tags":["茶文化","唐代"]},
  {"id":"HUZ017","name":"小莲庄","location":{"longitude":120.431,"latitude":30.870,"address":"湖州市南浔区南浔镇","city":"湖州"},"rating":4.3,"durationMin":90,"ticketPrice":100.0,"tags":["园林","清代"]},
  {"id":"HUZ018","name":"安吉龙王山","location":{"longitude":119.422,"latitude":30.401,"address":"湖州市安吉县章村镇","city":"湖州"},"rating":4.2,"durationMin":240,"ticketPrice":70.0,"tags":["登山","黄浦江源","自然"]},
  {"id":"HUZ019","name":"太湖龙之梦乐园","location":{"longitude":120.035,"latitude":30.945,"address":"湖州市长兴县图影","city":"湖州"},"rating":4.3,"durationMin":600,"ticketPrice":300.0,"tags":["主题乐园","动物世界","演艺"]},
  {"id":"HUZ020","name":"德清新市古镇","location":{"longitude":120.263,"latitude":30.607,"address":"湖州市德清县新市镇","city":"湖州"},"rating":4.3,"durationMin":180,"ticketPrice":50.0,"tags":["古镇","千年","水乡"]},
]
added = 0
for a in attrs:
    if a['id'] in ids: print(f'skip {a["id"]}')
    else: ids.add(a['id']); added += 1
data['cities'].append({"city":"湖州","attractions":attrs})
total = sum(len(c['attractions']) for c in data['cities'])
print(f'湖州: +{added} attractions, total: {len(data["cities"])} cities, {total} attractions')
with open('huazai-trip-skills/src/main/resources/attractions/knowledge-base.json','w',encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print('Fixed')
