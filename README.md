[![Build Status](https://travis-ci.org/qiniu/QStreaming.svg?branch=master)](https://travis-ci.org/qiniu/QStreaming) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) [![Gitter](https://badges.gitter.im/qiniu-streaming/community.svg)](https://gitter.im/qiniu-streaming/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)

QStreaming is a framework that simplifies writing and executing ETLs on top of [Apache Spark](http://spark.apache.org/)

It is based on a simple sql-like configuration file and runs on any Spark cluster

## Architect 

![architect](docs/image/architect.png)

QStreaming is built on top of [Apache Spark](http://spark.apache.org/) and is mainly made of following components:

- **Pipeline DSL**

  A configuration file defines the queries of the ETL Pipeline, it's  made of by the input tables, metric statements, data quality check rules (optional ) and output tables

- **Pipeline DSL Parser**

  A parser  parsing the **Pipeline DSL**  using  [Antlr](https://www.antlr.org/) parser generator and build the pipeline domain models

- **Pipeline translater**

  A  translater translate the pipeline  generated by **Pipeline DSL parser** into spark transformations

- **Data Quality Checker**

  Data quality checker is use to verify/measure  intermediate or final dataset according to the data quality check rules which defined in **Pipeline DSL** file 

- **Pipeline Runner**

  Pipeline Runner scheduling and run the pipeline as a spark batch/streaming application 

## Getting started

To run QStreaming you must first define 2 files.

##### Pipeline DSL

For example a simple  pipeline dsl file  should be as follows:

```sql
-- DDL for streaming input which connect to a kafka topic
-- this declares five fields based on the JSON data format.In addition, it use the ROWTIME() to declare a virtual column that generate the event time attribute from existing ts field
create stream input table user_behavior(
  user_id LONG,
  item_id LONG,
  category_id LONG,
  behavior STRING,
  ts TIMESTAMP,
  eventTime as ROWTIME(ts,'1 minutes')
) using kafka(
  kafka.bootstrap.servers="localhost:localhost:9091",
  startingOffsets=earliest,
  subscribe="user_behavior",
  "group-id"="user_behavior"
);

-- DDL for streaming output which connect to a kafka topic
create stream output table behavior_cnt_per_hour
using kafka(
  kafka.bootstrap.servers="localhost:9091",
  topic="behavior_cnt_per_hour"
)TBLPROPERTIES(
  "update-mode"="update",
  checkpointLocation = "behavior_cnt_per_hour"
);

-- CREATE VIEW count the number of "buy" records in each hour window.
create view v_behavior_cnt_per_hour as
SELECT
   window(eventTime, "1 minutes") as window,
   COUNT(*) as behavior_cnt,
   behavior
FROM user_behavior
GROUP BY
  window(eventTime, "1 minutes"),
  behavior;


--  persist result to kafka
insert into behavior_cnt_per_hour
select
   from_unixtime(cast(window.start as LONG)/1000,'yyyy-MM-dd HH:mm') as time,
   behavior_cnt,
   behavior
from
  v_behavior_cnt_per_hour;
```

##### Application configuration properties

There are only two config options  currently avaliable.



#### Run QStreaming

There are three options to run QStreaming

##### Run on a yarn cluster

To run on a cluster requires [Apache Spark](https://spark.apache.org/) v2.2+

- get the latest JAR file

  There are two options to get QStreaming Jar files

  - clone project and build

  ```bash
  git clone git@github.com:qbox/QStreaming.git \
  cd QStreaming && mvn clean install
  ```

  - Download the last released JAR from [here](https://packagecloud.io/qiniu/release)

- Run the following command:

``` bash
$SPARK_HOME/bin/spark-submit
--class com.qiniu.stream.core.Streaming \
--master yarn \
--deploy-mode client \
stream-standalone-0.0.2.jar \
-j pipeline.dsl
```

##### Run on a standalone cluster

To run on a standalone cluster you must first [start a spark standalone cluster](https://spark.apache.org/docs/latest/spark-standalone.html) , and then  run the  following  command:

```bash
$SPARK_HOME/bin/spark-submit
--class com.qiniu.stream.core.Streaming \
--master spark://IP:PORT \
stream-standalone-0.0.2.jar \
-j pipeline.dsl
```

##### Run as a library

It's also possible to use QStreaming inside your own project

To use it adds the dependency to your project

- maven

  ```
  <dependency>
    <groupId>com.qiniu.stream</groupId>
    <dependency>stream-standalone</dependency>
    <version>0.0.2</version>
  </dependency>
  ```

- gradle

  ```
  compile 'com.qiniu.stream:stream-standalone:0.0.2'
  ```

- sbt

  ```
  libraryDependencies += "com.qiniu.stream" % "stream-standalone" % "0.0.2"
  ```

## Datasources

we support following datasource as input:

- [Kafka](http://kafka.apache.org/) (streaming) with `json/regex/csv/avro`   format
- HDFS/S3 with `csv/json/text/parquet/avro`   storage format
- [Jdbc datasource](https://en.wikipedia.org/wiki/Java_Database_Connectivity)
- MongoDB
- [Apache Hbase](http://hbase.apache.org/)

and following datasources as output:

- [Kafka](http://kafka.apache.org/)
- [Elasticsearch](https://www.elastic.co/elasticsearch/)
- [Apache Hbase](http://hbase.apache.org/)
- MongoDB
- [Jdbc datasource](https://en.wikipedia.org/wiki/Java_Database_Connectivity)
- HDFS/S3 with `csv/json/text/parquet/avro`   storage format

## Features

### DDL Support for streaming process

```sql
create stream input table user_behavior(
  user_id LONG,
  item_id LONG,
  category_id LONG,
  behavior STRING,
  ts TIMESTAMP,
  eventTime as ROWTIME(ts,'1 minutes')
) using kafka(
  kafka.bootstrap.servers="localhost:9091",
  startingOffsets=earliest,
  subscribe="user_behavior",
  "group-id"="user_behavior"
);
```

Above DDL statement define an input which connect to a kafka topic.

For detail information  please refer to [CreateSourceTableStatement](https://github.com/qbox/QStreaming/blob/master/stream-spark/src/main/antlr4/com/qiniu/stream/spark/parser/Sql.g4#L38)  for how to define an input and [CreateSinkTableStatement](https://github.com/qbox/QStreaming/blob/master/stream-spark/src/main/antlr4/com/qiniu/stream/spark/parser/Sql.g4#L43) for how to define an output.

### Watermark support in sql

QStreaming supports watermark which helps a stream processing engine to deal with late data.

There are two ways to use watermark for a stream processing engine

- Adding ***ROWTIME(eventTimeField,delayThreshold)*** as a schema property in a  ddl statement

  ```sql
  create stream input table user_behavior(
    user_id LONG,
    item_id LONG,
    category_id LONG,
    behavior STRING,
    ts TIMESTAMP,
    eventTime as ROWTIME(ts,'1 minutes')
  ) using kafka(
    kafka.bootstrap.servers="localhost:9091",
    startingOffsets=earliest,
    subscribe="user_behavior",
    "group-id"="user_behavior"
  );
  ```

  Above example  means use `eventTime` as event time field  with 5 minutes delay thresholds

- Adding   ***waterMark("eventTimeField, delayThreshold")***  as a  view property in  a view statement

  ```sql
  create view v_behavior_cnt_per_hour(waterMark = "eventTime, 1 minutes") as
  SELECT
     window(eventTime, "1 minutes") as window,
     COUNT(*) as behavior_cnt,
     behavior
  FROM user_behavior
  GROUP BY
    window(eventTime, "1 minutes"),
    behavior;
  ```

Above  example  define a watermark use `eventTime` field with 1 minute threshold

### Dynamic user define function

```
-- define UDF named hello
def hello(name:String) = {
   s"hello ${name}"
};

```

QStreaming allow to define a dynamic UDF inside job.dsl, for more detail information please refer to [createFunctionStatement](https://github.com/qbox/QStreaming/blob/master/stream-spark/src/main/antlr4/com/qiniu/stream/spark/parser/Sql.g4#L16)

Above example define UDF with a string parameter.

### The multiple sink for streaming application

```sql
    create stream output table output using hbase(
        quorum = '192.168.0.2:2181,192.168.0.3:2181,192.168.0.4:2181',
        tableName = 'buy_cnt_per_hour',
        rowKey = '<hour_of_day>',
        cf = 'cf',
        fields = '[{"qualified":"buy_cnt","value":"behavior_cnt","type":"LongType"}]',
        where = 'behavior="buy"'
    ),hbase(
        quorum = 'jjh714:2181,jjh712:2181,jjh713:2181,jjh710:2181,jjh711:2181',
        tableName = 'order_cnt_per_hour
        rowKey = '<hour_of_day>',
        cf = 'cf',
        fields = '[{"qualified":"order_cnt","value":"behavior_cnt","type":"LongType"}]',
        where = 'behavior="order"'
    ) TBLPROPERTIES (outputMode = update,checkpointLocation = "behavior_output");
```

QStreaming allow you to define multiple output for streaming/batch process engine by leavarage  [foreEachBatch](https://spark.apache.org/docs/latest/structured-streaming-programming-guide.html#using-foreach-and-foreachbatch) mode (only avaliable in spark>=2.4.0)

Above example will sink the behavior count metric to two hbase table, for more information about how to create multiple sink please refer to [createSinkTableStatement](https://github.com/qbox/QStreaming/blob/master/stream-spark/src/main/antlr4/com/qiniu/stream/spark/parser/Sql.g4#L43)

### Variable interpolation

```sql
create batch input table raw_log
USING parquet(path="hdfs://cluster1/logs/day=<day>/hour=<hour>");
```

job.dsl file support variable interpolation from command line arguments , this  is  useful  for running QStreaming as a periodic job.

For example, you can pass the value for  `theDayThatRunAJob` and `theHourThatRunAJob` from an  [Airflow](http://airflow.apache.org/) DAG

```bash
$SPARK_HOME/bin/spark-submit
--name {{.dir}} \
--class com.qiniu.stream.core.Streaming \
--master yarn \
--deploy-mode client \
--conf spark.executor.extraClassPath=./ \
stream-standalone-0.0.2.jar \
-j pipeline.dsl \
-c stream.template.vars.day=theDayThatRunAJob \
-c stream.template.vars.hour=theHourThatRunAJob
```

### Kafka lag monitor

QStreaming allow to monitor the kafka topic offset lag by  adding the ***"group-id"*** connector property in  ddl statement as below

```sql
create stream input table user_behavior(
  user_id LONG,
  item_id LONG,
  category_id LONG,
  behavior STRING,
  ts TIMESTAMP,
  eventTime as ROWTIME(ts,'1 minutes')
) using kafka(
  kafka.bootstrap.servers="localhost:9091",
  startingOffsets=earliest,
  subscribe="user_behavior",
  "group-id"="user_behavior"
);
```

## [Contributing](https://github.com/qiniu/QStreaming/CONTRIBUTING.md)
We welcome all kinds of contribution, including bug reports, feature requests, documentation improvements, UI refinements, etc.

Thanks to all [contributors](https://github.com/qiniu/QStreaming/graphs/contributors)!!


## License
See the [LICENSE file](https://github.com/qiniu/QStreaming/LICENSE) for license rights and limitations (Apache License).



## Join QStreaming WeChat Group

Join [Gitter room](https://gitter.im/qiniu-streaming/community)

Join We-Chat Group

![image-20200924173745117](docs/image/wechat.png)