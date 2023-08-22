import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.tianji.learning.LearningApplication;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest(classes = LearningApplication.class)
public class testone {

    @Autowired
    ILearningLessonService  lessonService;



    @Test
    void test(){
        LearningLesson lesson = new LearningLesson();
        lesson.setCourseId(2L);
        //1.统计当前课程的学习人数
        Integer count = lessonService.lambdaQuery()
                .eq(LearningLesson::getCourseId, lesson.getCourseId())
                .count();
        System.out.println(count);
    }
    @Autowired
    LearningRecordMapper recordMapper;


    @Test
    void test1(){
        //7.查询学习记录表learning_record 本周 当前用户下 每一门课下 已学习的小节数量
        //SELECT lesson_id,count(*) FROM learning_record
        //WHERE user_id = 2
        //AND finished = 1
        //AND finish_time BETWEEN '2023-08-06 00:00:01' AND '2023-08-12 23:59:59'
        //GROUP BY lesson_id;

        QueryWrapper<LearningRecord> rWrapper = new QueryWrapper<>();
        rWrapper.select("lesson_id as lessonId","count(*) as userId")//实体类中没有属性结束count值，我们使用临时属性userid接收
                .eq("user_id", 2)
                .eq("finished", true)
                .between("finish_time", "2023-08-06 00:00:01", "2023-08-12 23:59:59")
                .groupBy("lesson_id");
        List<LearningRecord> recordList = recordMapper.selectList(rWrapper);
        System.out.println(recordList);
    }


    @Test
    void lessonStatusCheck() {
        //1.查询课程 未过期 所有课程不区分用户
        List<LearningLesson> list = lessonService.lambdaQuery()
                .ne(LearningLesson::getStatus, LessonStatus.EXPIRED)
                .list();
        List<LearningLesson> list1 = lessonService.list(
                Wrappers.<LearningLesson>lambdaQuery()
                        .ne(LearningLesson::getStatus, LessonStatus.EXPIRED));

        System.out.println(list);

        System.out.println(list1);
    }
}
