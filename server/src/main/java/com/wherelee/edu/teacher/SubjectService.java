package com.wherelee.edu.teacher;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.teacher.dto.CreateSubjectRequest;
import com.wherelee.edu.teacher.dto.SubjectVO;
import com.wherelee.edu.teacher.dto.UpdateSubjectRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectMapper subjectMapper;

    public List<SubjectVO> list() {
        return subjectMapper.findAll().stream().map(SubjectVO::from).toList();
    }

    @Transactional
    public SubjectVO create(CreateSubjectRequest request) {
        if (subjectMapper.findByCode(request.code()) != null) {
            throw BizException.of(ErrorCode.SUBJECT_CODE_EXISTS);
        }
        Subject subject = new Subject();
        subject.setCode(request.code());
        subject.setName(request.name());
        subject.setSort(request.sort() == null ? 0 : request.sort());
        subjectMapper.insert(subject);
        log.info("新增学科 code={} name={}", subject.getCode(), subject.getName());
        return SubjectVO.from(subject);
    }

    /**
     * 改学科。只允许改名称和排序：
     * code 是被历史数据引用的标识，改了会让已有关联对不上，且无法回滚。
     */
    @Transactional
    public void update(Long id, UpdateSubjectRequest request) {
        Subject subject = subjectMapper.findById(id);
        if (subject == null) {
            throw BizException.of(ErrorCode.SUBJECT_NOT_FOUND);
        }
        subject.setName(request.name().trim());
        if (request.sort() != null) {
            subject.setSort(request.sort());
        }
        subjectMapper.update(subject);
        log.info("学科修改 id={} name={} sort={}", id, subject.getName(), subject.getSort());
    }

    /**
     * 删除前检查引用：还有讲师挂在这个学科下就不让删，
     * 否则讲师档案里会出现指向不存在学科的脏数据。
     */
    @Transactional
    public void delete(Long id) {
        Subject subject = subjectMapper.findById(id);
        if (subject == null) {
            throw BizException.of(ErrorCode.SUBJECT_NOT_FOUND);
        }
        if (subjectMapper.countTeachers(id) > 0) {
            throw BizException.of(ErrorCode.SUBJECT_IN_USE);
        }
        long courses = subjectMapper.countCourses(id);
        if (courses > 0) {
            throw new BizException(ErrorCode.SUBJECT_IN_USE, "还有 " + courses + " 门课程在用这个学科");
        }
        subjectMapper.deleteById(id);
        log.info("删除学科 id={} code={}", id, subject.getCode());
    }
}
