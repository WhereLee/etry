/**
 * learning 模块对课程模块公开的播放契约。
 *
 * <p>课程模块只表达“需要播放地址/记录播放事件”，不直接依赖 Redis 缓存、
 * COS 预签名或播放日志实现。</p>
 */
package com.wherelee.edu.modules.learning.api;