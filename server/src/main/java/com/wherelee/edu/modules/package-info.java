/**
 * 单体应用内的业务模块边界。
 *
 * <p>这里只做逻辑模块化，不拆分微服务。各模块共享同一 Spring Boot 进程、
 * MySQL 数据库和部署单元；模块划分用于约束代码阅读和后续修改范围。</p>
 */
package com.wherelee.edu.modules;