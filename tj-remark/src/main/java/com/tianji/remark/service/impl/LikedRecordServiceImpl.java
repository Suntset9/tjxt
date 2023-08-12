//package com.tianji.remark.service.impl;
//
//import com.tianji.api.dto.remark.LikedTimesDTO;
//import com.tianji.api.dto.trade.OrderBasicDTO;
//import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
//import com.tianji.common.constants.MqConstants;
//import com.tianji.common.utils.StringUtils;
//import com.tianji.common.utils.UserContext;
//import com.tianji.remark.domain.dto.LikeRecordFormDTO;
//import com.tianji.remark.domain.po.LikedRecord;
//import com.tianji.remark.mapper.LikedRecordMapper;
//import com.tianji.remark.service.ILikedRecordService;
//import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//import java.util.Set;
//import java.util.stream.Collectors;
//
//import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
//import static com.tianji.common.constants.MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE;
//
///**
// * <p>
// * 点赞记录表 服务实现类
// * </p>
// *
// * @author Sunset
// * @since 2023-08-10
// */
//@Service
//@Slf4j
//@RequiredArgsConstructor
//public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {
//
//    private final RabbitMqHelper rabbitMqHelper;
//
//    @Override
//    public void addLikeRecord(LikeRecordFormDTO dto) {
//        //1.获取登录用户id
//        Long userId = UserContext.getUser();
//        //2.判断是否点过赞 dto.like 为 true 则是点赞
//        //boolean flag = true;
//        //if (dto.getLiked()){
//        //    //2.1 点赞逻辑
//        //    flag = like(userId,dto);
//        //}else {
//        //    //2.2 取消赞逻辑
//        //    flag = unLike(userId,dto);
//        //}
//        boolean flag = dto.getLiked() ? like(userId,dto) : unLike(userId,dto);
//        if (!flag){
//            return;
//        }
//        //3.统计该业务id的总点赞数
//        Integer totalLikesNum = this.lambdaQuery()
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .count();
//
//        //4。发送消息到mq
//        String rotingKey = StringUtils.format(LIKED_TIMES_KEY_TEMPLATE, dto.getBizType());
//        //LikedTimesDTO msg = new LikedTimesDTO();//使用api接口下的点赞统计类
//        //msg.setBizId(dto.getBizId());
//        //msg.setLikedTimes(totalLikesNum);
//        LikedTimesDTO msg = LikedTimesDTO.of(dto.getBizId(), totalLikesNum);
//        log.debug("发送消息{}",msg);
//        rabbitMqHelper.send(
//                LIKE_RECORD_EXCHANGE,
//                rotingKey,
//                msg
//        );
//
//    }
//    //取消赞
//    private boolean unLike(Long userId, LikeRecordFormDTO dto) {
//        LikedRecord record = lambdaQuery()
//                .eq(LikedRecord::getUserId, userId)
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .one();
//        if (record == null){
//            return false;//说明之前没有点过赞
//        }
//        //删除点赞记录
//        boolean result = removeById(record.getId());
//        return result;
//    }
//    //点赞
//    private boolean like(Long userId, LikeRecordFormDTO dto) {
//        LikedRecord record = lambdaQuery()
//                .eq(LikedRecord::getUserId, userId)
//                .eq(LikedRecord::getBizId, dto.getBizId())
//                .one();
//        if (record != null){
//            return false;//说明之前点过赞
//        }
//
//        LikedRecord like = new LikedRecord();
//        like.setBizId(dto.getBizId());
//        like.setBizType(dto.getBizType());
//        like.setUserId(userId);
//        boolean result = save(like);
//
//        return result;
//    }
//
//
//    @Override
//    public Set<Long> getLikesStatusByBizIds(List<Long> bizIds) {
//        // 1.获取当前用户id
//        Long userId = UserContext.getUser();
//        //2.查询点赞状态
//        List<LikedRecord> list = lambdaQuery()
//                .in(LikedRecord::getBizId, bizIds)
//                .eq(LikedRecord::getUserId, userId)
//                .list();
//        //3.将查询的转为集合返回
//        Set<Long> likeBizIds = list.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());
//
//        return likeBizIds;
//    }
//}
