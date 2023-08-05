package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.LearningLessonVO;

import java.util.List;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author Sunset
 * @since 2023-08-04
 */
public interface ILearningLessonService extends IService<LearningLesson> {
    /**
     * <p>
     * 学生课程表 服务类
     * </p>
     */
    void addUserLessons(Long userId, List<Long> courseIds);


    PageDTO<LearningLessonVO> queryMyLEssons(PageQuery query);

    LearningLessonVO queryMyCurrentLesson();

    Long isLessonValid(Long courseId);

    LearningLessonVO queryLessonByCourseId(Long courseId);

    Integer countLearningLessonByCourse(Long courseId);
}
