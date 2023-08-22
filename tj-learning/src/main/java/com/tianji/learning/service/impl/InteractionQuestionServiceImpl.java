package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.ui.context.ThemeSource;

import javax.swing.plaf.basic.BasicTreeUI;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author Sunset
 * @since 2023-08-08
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final IInteractionReplyService replyService;

    private final UserClient userClient;

    private final SearchClient searchClient;

    private final CourseClient courseClient;

    private final CatalogueClient catalogueClient;

    private final CategoryCache categoryCache;
    @Override
    public void saveQuestion(QuestionFormDTO questionTO) {
        //1.获取登录用户
        Long userId = UserContext.getUser();

        //2.dto转po
        InteractionQuestion question = BeanUtils.copyBean(questionTO, InteractionQuestion.class);
        //2.1.填入属性
        question.setUserId(userId);

        //3.保存
        this.save(question);
    }

    @Override
    public void updateQuestion(Long id, QuestionFormDTO questionDTO) {
        //1.判断是否为空
        if (StringUtils.isBlank(questionDTO.getTitle()) || StringUtils.isBlank(questionDTO.getDescription()) || questionDTO.getAnonymity() == null ){
            throw new BizIllegalException("非法参数");
        }
        //2.校验id
        InteractionQuestion question = this.getById(id);
        if (question == null){
            throw new BizIllegalException("非法参数");
        }
        //修改只能修改自己的互动问题
        Long userId = UserContext.getUser();
        //3.设置属性
        if (userId.equals(question.getId())){
            throw new BadRequestException("不能修改别人的互动问题");
        }

        question.setTitle(questionDTO.getTitle());
        question.setDescription(questionDTO.getDescription());
        question.setAnonymity(questionDTO.getAnonymity());
        //4.保存
        this.updateById(question);
    }

    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        //1.校验 参数courseId
        Long courseId = query.getCourseId();
        if (courseId == null){//课程id不能为空，小节id可不传,前端页查看全部则可以不传小节id
           throw new BadRequestException("课程id不能为空");
       }
        //2.获取当前登录用户id
        Long userId = UserContext.getUser();
        //3.分页查询互动问题interaction_question 条件：courseId onlyMine为true才会加userId 小节id不为空 hidden为false  分页查询 按提问时间倒序 问题描述不查
        Page<InteractionQuestion> page = this.lambdaQuery()
                //问题描述不查，因为前端只显示标题，查问题描述，字节大。冗余
                //.select(InteractionQuestion::getId,InteractionQuestion::getTitle,InteractionQuestion::getCourseId)//方法一：全部字段选择
                /*.select(InteractionQuestion.class, new Predicate<TableFieldInfo>() {
                    @Override
                    public boolean test(TableFieldInfo tableFieldInfo) {
                        tableFieldInfo.getProperty();//获取InteractionQuestion实体类的属性名称
                        return !tableFieldInfo.getProperty().equals("description");//指定 不查的字段 取反则代表除了这个
                    }
                })//方法二：指定不查的字段*/
                .select(InteractionQuestion.class, tableFieldInfo -> !tableFieldInfo.getProperty().equals("description"))//方法三：lambda表达式
                .eq(InteractionQuestion::getCourseId, query.getCourseId())
                .eq(query.getOnlyMine(), InteractionQuestion::getUserId, userId)
                .eq(InteractionQuestion::getHidden, false)
                .eq(query.getSectionId() != null, InteractionQuestion::getSectionId, query.getSectionId())
                //.page(query.toMpPage("create_time",false));跟下面等价，下面为封装方法
                .page(query.toMpPageDefaultSortByCreateTimeDesc());

        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }

        Set<Long> latesAnswerIds = new HashSet<>();//互动问题的，最新回答id集合
        Set<Long> userIds = new HashSet<>(); //互动问题用户和id的集合
        for (InteractionQuestion record : records) {
            if (record.getUserId() != null){
                userIds.add(record.getUserId());
            }

            if (record.getLatestAnswerId() != null){
                latesAnswerIds.add(record.getLatestAnswerId());
            }
        }
        /* //stream流写法
        Set<Long> longSet = records.stream()
                .filter(c -> c.getLatestAnswerId() != null)
                .map(InteractionQuestion::getLatestAnswerId)
                .collect(Collectors.toSet());*/


        //4.根据最新回答id集合 批量查询回答信息Interaction_reply  条件 in  id集合
        Map<Long,InteractionReply> replyMap = new HashMap<>();//最新回答的消息
        if (CollUtils.isNotEmpty(latesAnswerIds)){//集合有可能为空 判断是否为空
            List<InteractionReply> replyList = replyService.list(
                    Wrappers.<InteractionReply>lambdaQuery()
                            .in(InteractionReply::getId, latesAnswerIds)
                            .eq(InteractionReply::getHidden, false)//不查询被隐藏的
            );
            for (InteractionReply reply : replyList) {
                if (!reply.getAnonymity()){//判断是否匿名
                    userIds.add(reply.getUserId());//将最新回答的用户id 存入userId
                }
                 replyMap.put(reply.getId(),reply);
            }
            //Map<Long, InteractionReply> replyMap1 = replyList.stream().collect(Collectors.toMap(InteractionReply::getId, c -> c));//stream流写法
        }

        //5.远程调用用户服务  获取用户登录信息 批量
        List<UserDTO> userDTOS = userClient.queryUserByIds(userIds);
        Map<Long, UserDTO> userDTOMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));

        //6.封装Vo返回
        List<QuestionVO> voList = new ArrayList<>();
        for (InteractionQuestion record : records) {//循环查询到的互动问题回答或评论
            QuestionVO vo = BeanUtils.copyBean(record, QuestionVO.class);//拷贝属性
            if (!vo.getAnonymity()){//判断不是匿名
                UserDTO userDTO = userDTOMap.get(record.getUserId());//通过远程调用用户服务得到信息根据提问学员id获取昵称和头像
                if (!record.getAnonymity()){//不是匿名则设置昵称 头像
                    vo.setUserName(userDTO.getUsername());//提问者昵称
                    vo.setUserIcon(userDTO.getIcon());//提问者头像
                }
            }

            InteractionReply reply = replyMap.get(record.getLatestAnswerId());//获取最新回答的id值
            if (reply != null){//
                if (!reply.getAnonymity()){//最新回答 如果是 非匿名 才设置 最新回答这的昵称
                    UserDTO userDTO = userDTOMap.get(reply.getUserId());//通过远程调用用户服务得到信息根据最新回答id获取最新作者名称
                    if (userDTO != null){
                        vo.setLatestReplyUser(userDTO.getName());//最新作者的名称
                    }
                }
                vo.setLatestReplyContent(reply.getContent());//最新的回答信息
            }
            voList.add(vo);
        }
        return PageDTO.of(page,voList);
    }


    @Override
    public QuestionVO queryQuestionById(Long id) {
        //1.校验
        if (id ==null){
            throw new BadRequestException("非法参数");
        }
        //2.查询互动问题 按主键查询
        InteractionQuestion question = this.getById(id);
        if (question == null){
            throw new BizIllegalException("问题不存在");
        }

        //3.如果该问题管理员设置隐藏 则返回空
        if (question.getHidden()){
            return null;
        }
        //4.封装vo返回
        QuestionVO vo = BeanUtils.copyBean(question, QuestionVO.class);

        //5.如果用户是匿名提问，不用查询提问者昵称和头像
        if (!question.getAnonymity()){
            UserDTO userDTO = userClient.queryUserById(question.getUserId());//调用接口查询用户头像
            if (userDTO!=null){
                vo.setUserName(userDTO.getName());
                vo.setUserIcon(userDTO.getIcon());
            }
        }
        return vo;
    }


    @Override
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query) {
        //0.根据课程名称去es中查询课程id
        String courseName = query.getCourseName();
        List<Long> cids = null;
        if (StringUtils.isNotBlank(courseName)){
            cids = searchClient.queryCoursesIdByName(courseName);//通过feign远程调用搜索服务，从es中搜索该关键字对应的课程id
            if (cids == null){//查询为空则返回空的分页对象
                return PageDTO.empty(0L,0L);
            }
        }

        //1.查询互动问题表  条件前端传条件了就添加条件 分页 排序按提问时间倒序
        Page<InteractionQuestion> page = this.lambdaQuery()
                .in(CollUtils.isNotEmpty(cids) ,InteractionQuestion::getCourseId, cids)
                .eq(query.getStatus() != null, InteractionQuestion::getStatus, query.getStatus())
                .between(query.getBeginTime() != null && query.getEndTime() != null,
                        InteractionQuestion::getCreateTime, query.getBeginTime(), query.getEndTime())
                //.page(query.toMpPage("create_time",false));
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if (CollUtils.isEmpty(records)){
            return PageDTO.empty(0L,0L);
        }

        Set<Long> userids = new HashSet<>();//用户id集合
        Set<Long> courseids = new HashSet<>();//课程id集合
        Set<Long> chapterAndSectionIds = new HashSet<>();//章和节id集合
        for (InteractionQuestion record : records) {
            userids.add(record.getUserId());
            courseids.add(record.getCourseId());
            chapterAndSectionIds.add(record.getChapterId());//章id
            chapterAndSectionIds.add(record.getSectionId());//节id
        }

        //2.远程调用用户服务 获取用户信息
        List<UserDTO> userDTOS = userClient.queryUserByIds(userids);
        if (CollUtils.isEmpty(userDTOS)){
            throw new BizIllegalException("用户不存在");
        }
        Map<Long, UserDTO> userDTOMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        //3.远程调用课程服务 获取课程信息
        List<CourseSimpleInfoDTO> infoList = courseClient.getSimpleInfoList(courseids);
        if (CollUtils.isEmpty(infoList)){
            throw new BizIllegalException("课程不存在");
        }
        Map<Long, CourseSimpleInfoDTO> cinfoMap = infoList.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        //4.远程调用课程服务 获取章节信息
        List<CataSimpleInfoDTO> cataSimpleInfoDTOS = catalogueClient.batchQueryCatalogue(chapterAndSectionIds);
        if (cataSimpleInfoDTOS == null){
            throw new BizIllegalException("章节信息不存在");
        }
        Map<Long, String> cataInfoDTO = cataSimpleInfoDTOS.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId, c -> c.getName()));

        //6.封装vo返回
        List<QuestionAdminVO> voList = new ArrayList<>();
        for (InteractionQuestion record : records) {
            QuestionAdminVO vo = BeanUtils.copyBean(record, QuestionAdminVO.class);
            UserDTO userDTO = userDTOMap.get (record.getUserId());
            if (userDTO != null){//用户信息判断
                vo.setUserName(userDTO.getName());//封装提问者名称
            }
            CourseSimpleInfoDTO cinfoDTO = cinfoMap.get(record.getCourseId());
            if (cinfoDTO != null){//判断课程信息是否存在
                vo.setCourseName(cinfoDTO.getName());//封装课程名称
                List<Long> categoryIds = cinfoDTO.getCategoryIds();//封装了方法，拼接了三级分类名称
                //5.获取一二三级分类信息
                String categoryNameList = categoryCache.getCategoryNames(categoryIds);
                vo.setCategoryName(categoryNameList);//封装三级分类名称
            }
            vo.setChapterName(cataInfoDTO.get(record.getChapterId()));//封装章名称
            vo.setSectionName(cataInfoDTO.get(record.getSectionId()));//封装节名称
            voList.add(vo);
        }

        return PageDTO.of(page,voList);
    }
}





















