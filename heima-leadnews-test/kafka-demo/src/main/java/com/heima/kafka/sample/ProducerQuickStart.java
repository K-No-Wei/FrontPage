package com.heima.kafka.sample;

import org.apache.kafka.clients.producer.*;

import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * 生产者
 */
public class ProducerQuickStart {

    public static void main(String[] args) {
        //1.kafka的配置信息
        Properties properties = new Properties();
        //kafka的连接地址
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,"192.168.150.101:9092");
        //发送失败，失败的重试次数
        properties.put(ProducerConfig.RETRIES_CONFIG,5);
        //消息key的序列化器
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.StringSerializer");
        //消息value的序列化器
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.StringSerializer");


        //ack
        properties.put(ProducerConfig.ACKS_CONFIG, "all");

        //2.生产者对象
        KafkaProducer<String,String> producer = new KafkaProducer<String, String>(properties);

        //封装发送的消息
        ProducerRecord<String,String> record = new ProducerRecord<String, String>("itheima-topic","100003","hello kafka");

        //3.发送消息
        Future<RecordMetadata> future = producer.send(record);
        producer.send(record, (recordMetadata, e) -> {
            if (e == null) {
                System.out.println(recordMetadata.offset());
            } else {
                System.out.println(e.getMessage());
            }
        });

        try {
            RecordMetadata recordMetadata = future.get();
            recordMetadata.offset();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        System.out.println("消息发送完成");

        //4.关闭消息通道，必须关闭，否则消息发送不成功
        producer.close();
    }

}