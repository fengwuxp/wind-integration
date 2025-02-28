package com.wind.integration.oss.model;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * oss 文件对象
 *
 * @author wuxp
 * @date 2024-12-17 18:59
 **/
@Data
public class WindOssFile {

    /**
     * 对象 key
     */
    @NotBlank
    private String key;

    /**
     * 文件地址（公网地址）
     */
    @NotBlank
    private String url;

    /**
     * 文件名
     */
    @NotBlank
    private String name;

    /**
     * 初始文件名
     */
    private String originalName;

    /**
     * 文件 hash 值
     */
    private String hash;

    /**
     * 文件大小，字节数
     */
    private long size;

    /**
     * 文件上传时间
     */
    private LocalDateTime gmtCreate;

    /**
     * 文件 Content Type
     */
    private String contentType;

    /**
     * entity tag（是 HTTP 协议中用于缓存和条件请求的机制之一）
     */
    private String eTag;

    /**
     * 最后修改时间
     */
    private LocalDateTime lastModified;

    /**
     * 元数据
     */
    private Map<String, String> metadata;
}
