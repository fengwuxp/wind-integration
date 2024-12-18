package com.wind.integration.oss.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * oss 上传结果
 *
 * @author wuxp
 * @date 2024-12-17 18:51
 **/
@Data
@AllArgsConstructor
public class OssUploadResult {

    private final String key;

    private final String eTag;

    /**
     * 最后修改时间
     */
    private final LocalDateTime lastModified;
}
