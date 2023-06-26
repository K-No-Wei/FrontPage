package com.heima.kafka.listener;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.ToString;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class HelloListener {

    @KafkaListener(topics = "itcast-topic")
    public void onMessage(String message){
        if(!StringUtils.isEmpty(message)){
//            System.out.println(message);
            User users = JSON.parseObject(message, User.class);
            System.out.println(users);
        }

    }
}

@Data
@ToString
class User{
    private Integer id;
    private String name;
}