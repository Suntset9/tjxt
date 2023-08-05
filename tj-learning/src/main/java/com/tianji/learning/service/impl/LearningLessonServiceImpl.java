package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.context.ThemeSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author Sunset
 * @since 2023-08-04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {

    final CourseClient courseClient;

     final CatalogueClient catalogueClient;

    @Override
    @Transactional
    public void addUserLessons(Long userId, List<Long> courseIds) {
        // TODO 添加课程信息到用户课程表
        //1.通过feign远程调用得到课程信息
        List<CourseSimpleInfoDTO> cInfoLists  = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cInfoLists)){
            log.error("课程信息不存在，无法添加课表");
            return;
        }
        //2封装po实体类，计算并填入过期时间
        ArrayList<LearningLesson> list = new ArrayList<>(cInfoLists.size());

        for (CourseSimpleInfoDTO cinfolist : cInfoLists) {
            LearningLesson Lesson = new LearningLesson();
            Lesson.setUserId(userId);
            Lesson.setCourseId(cinfolist.getId());
            Integer validDuration = cinfolist.getValidDuration();//查询课程有效期，单位：月
            if (validDuration != null && validDuration > 0 ){
                LocalDateTime now = LocalDateTime.now();
                Lesson.setCreateTime(now);//将当前时间设置进课程创建时间
                //当前时间加上课程有效期，则为过期时间
                Lesson.setExpireTime(now.plusHours(validDuration));
            }
            list.add(Lesson);
        }
        //3.批量保存
        this.saveBatch(list);
    }

    @Override
    public PageDTO<LearningLessonVO> queryMyLEssons(PageQuery query) {
        // TODO 分页查询我的课表
        //1.获取当前登录用户
        Long userId = UserContext.getUser();
        //2.对当前用户课表进行分页查询
        // select * from learning_lesson where user_id = #{userId} order by latest_learn_time limit 0, 5
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(query.toMpPage("latest_learn_time", false));
        //分页查询的结果
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)){//如果没有数据则返回空的分页对象
            return PageDTO.empty(page);
        }

        //3.封装vo实体类，进行返回,课程封面，名称等没有，远程调用接口获取
        List<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toList());
        //查询课程的结果
        List<CourseSimpleInfoDTO> cinfos = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cinfos)){//健壮性判断，没有课程则抛出异常
            throw new BizIllegalException("课程不存在");
        }
        //把cinfos课程集合处理成Map，key是courseId，值是course本身
        Map<Long, CourseSimpleInfoDTO> infoDTOMap = cinfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));

        //4.将vo实体类的属性封装list集合中返回
        List<LearningLessonVO> list = new ArrayList<>();
        for (LearningLesson record : records) {
            LearningLessonVO vo = BeanUtils.copyBean(record, LearningLessonVO.class);
            //获取课程id对应的课程信息
            CourseSimpleInfoDTO infoDTO = infoDTOMap.get(record.getCourseId());
            //将对应属性设置进去
            vo.setCourseName(infoDTO.getName());
            vo.setCourseCoverUrl(infoDTO.getCoverUrl());
            vo.setSections(infoDTO.getSectionNum());
            list.add(vo);
        }

        return PageDTO.of(page,list);
    }

    @Override
    public LearningLessonVO queryMyCurrentLesson() {
        //1.获取当前用户id
        Long userId = UserContext.getUser();
        //2.查询当前用户最近的学习课程， 按照最新学习时间latest_learn_time降序排序 取第一条， 正在学习中的， status = 1
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING)
                .last("limit 1")
                .one();
        if (lesson == null){
            return null;
        }
        //3.远程调用课程服务，给vo中的课程名，封面，章节数赋值
        CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(lesson.getId(), false, false);
        if (cinfo == null){
            throw new BizIllegalException("课程不存在");
        }

        //4.查询当前用户课表中，已报名 总的课程数
        //select count(*) from learning_lesson where user_id =xxx
        Integer count = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .count();

        //5.通过Feign远程调用课程服务，获取小结名称，和小结编号
        Long latestSectionId = lesson.getLatestSectionId();//最近学习的小节id
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(CollUtils.singletonList(latestSectionId));
        if (CollUtils.isEmpty(cataSimpleInfoDTOS)){
            throw new BizIllegalException("小节不存在");
        }

        //6.封装到Vo返回
        LearningLessonVO vo = BeanUtils.copyBean(lesson, LearningLessonVO.class);
        vo.setCourseName(cinfo.getName());
        vo.setCourseCoverUrl(cinfo.getCoverUrl());
        vo.setSections(cinfo.getSectionNum());
        vo.setCourseAmount(count);//当前用户学习的课程总数
        CataSimpleInfoDTO cataSimpleInfoDTO = cataSimpleInfoDTOS.get(0);
        vo.setLatestSectionName(cataSimpleInfoDTO.getName());//最近学习的小结名称
        vo.setLatestSectionIndex(cataSimpleInfoDTO.getCIndex());//最近学习的小结序号

        return vo;
    }

    /**
     * 检查课程是否有效接口
     * @param courseId
     * @return
     */
    @Override
    public Long isLessonValid(Long courseId) {
        //1.获取当前用户id
        Long userId = UserContext.getUser();
        //2.根据用户id查询当前课表是否有该课程
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();//用户+课程有联合唯一索引，根据当前课程号查询一天数据

        if (lesson == null){
            return null;
        }
        //3.判断课程是否有效
        LocalDateTime expireTime = lesson.getExpireTime();
        LocalDateTime now = LocalDateTime.now();

        if (expireTime != null && now.isAfter(expireTime)){
            return null ;
        }
        //3.返回课表id
        return lesson.getId();
    }

    /**
     /**
     * 查询用户课表中指定课程状态
     * @param courseId 课程id
     * @return 返回json格式，vo对象中包含json需要的格式，所以使用vo对象返回，
     */
    @Override
    public LearningLessonVO queryLessonByCourseId(Long courseId) {
        //1.获取当前用户id
        Long userId = UserContext.getUser();
        //2.根据课程id查询课程，
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId)
                .one();
        if (lesson==null){
            return  null;
        }

        //3.返回课程学习进度，有效期等信息
        return BeanUtils.copyBean(lesson, LearningLessonVO.class);
    }

    @Override
    public Integer countLearningLessonByCourse(Long courseId) {
        //1.统计当前课程的学习人数
        Integer count = this.lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .count();

        return count;
    }
}
