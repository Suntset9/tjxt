package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.units.qual.C;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author Sunset
 * @since 2023-08-04
 */
@Api(tags = "我的课表相关接口")
@RestController
@RequestMapping("/lessons")
@RequiredArgsConstructor
public class LearningLessonController {

    private final ILearningLessonService lessonService;

    @ApiOperation("查询我的课表，排序字段 latest_learn_time:学习时间排序，create_time:购买时间排序")
    @GetMapping("/page")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query){
        return lessonService.queryMyLEssons(query);
    }

    @ApiOperation("查询我正在学习的课程")
    @GetMapping("/now")
    public LearningLessonVO queryMyCurrentLesson(){
        return lessonService.queryMyCurrentLesson();
    }


    /**
     * 校验当前用户是否可以学习当前课程
     * @param courseId 课程id
     * @return lessonId，如果是报名了则返回lessonId，否则返回空
     */
    @ApiOperation("校验当前用户是否可以学习当前课程")
    @GetMapping("/{courseId}/valid")
    Long isLessonValid(@PathVariable("courseId") Long courseId){
        return lessonService.isLessonValid(courseId);
    }

    /**
     * 查询用户课表中指定课程状态
     * @param courseId 课程id
     * @return 返回json格式，vo对象中包含json需要的格式，所以使用vo对象返回，
     */
    @ApiOperation("查询用户课表中指定课程状态")
    @GetMapping("/{courseId}")
    public LearningLessonVO queryLessonByCourseId(@PathVariable("courseId")Long courseId){
        return lessonService.queryLessonByCourseId(courseId);
    }

    /**
     * 统计课程学习人数
     * @param courseId 课程id
     * @return 学习人数
     */
    @ApiOperation("统计课程学习人数")
    @GetMapping("/{courseId}/count")
    Integer countLearningLessonByCourse(@PathVariable("courseId") Long courseId){
        return lessonService.countLearningLessonByCourse(courseId);
    }

}
