import json
import mysql.connector
from kafka import KafkaConsumer
from snownlp import SnowNLP
import time

# MySQL 连接配置
conn = mysql.connector.connect(
    host='localhost',
    user='root',
    password='123456',   # 替换为真实密码
    database='stock_db'
)
cursor = conn.cursor()

# 创建表（如果不存在）
cursor.execute("""
    CREATE TABLE IF NOT EXISTS news_sentiment_mysql (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        stock_code VARCHAR(10),
        title VARCHAR(500),
        content TEXT,
        publish_time DATETIME,
        source VARCHAR(50),
        sentiment_score DOUBLE,
        INDEX idx_stock_time (stock_code, publish_time)
    )
""")
conn.commit()

# Kafka 消费者
consumer = KafkaConsumer(
    'stock-news',
    bootstrap_servers='localhost:9092',
    auto_offset_reset='earliest',   # 从最早的消息开始消费
    enable_auto_commit=True,
    group_id='news_consumer_group',
    value_deserializer=lambda m: json.loads(m.decode('utf-8'))
)

print("开始消费新闻并计算情感得分，按 Ctrl+C 停止...")
for msg in consumer:
    news = msg.value
    title = news.get('title', '')
    content = news.get('content', '')
    text = title + ' ' + content
    # 情感分析
    try:
        score = SnowNLP(text[:300]).sentiments
    except:
        score = 0.5
    # 插入 MySQL
    sql = """
        INSERT INTO news_sentiment_mysql (stock_code, title, content, publish_time, source, sentiment_score)
        VALUES (%s, %s, %s, %s, %s, %s)
    """
    val = (news['symbol'], title, content, news.get('publish_time'), news.get('source'), score)
    cursor.execute(sql, val)
    conn.commit()
    print(f"已处理: {news['symbol']} - {title[:30]}... 情感得分: {score:.2f}")

# 不会执行到这里，因为循环无限
cursor.close()
conn.close()
