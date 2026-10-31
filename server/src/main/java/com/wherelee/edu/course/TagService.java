package com.wherelee.edu.course;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.course.dto.TagDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagMapper tagMapper;

    public List<TagDtos.TagVO> list() {
        return tagMapper.findAll().stream().map(TagService::toVO).toList();
    }

    @Transactional
    public TagDtos.TagVO create(TagDtos.SaveRequest request) {
        String name = request.name().trim();
        if (tagMapper.findByName(name) != null) {
            throw BizException.of(ErrorCode.TAG_NAME_EXISTS);
        }
        Tag tag = new Tag();
        tag.setName(name);
        tag.setSort(request.sort() == null ? 0 : request.sort());
        tagMapper.insert(tag);
        log.info("新增标签 tagId={} name={}", tag.getId(), name);
        return toVO(tag);
    }

    @Transactional
    public void update(Long id, TagDtos.SaveRequest request) {
        Tag tag = tagMapper.findById(id);
        if (tag == null) {
            throw BizException.of(ErrorCode.TAG_NOT_FOUND);
        }
        String name = request.name().trim();
        Tag existing = tagMapper.findByName(name);
        if (existing != null && !existing.getId().equals(id)) {
            throw BizException.of(ErrorCode.TAG_NAME_EXISTS);
        }
        tag.setName(name);
        if (request.sort() != null) {
            tag.setSort(request.sort());
        }
        tagMapper.update(tag);
        log.info("修改标签 tagId={} name={}", id, name);
    }

    /**
     * 删除前检查引用：还有课程挂着就拒。
     * 不级联删关联——那样课程会静默丢掉一个标签，运营根本不知道发生了什么。
     */
    @Transactional
    public void delete(Long id) {
        Tag tag = tagMapper.findById(id);
        if (tag == null) {
            throw BizException.of(ErrorCode.TAG_NOT_FOUND);
        }
        long used = tagMapper.countCourses(id);
        if (used > 0) {
            throw new BizException(ErrorCode.TAG_IN_USE, "还有 " + used + " 门课程在用这个标签");
        }
        tagMapper.deleteById(id);
        log.info("删除标签 tagId={} name={}", id, tag.getName());
    }

    private static TagDtos.TagVO toVO(Tag tag) {
        return new TagDtos.TagVO(tag.getId(), tag.getName(), tag.getSort());
    }
}
