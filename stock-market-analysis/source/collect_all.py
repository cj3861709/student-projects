#!/usr/bin/env python3
import baostock as bs
import pandas as pd
import json
import time
from kafka import KafkaProducer
from datetime import datetime, timedelta
import logging

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

producer = KafkaProducer(
    bootstrap_servers='localhost:9092',
    value_serializer=lambda v: json.dumps(v, ensure_ascii=False).encode('utf-8')
)

STOCK_LIST = [
    "sz.000001", "sh.600036", "sz.000858", "sh.601318", "sz.002415",
    "sz.000002", "sh.600519", "sz.000333", "sz.002594", "sz.300750",
    "sh.601166", "sh.600016", "sz.000651", "sz.002304", "sh.600030",
    "sh.601888", "sz.002352", "sh.600309", "sz.002475", "sh.600900"
]

# 采集近两年半数据（约 600 个交易日）
end_date = '2026-05-31'
start_date = (datetime.strptime(end_date, '%Y-%m-%d') - timedelta(days=800)).strftime('%Y-%m-%d')  # 800天 ≈ 2.2年

total = 0
for code in STOCK_LIST:
    lg = bs.login()
    if lg.error_code != '0':
        logger.error(f"Login fail {lg.error_msg}")
        continue
    try:
        rs = bs.query_history_k_data_plus(
            code, "date,open,high,low,close,volume",
            start_date=start_date, end_date=end_date,
            frequency="d", adjustflag="3"
        )
        rows = []
        while rs.next():
            rows.append(rs.get_row_data())
        if rows:
            df = pd.DataFrame(rows, columns=['date','open','high','low','close','volume'])
            for _, row in df.iterrows():
                msg = {
                    'symbol': code.split('.')[1],
                    'trade_time': row['date'] + ' 15:00:00',
                    'open': float(row['open']),
                    'high': float(row['high']),
                    'low': float(row['low']),
                    'close': float(row['close']),
                    'volume': int(row['volume']),
                    'data_type': 'daily'
                }
                producer.send('stock-prices', msg)
            producer.flush()
            cnt = len(df)
            total += cnt
            logger.info(f"{code}: {cnt} records")
        else:
            logger.warning(f"No data for {code}")
    finally:
        bs.logout()
    time.sleep(0.5)

logger.info(f"Total records sent to Kafka: {total}")
