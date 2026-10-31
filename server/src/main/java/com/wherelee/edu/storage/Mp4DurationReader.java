package com.wherelee.edu.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 从 mp4 文件里读真实时长。
 *
 * 为什么需要：课时时长之前是前端传的，老师把它填成 1 秒，学员播 1 秒就算"学完"，
 * 学习进度的完成率全废。现在改成服务端自己读。
 *
 * 只读需要的那几段，不下载整个视频：
 *   1. 先读前 64KB，多数 mp4（做过 faststart 的）moov 在文件头部
 *   2. 没找到就读最后 2MB，普通 mp4 的 moov 在尾部
 *
 * 只解析 moov/mvhd 两个 box，其余结构不关心。
 */
@Slf4j
@Component
public class Mp4DurationReader {

    private static final int HEAD_PROBE = 64 * 1024;
    private static final int TAIL_PROBE = 2 * 1024 * 1024;

    /**
     * 返回秒数；解析不出来返回 null（非 mp4、moov 太大超出探测范围、文件被截断）。
     * 调用方应该把 null 当成"不能接受"处理，而不是当成 0。
     */
    public Integer read(FileStorage storage, String key) {
        long size = storage.size(key);
        if (size < 16) {
            return null;
        }

        Integer duration = findInMoov(storage.readRange(key, 0, Math.min(HEAD_PROBE, size)));
        if (duration != null) {
            return duration;
        }

        long tailStart = Math.max(0, size - TAIL_PROBE);
        duration = findInMoov(storage.readRange(key, tailStart, size - tailStart));
        if (duration == null) {
            log.warn("无法解析视频时长 key={} size={}", key, size);
        }
        return duration;
    }

    /** 在给定字节里顺序找 moov，找到就在它内部找 mvhd */
    private Integer findInMoov(byte[] data) {
        int offset = 0;
        while (offset + 8 <= data.length) {
            int boxSize = (int) readUInt32(data, offset);
            String type = type(data, offset);
            if (boxSize < 8) {
                return null;
            }
            if ("moov".equals(type)) {
                int innerLen = Math.min(boxSize - 8, data.length - offset - 8);
                return findMvhd(data, offset + 8, innerLen);
            }
            if (offset + boxSize > data.length) {
                // 这个 box 跨出了探测范围，后面的数据没读到
                return null;
            }
            offset += boxSize;
        }
        return null;
    }

    private Integer findMvhd(byte[] data, int start, int length) {
        int offset = start;
        int end = start + length;
        while (offset + 8 <= end) {
            int boxSize = (int) readUInt32(data, offset);
            if (boxSize < 8) {
                return null;
            }
            if ("mvhd".equals(type(data, offset))) {
                return readMvhd(data, offset + 8);
            }
            offset += boxSize;
        }
        return null;
    }

    /**
     * mvhd payload 布局（跳过 size+type）：
     *   version(1) flags(3)
     *   v0: creation(4) modification(4) timescale(4) duration(4)
     *   v1: creation(8) modification(8) timescale(4) duration(8)
     */
    private Integer readMvhd(byte[] data, int payload) {
        if (payload + 32 > data.length) {
            return null;
        }
        int version = data[payload] & 0xFF;
        long timescale;
        long duration;
        if (version == 1) {
            timescale = readUInt32(data, payload + 20);
            duration = readUInt64(data, payload + 24);
        } else {
            timescale = readUInt32(data, payload + 12);
            duration = readUInt32(data, payload + 16);
        }
        if (timescale <= 0 || duration <= 0) {
            return null;
        }
        long seconds = duration / timescale;
        return seconds > Integer.MAX_VALUE ? null : (int) seconds;
    }

    private String type(byte[] data, int offset) {
        return new String(data, offset + 4, 4, StandardCharsets.ISO_8859_1);
    }

    private long readUInt32(byte[] data, int offset) {
        if (offset + 4 > data.length) {
            return -1;
        }
        return ((long) (data[offset] & 0xFF) << 24)
                | ((long) (data[offset + 1] & 0xFF) << 16)
                | ((long) (data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private long readUInt64(byte[] data, int offset) {
        long high = readUInt32(data, offset);
        long low = readUInt32(data, offset + 4);
        if (high < 0 || low < 0) {
            return -1;
        }
        return (high << 32) | low;
    }
}
