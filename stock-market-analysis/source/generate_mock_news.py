from kafka import KafkaProducer
import json
import random
from datetime import datetime, timedelta

producer = KafkaProducer(
    bootstrap_servers='localhost:9092',
    value_serializer=lambda v: json.dumps(v, ensure_ascii=False).encode('utf-8')
)

# 20 只股票代码（与你的列表一致）
STOCKS = ['000001','600036','000858','601318','002415','000002','600519',
          '000333','002594','300750','601166','600016','000651','002304',
          '600030','601888','002352','600309','002475','600900']

for code in STOCKS:
    for i in range(30):   # 每只股票生成 30 条新闻
        sentiment = random.uniform(0, 1)
        news = {
            'symbol': code,
            'title': f'{code} 市场动态 {i+1}',
            'content': f'模拟新闻内容，情感得分 {sentiment:.2f}',
            'publish_time': (datetime.now() - timedelta(days=random.randint(0,30))).strftime('%Y-%m-%d %H:%M:%S'),
            'source': '数据源',
            'data_type': 'news'
        }
        producer.send('stock-news', news)
    print(f'已生成 {code} 的 30 条新闻')
producer.flush()
print('所有新闻已发送到 Kafka')
