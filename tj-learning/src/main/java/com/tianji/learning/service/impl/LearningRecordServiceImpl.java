package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author Sunset
 * @since 2023-08-05
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService lessonService;
    private final CourseClient client;
    private final LearningRecordDelayTaskHandler taskHandler;
    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        //1.获取当前登录用户
        Long userId = UserContext.getUser();
        //2.查询课表信息 条件userid 和courseId
        LearningLesson lesson = lessonService.lambdaQuery()
                .eq(LearningLesson::getCourseId, courseId)
                .eq(LearningLesson::getUserId, userId)
                .one();

        //3.查询学习记录 条件lesson_id和userId
        List<LearningRecord> records = this.lambdaQuery()
                .eq(LearningRecord::getLessonId, lesson.getId())
                .eq(LearningRecord::getUserId, userId)
                .list();


        //4.封装结果返回
        LearningLessonDTO dto = new LearningLessonDTO();
        dto.setId(lesson.getId());
        dto.setLatestSectionId(lesson.getLatestSectionId());
        List<LearningRecordDTO> dtos = BeanUtils.copyList(records, LearningRecordDTO.class);
        dto.setRecords(dtos);

        return dto;
    }

    @Override
    public void addLearningRecord(LearningRecordFormDTO formDTO) {
        //1.获取当前登录用户id
        Long userId = UserContext.getUser();

        //2.处理学习记录
        boolean isFinshed = false;//代表本小节是否第一次学完
        if (formDTO.getSectionType().equals(SectionType.VIDEO)){
            //2.1提交视频播放记录
            isFinshed = handleVideRecord(userId,formDTO);
        }else {
            //2.2提交考试记录
            isFinshed = handleExamRecord(userId,formDTO);
        }
        if (!isFinshed){
            //没有新学完的小节，无需更新课表中的数据
            return;
        }
        //3.处理课表记录
        handleLessonData(formDTO);
    }

    private void handleLessonData(LearningRecordFormDTO formDTO) {
        //1.查询课表learning_lesson 条件 lesson_id主键
        LearningLesson lesson = lessonService.getById(formDTO.getLessonId());
        if (lesson == null){
            throw new BizIllegalException("课程不存在，无法更新数据");
        }

        //2.判断是否第一次学完 isFinished是不是true
        boolean allLearned = false;

        //3.远程调用课程服务 得到课程信息 小节总数
        CourseFullInfoDTO cInfo = client.getCourseInfoById(lesson.getCourseId(), false, false);
        if (cInfo == null) {
            throw new BizIllegalException("课程不存在，无法更新数据");
        }
        //4.如果isFinished为true 本小节时第一次学完 判断该用户对该课程下全部小节是否学完
        allLearned = lesson.getLearnedSections() + 1 >= cInfo.getSectionNum();


        //5.更新课表数据
        lessonService.lambdaUpdate()
                //.set(lesson.getStatus()==LessonStatus.NOT_BEGIN, LearningLesson::getStatus , LessonStatus.LEARNING.getValue())//如果已学习小节数量为0,则代表第一次开始学习，将状态更新为学习中
                .set(lesson.getLearnedSections() == 0 , LearningLesson::getStatus , LessonStatus.LEARNING.getValue())//如果已学习小节数量为0,则代表第一次开始学习，将状态更新为学习中
                .set(allLearned,LearningLesson::getStatus, LessonStatus.FINISHED.getValue())//第一次学完则将状态更新为已学完
                .set(LearningLesson::getLatestSectionId,formDTO.getSectionId())//保存最近一次学习的小节id
                .set(LearningLesson::getLatestLearnTime,formDTO.getCommitTime())//保存最近一次学习时间
                //.set(LearningLesson::getLearnedSections,lesson.getLearnedSections()+1)//已学完则将已学习小节数量+1
                .setSql("learned_sections = learned_sections + 1")//已学完则将已学习小节数量+1
                .eq(LearningLesson::getId,lesson.getId())//where id =xxx ，
                .update();
    }

    private boolean handleVideRecord(Long userId, LearningRecordFormDTO formDTO) {
        //1.查询判断学习记录是否存在 条件 userId lessonId sectionId
        LearningRecord old = queryOldRecord(formDTO.getLessonId(),formDTO.getSectionId());
        //LearningRecord old = this.lambdaQuery()
        //        .eq(LearningRecord::getLessonId, formDTO.getLessonId())
        //        .eq(LearningRecord::getSectionId, formDTO.getSectionId())
        //        .one();

        //判断学习记录是否存在
        if (old == null){
            //2.不存在则新增学习记录
            //转换po
            LearningRecord record = BeanUtils.copyBean(formDTO, LearningRecord.class);
            //2.填充数据
            record.setUserId(userId);
            //保存学习记录
            boolean save = this.save(record);
            if (!save){
                throw new DbException("新增学习记录失败！");
            }
            return false;//新增之后返回，代表本小节没有学完
        }

        //3.存在则更新学习记录 learning_record 更新什么字段 moment  !old.getFinished() 默认值为flase，取反为true 学完后为true 取反flase
        boolean finished = !old.getFinished() && formDTO.getMoment() *2 >= formDTO.getDuration();//判断本小节是否第一次学完
        //判断是否第一次学完，不是则提交缓存到Redis
        if (!finished){
            LearningRecord record = new LearningRecord();
            record.setLessonId(formDTO.getLessonId());
            record.setSectionId(formDTO.getSectionId());
            record.setId(old.getId());
            record.setMoment(formDTO.getMoment());
            record.setFinished(old.getFinished());
            taskHandler.addLearningRecordTask(record);
            return false;
        }
        //走是第一次学完分支，更新学习记录，清理redis缓存
        // update learning_record set moment=xxx ,finished=true ,finsh_time=xxx where id =xxx
        boolean success = lambdaUpdate()
                .set(LearningRecord::getMoment, formDTO.getMoment())//保存当前学习时间
                .set(finished, LearningRecord::getFinishTime, formDTO.getCommitTime())//判断是否要保存完成时间
                .set(finished, LearningRecord::getFinished, true)//判断是否要保存已经学完
                .eq(LearningRecord::getId,old.getId())//保存的学习记录id
                .update();
        if (!success){
            throw new DbException("更新学习记录失败！");
        }
        taskHandler.cleanRecordCache(formDTO.getLessonId(), formDTO.getSectionId());

        return true;
    }

    private LearningRecord queryOldRecord(Long lessonId, Long sectionId) {
        //1.查询缓存
        LearningRecord record = taskHandler.readRecordCache(lessonId, sectionId);
        //2.如果命中直接返回
        if (record != null){
            return record;
        }
        //3.如果未命中 查询数据库 如果都没有则返回null，不加判断，缓存中都会set null 报空指针
        LearningRecord learningRecord = lambdaQuery()
                .eq(LearningRecord::getLessonId, lessonId)
                .eq(LearningRecord::getSectionId, sectionId)
                .one();
        //判断数据库是否为空
        if (learningRecord == null){
            return null;
        }
        //4.放入缓存
        taskHandler.writeRecordCache(learningRecord);
        return learningRecord;
    }

    private boolean handleExamRecord(Long userId, LearningRecordFormDTO formDTO) {
        //1.将dto转换为po
        LearningRecord record = BeanUtils.copyBean(formDTO, LearningRecord.class);
        //2.填充数据
        record.setUserId(userId);
        record.setFinished(true);
        record.setFinishTime(formDTO.getCommitTime());
        //保存学习记录
        boolean save = this.save(record);

        if (!save){
            throw new DbException("新增考试记录失败");
        }

        return true;
    }
}
