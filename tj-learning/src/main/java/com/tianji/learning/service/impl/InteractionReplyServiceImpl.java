package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.remark.RemarkClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import javassist.runtime.DotClass;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_LIKED_TIME;
import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_UPDATE_TIME;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author Sunset
 * @since 2023-08-08
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final InteractionQuestionMapper questionMapper;
    private final UserClient userClient;

    private final RemarkClient remarkClient;

    @Override
    public void saveReply(ReplyDTO dto) {
        log.info("接收到参数{}",dto);
        //获取当前登录用户
        Long userId = UserContext.getUser();
        //1.保存回答或评论 interaction_reply
        InteractionReply reply = BeanUtils.copyBean(dto, InteractionReply.class);
        reply.setUserId(userId);
        this.save(reply);
        //获取问题实体
        InteractionQuestion question = questionMapper.selectById(dto.getQuestionId());
        //2.判断是否回答 dto.answerId 为空则是回答
        if (dto.getAnswerId() != null){
            //不是回答则累加回答的评论数量
            InteractionReply answerInfo = this.getById(dto.getAnswerId()); //回答
            answerInfo.setReplyTimes(answerInfo.getReplyTimes()+1);//评论次数累加
            this.updateById(answerInfo);
        }else {
            //3.如果是回答就 修改最近一次回答的id 同时累加问题表的回答次数
            question.setLatestAnswerId(reply.getId());
            question.setAnswerTimes(question.getAnswerTimes()+1);
        }
        if (dto.getIsStudent()){
            //4.判断是否学生提交 是则修改问题状态为未查看 dti.isStudent 为 true 则代表学生提交 如果是则将表中该问题的status字段改为未查看
            question.setStatus(QuestionStatus.UN_CHECK);
        }
        questionMapper.updateById(question);



        /* 讲义写法
        // 1.获取登录用户
        Long userId = UserContext.getUser();
        // 2.新增回答
        InteractionReply reply = BeanUtils.toBean(replyDTO, InteractionReply.class);
        reply.setUserId(userId);
        save(reply);
        // 3.累加评论数或者累加回答数
        // 3.1.判断当前回复的类型是否是回答
        boolean isAnswer = replyDTO.getAnswerId() == null;
        if (!isAnswer) {
            // 3.2.是评论，则需要更新上级回答的评论数量
            lambdaUpdate()
                    .setSql("reply_times = reply_times + 1")
                    .eq(InteractionReply::getId, replyDTO.getAnswerId())
                    .update();
        }
        // 3.3.尝试更新问题表中的状态、 最近一次回答、回答数量
        questionService.lambdaUpdate()
                .set(isAnswer, InteractionQuestion::getLatestAnswerId, reply.getAnswerId())
                .setSql(isAnswer, "answer_times = answer_times + 1")
                .set(replyDTO.getIsStudent(), InteractionQuestion::getStatus, QuestionStatus.UN_CHECK.getValue())
                .eq(InteractionQuestion::getId, replyDTO.getQuestionId())
                .update();
        */

    }

    @Override
    public PageDTO<ReplyVO> queryReplyVoPage(ReplyPageQuery query) {
        //1.校验questionId和answerrId是否都为空
        if (query.getQuestionId()==null && query.getAnswerId() == null ){
            throw new BadRequestException("问题id和回答id不能都为空");
        }
        //2.分页查询Interaction_repl表
        Page<InteractionReply> page = this.lambdaQuery()
                //如果传问题id则拼接问题id
                .eq(query.getQuestionId() != null, InteractionReply::getQuestionId, query.getQuestionId())
                //如果问题id没传，则查询answer_id为0的数据，也就是回答
                .eq(InteractionReply::getAnswerId, query.getAnswerId() == null ? 0L : query.getAnswerId())
                .eq(InteractionReply::getHidden, false)//隐藏问题则不查
                .page(query.toMpPage(//先根据点赞排序，点赞相同 则按照创建时间排序
                        new OrderItem(DATA_FIELD_NAME_LIKED_TIME, false),
                        new OrderItem(DATA_FIELD_NAME_UPDATE_TIME, false)));
        List<InteractionReply> records = page.getRecords();
        if (CollUtils.isEmpty(records)){
            return PageDTO.empty(0L,0L);
        }
        //3.补全其他数据
        Set<Long> uids = new HashSet<>();//userid
        Set<Long> targetReplIds = new HashSet<>();//回复的目标id
        Set<Long> answerIds = new HashSet<>();//互动问答的id
        for (InteractionReply record : records) {
            if (!record.getAnonymity()){
                uids.add(record.getUserId());
                uids.add(record.getTargetUserId());
            }
            answerIds.add(record.getId());
            if (record.getTargetReplyId()!=null && record.getTargetReplyId()>0){
                targetReplIds.add(record.getTargetReplyId());
            }
        }
        //4.查询目标回复， 如果目标恢复不是匿名 则需要查询出目标恢复的用户信息
        if (targetReplIds.size()>0){
            List<InteractionReply> targetReplis = this.listByIds(targetReplIds);
            Set<Long> targeUserIds = targetReplis.stream()
                    .filter(Predicate.not(InteractionReply::getAnonymity))//不是匿名
                    .map(InteractionReply::getUserId)//返回用户id
                    .collect(Collectors.toSet());
            uids.addAll(targeUserIds);
        }
        log.info("接收到补全数据{}",uids);

        List<UserDTO> userDTOList = userClient.queryUserByIds(uids);
        Map<Long, UserDTO> userDTOMap = new HashMap<>();
        if (userDTOList != null){
            userDTOMap = userDTOList.stream().collect(Collectors.toMap(UserDTO::getId, c -> c));
        }

        //4.1 查询用户点赞状态
        Set<Long> bizLiked = remarkClient.isBizLiked(answerIds);

        //封装vo返回
        List<ReplyVO> voList = new ArrayList<>();
        for (InteractionReply record : records) {
            ReplyVO vo = BeanUtils.copyBean(record, ReplyVO.class);
            if (!record.getAnonymity()){
                UserDTO userDTO = userDTOMap.get(record.getUserId());
                if (userDTO!=null){
                    vo.setUserName(userDTO.getName());
                    vo.setUserIcon(userDTO.getIcon());
                    vo.setUserType(userDTO.getType());
                }
            }
            UserDTO userDTO = userDTOMap.get(record.getTargetUserId());
            vo.setTargetUserName(userDTO.getName());

            vo.setLikedTimes(record.getLikedTimes());
            //点赞状态
            vo.setLiked(bizLiked.contains(record.getId()));

            voList.add(vo);
        }
        return PageDTO.of(page,voList);
    }
}
