#!/usr/bin/env python3
import baostock as bs
import pandas as pd
import json
import time
import random
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
    {"code": "000001", "baostock_code": "sz.000001"},
    {"code": "600036", "baostock_code": "sh.600036"},
    {"code": "000858", "baostock_code": "sz.000858"},
    {"code": "601318", "baostock_code": "sh.601318"},
    {"code": "002415", "baostock_code": "sz.002415"},
    {"code": "000002", "baostock_code": "sz.000002"},
    {"code": "600519", "baostock_code": "sh.600519"},
    {"code": "000333", "baostock_code": "sz.000333"},
    {"code": "002594", "baostock_code": "sz.002594"},
    {"code": "300750", "baostock_code": "sz.300750"},
    {"code": "601166", "baostock_code": "sh.601166"},
    {"code": "600016", "baostock_code": "sh.600016"},
    {"code": "000651", "baostock_code": "sz.000651"},
    {"code": "002304", "baostock_code": "sz.002304"},
    {"code": "600030", "baostock_code": "sh.600030"},
    {"code": "601888", "baostock_code": "sh.601888"},
    {"code": "002352", "baostock_code": "sz.002352"},
    {"code": "600309", "baostock_code": "sh.600309"},
    {"code": "002475", "baostock_code": "sz.002475"},
    {"code": "600900", "baostock_code": "sh.600900"},
]

def fetch_extra_historical(code, start_date, end_date):
    lg = bs.login()
    if lg.error_code != '0':
        logger.error(f"BaoStock login failed: {lg.error_msg}")
        return None
    try:
        rs = bs.query_history_k_data_plus(
            code,
            "date,open,high,low,close,volume",
            start_date=start_date, end_date=end_date,
            frequency="d", adjustflag="3"
        )
        data_list = []
        while (rs.error_code == '0') and rs.next():
            data_list.append(rs.get_row_data())
        if data_list:
            df = pd.DataFrame(data_list, columns=rs.fields)
            df['open'] = df['open'].astype(float)
            df['high'] = df['high'].astype(float)
            df['low'] = df['low'].astype(float)
            df['close'] = df['close'].astype(float)
            df['volume'] = df['volume'].astype(int)
            return df
        else:
            return None
    finally:
        bs.logout()

def supplement_historical(extra_days=20):
    """
    往前追溯 extra_days 天，每只股票约获得 extra_days 条日线数据
    """
    end_date = '2025-06-01'   # 与之前已采集的数据衔接
    start_date = (datetime.strptime(end_date, '%Y-%m-%d') - timedelta(days=extra_days)).strftime('%Y-%m-%d')
    total = 0
    for stock in STOCK_LIST:
        df = fetch_extra_historical(stock['baostock_code'], start_date, end_date)
        if df is not None and not df.empty:
            logger.info(f"Extra {len(df)} records for {stock['code']} from {start_date} to {end_date}")
            for _, row in df.iterrows():
                msg = {
                    'symbol': stock['code'],
                    'trade_time': row['date'] + ' 15:00:00',
                    'open': float(row['open']),
                    'high': float(row['high']),
                    'low': float(row['low']),
                    'close': float(row['close']),
                    'volume': int(row['volume']),
                    'data_type': 'daily'
                }
                producer.send('stock-prices', msg)
                total += 1
            producer.flush()
            time.sleep(random.uniform(0.5, 1))
        else:
            logger.warning(f"No extra data for {stock['code']}")
    logger.info(f"Total supplemented records: {total}")

if __name__ == '__main__':
    supplement_historical(extra_days=20)   # 20天 × 20只 ≈ 400条，补齐到10040以上
