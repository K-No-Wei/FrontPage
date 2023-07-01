package com.heima.minio.test;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;

import java.io.FileInputStream;

public class MinIOTest {
    public static void main(String[] args) {
        FileInputStream fileInputStream = null;

        try {
            fileInputStream = new FileInputStream("D:\\like\\29046.png");

            MinioClient minioClient = MinioClient.builder().credentials("minioadmin", "minioadmin")
                    .endpoint("http://192.168.150.101:9000")
                    .build();

            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .object("list.png")
                    .contentType("image/png")
                    .bucket("leadnews")
                    .stream(fileInputStream, fileInputStream.available(), -1)
                    .build();

            minioClient.putObject(putObjectArgs);

            System.out.println("http://192.168.150.101:9000/leadnews/list.png");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
