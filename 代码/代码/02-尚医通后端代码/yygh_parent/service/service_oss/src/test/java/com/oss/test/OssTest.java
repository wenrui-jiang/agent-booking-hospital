package com.oss.test;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;

public class OssTest {

    public static void main(String[] args) {
        String endpoint = System.getenv().getOrDefault("ALIYUN_OSS_ENDPOINT", "https://oss-cn-hangzhou.aliyuncs.com");
        String accessKeyId = System.getenv("ALIYUN_OSS_ACCESS_KEY_ID");
        String accessKeySecret = System.getenv("ALIYUN_OSS_SECRET");
        String bucketName = System.getenv("ALIYUN_OSS_BUCKET");

        if (accessKeyId == null || accessKeySecret == null || bucketName == null) {
            System.out.println("Skip OSS smoke test: ALIYUN_OSS_ACCESS_KEY_ID, ALIYUN_OSS_SECRET, and ALIYUN_OSS_BUCKET are required.");
            return;
        }

        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        ossClient.createBucket(bucketName);
        ossClient.shutdown();
    }
}