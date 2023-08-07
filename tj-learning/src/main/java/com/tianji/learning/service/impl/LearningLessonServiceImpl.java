package com.tianji.learning.service.impl;

import cn.hutool.core.io.unit.DataUnit;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import com.tianji.common.utils.*;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.context.ThemeSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private final CourseClient courseClient;

    private final CatalogueClient catalogueClient;

    private final LearningRecordMapper recordMapper;
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
                Lesson.setExpireTime(now.plusMonths(validDuration));
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
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if (lesson == null){
            return null;
        }
        //3.远程调用课程服务，给vo中的课程名，封面，章节数赋值
        CourseFullInfoDTO cinfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
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
    /**
     * 创建学习计划
     * @param dto 接收前端参数保存到数据库
     */
    @Override
    public void createLearningPlans(LearningPlanDTO dto) {
        //1.获取当前用户id
        Long userId = UserContext.getUser();
        //2.根据当前用户查询课表
        LearningLesson lesson = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, dto.getCourseId())
                .one();
        /**
         *  下面写法等同于
         *  if (lesson ==null){
         *   throw new BizIllegalException("课程信息不存在");
         *  }
         */
        AssertUtils.isNotNull(lesson,"课程信息不存在");
        //3.保存前端传递的参数到数据库
        //lesson.setWeekFreq(dto.getFreq());
        //this.updateById(lesson);传统写法
        this.lambdaUpdate()
                .set(LearningLesson::getWeekFreq,dto.getFreq())
                .set(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .eq(LearningLesson::getId,lesson.getId())
                .update();
    }

    /**
     * 查询学习计划进度
     * @param query 分页参数
     * @return vo类，继承了 PageDTO<LearningPlanVO> 所以前端需要参数都可以返回
     */
    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
        //1.获取当前登录用户id
        Long userId = UserContext.getUser();
        //todo 2.查询积分
        //3.查询本周学习计划总数据 learning_lesson 条件userId status in（0，1） plan_status=1 查询 sum（week_freq）
        /**
         * select sum(week_freq) from learning_lesson
         *  where user_id = 2
         *  and plan_status = 1
         *  and status in (0,1)
         */
        QueryWrapper<LearningLesson> wrapper = new QueryWrapper<>();
        wrapper.select("sum(week_freq) as plansTotal");
        wrapper.eq("user_id",userId);
        wrapper.in("status",LessonStatus.NOT_BEGIN,LessonStatus.LEARNING);
        wrapper.eq("plan_status",PlanStatus.PLAN_RUNNING);
        Map<String, Object> map = this.getMap(wrapper);//map有可能为null
        //{plansTotal：7}
        Integer plansTotal = 0;
        if (map != null && map.get("plansTotal") != null){
            plansTotal = Integer.valueOf(map.get("plansTotal").toString());
        }

        //4.查询本周 实际 已学习的小节总数据  learning_record 条件userId finish_time在本周区间之内 finished为true  count(*)
        //SELECT * FROM learning_record
        //WHERE user_id = 2
        //AND finished = 1
        //AND finish_time BETWEEN '2023-08-06 00:00:01' AND '2023-08-12 23:59:59'
        LocalDate now = LocalDate.now();
        LocalDateTime weekBeginTime = DateUtils.getWeekBeginTime(now);
        LocalDateTime weekEndTime = DateUtils.getWeekEndTime(now);
        Integer weekFinished = recordMapper.selectCount(Wrappers.<LearningRecord>lambdaQuery()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .between(LearningRecord::getFinishTime, weekBeginTime, weekEndTime));


        //5.查询课表数据 learning_lesson 条件userId  status in（0，1）  plan_status = 1   分页
        Page<LearningLesson> page = this.lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .in(LearningLesson::getStatus,LessonStatus.NOT_BEGIN,LessonStatus.LEARNING)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();//获取集合
        if (CollUtils.isEmpty(records)){
            LearningPlanPageVO vo = new LearningPlanPageVO();
            vo.setTotal(0L);
            vo.setPages(0L);
            vo.setList(CollUtils.emptyList());
            return vo;
        }

        //6.远程调用课程服务 获取课程信息
        Set<Long> courseId = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        List<CourseSimpleInfoDTO> cInfos = courseClient.getSimpleInfoList(courseId);
        if (CollUtils.isEmpty(cInfos)){
            throw new BizIllegalException("课程不存在");
        }
        //将cInfos转换为map<课程id，CourseSimpleInfoDTO>
        Map<Long, CourseSimpleInfoDTO> cInfosMap = cInfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));

        //7.查询学习记录表learning_record 本周 当前用户下 每一门课下 已学习的小节数量
        //SELECT lesson_id,count(*) FROM learning_record
        //WHERE user_id = 2
        //AND finished = 1
        //AND finish_time BETWEEN '2023-08-06 00:00:01' AND '2023-08-12 23:59:59'
        //GROUP BY lesson_id;
        QueryWrapper<LearningRecord> rWrapper = new QueryWrapper<>();
        rWrapper.select("lesson_id as lessonId","count(*) as userId")//实体类中没有属性结束count值，我们使用临时属性userid接收
                .eq("user_id", userId)
                .eq("finished", true)
                .between("finish_time", weekBeginTime, weekEndTime)
                .groupBy("lesson_id");
        List<LearningRecord> recordList = recordMapper.selectList(rWrapper);
        //[LearningRecord(id=null, lessonId=1688073148521054209, sectionId=null, userId=1, moment=null, finished=null, createTime=null, finishTime=null, updateTime=null)]
        //map中的key是 lessonId  value 是 当前用户对该课程下已学习的小节数量
        Map<Long, Long> courseWeekFinishNumMap = recordList.stream().collect(Collectors.toMap(LearningRecord::getLessonId, LearningRecord::getUserId));

        //8.封装VO返回
        LearningPlanPageVO vo = new LearningPlanPageVO();
        vo.setWeekTotalPlan(plansTotal);//将课程表中查询的数据填入
        vo.setWeekFinished(weekFinished);//将我的课表中查询的数据传入
        List<LearningPlanVO> volist = new ArrayList<>();
        for (LearningLesson record : records) {
            LearningPlanVO learningPlanVO = BeanUtils.copyBean(record, LearningPlanVO.class);//将查询到的课表属性拷贝\
            CourseSimpleInfoDTO infoDTO = cInfosMap.get(record.getCourseId());//根据课程id获取课程名和小节总数
            if (infoDTO != null){
                learningPlanVO.setCourseName(infoDTO.getName());//课程名 通过课程微服务调用获取数据填入
                learningPlanVO.setSections(infoDTO.getSectionNum());//课程下的总小节数
            }
            //Long aLong = courseWeekFinishNumMap.get(record.getId());//当前用户该课程下已学习的小节数量
            //if (aLong != null){
            //    learningPlanVO.setWeekLearnedSections(aLong.intValue());
            //}else {
            //    learningPlanVO.setWeekLearnedSections(0);
            //}

            learningPlanVO.setWeekLearnedSections(courseWeekFinishNumMap.getOrDefault(record.getId(),0L).intValue());//map方法

            volist.add(learningPlanVO);//添加到集合中
        }

        //vo.setTotal(page.getTotal());
        //vo.setPages(page.getPages());
        //vo.setList(volist);下面的自定义的参数赋值方法，所以这里和下面的写法等价
        return vo.pageInfo(page.getTotal(),page.getPages(),volist);
    }
}
