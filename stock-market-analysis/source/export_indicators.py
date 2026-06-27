from pyspark.sql import SparkSession

spark = SparkSession.builder \
    .appName("ExportIndicators") \
    .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension") \
    .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog") \
    .getOrCreate()

df = spark.read.format("delta").load("/user/stock/delta/indicators")
df.coalesce(1).write.mode("overwrite") \
    .option("header", "true") \
    .csv("/home/chenjin/Desktop/indicators_output")

print("导出完成，文件位于 /home/chenjin/Desktop/indicators_output/")