package com.wherelee.edu.course;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CourseMaterial {

    private Long id;
    private Long courseId;
    private String title;
    /** 原始文件名，下载时用来命名 */
    private String fileName;
    private String fileKey;
    /** 由服务端从对象存储读取，不信客户端 */
    private Long sizeBytes;
    private Integer sort;
    private LocalDateTime createdAt;
}
