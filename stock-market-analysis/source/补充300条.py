#!/usr/bin/env python3
import baostock as bs
import pandas as pd
import json
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

# 往前推 30 天（实际交易日约 20 天/只，20*20=400 条）
end_date = '2025-06-01'
start_date = (datetime.strptime(end_date, '%Y-%m-%d') - timedelta(days=30)).strftime('%Y-%m-%d')

total_added = 0

for bs_code in STOCK_LIST:
    lg = bs.login()
    if lg.error_code != '0':
        logger.error(f"Login fail {lg.error_msg}")
        continue
    try:
        rs = bs.query_history_k_data_plus(
            bs_code, "date,open,high,low,close,volume",
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
                    'symbol': bs_code.split('.')[1],
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
            added = len(df)
            total_added += added
            logger.info(f"Added {added} records for {bs_code}")
    finally:
        bs.logout()

logger.info(f"Total added records: {total_added}")
