import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.tianji.learning.LearningApplication;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.service.ILearningLessonService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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


}
