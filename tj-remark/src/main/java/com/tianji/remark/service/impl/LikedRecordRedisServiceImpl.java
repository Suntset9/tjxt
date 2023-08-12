package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.remark.LikedTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author Sunset
 * @since 2023-08-10
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LikedRecordRedisServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper rabbitMqHelper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void addLikeRecord(LikeRecordFormDTO dto) {
        //1.获取登录用户id
        Long userId = UserContext.getUser();
        //2.判断是否点过赞 dto.like 为 true 则是点赞
        /*
        boolean flag = true;
        if (dto.getLiked()){
            //2.1 点赞逻辑
            flag = like(userId,dto);
        }else {
            //2.2 取消赞逻辑
            flag = unLike(userId,dto);
        }*/
        boolean flag = dto.getLiked() ? like(userId,dto) : unLike(userId,dto);
        if (!flag){
            return;
        }
        /*//3.统计该业务id的总点赞数
        Integer totalLikesNum = this.lambdaQuery()
                .eq(LikedRecord::getBizId, dto.getBizId())
                .count();*/
        // 3.基于redis统计 业务id的总点赞量
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        Long totallikeTimes = redisTemplate.opsForSet().size(key);
        if (totallikeTimes == null){
            return;
        }

        //4.采用zset结构缓存点赞的总数   likes:times:type:QA   likes:times:type:NOTE
        String bizTypeTotalLikeKey = RedisConstants.LIKE_COUNT_KEY_PREFIX + dto.getBizType();
        redisTemplate.opsForZSet()
                .add(bizTypeTotalLikeKey,dto.getBizId().toString(),totallikeTimes);//key value score


        /*//4。发送消息到mq
        String rotingKey = StringUtils.format(LIKED_TIMES_KEY_TEMPLATE, dto.getBizType());
        //LikedTimesDTO msg = new LikedTimesDTO();//使用api接口下的点赞统计类
        //msg.setBizId(dto.getBizId());
        //msg.setLikedTimes(totalLikesNum);
        LikedTimesDTO msg = LikedTimesDTO.of(dto.getBizId(), totalLikesNum);
        log.debug("发送消息{}",msg);
        rabbitMqHelper.send(
                LIKE_RECORD_EXCHANGE,
                rotingKey,
                msg
        );*/

    }
    //取消赞
    private boolean unLike(Long userId, LikeRecordFormDTO dto) {
        /*LikedRecord record = lambdaQuery()
                .eq(LikedRecord::getUserId, userId)
                .eq(LikedRecord::getBizId, dto.getBizId())
                .one();
        if (record == null){
            return false;//说明之前没有点过赞
        }
        //删除点赞记录
        boolean result = removeById(record.getId());
        return result;*/
        //获取key
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        //执行SREM命令
        Long result = redisTemplate.opsForSet().remove(key, userId.toString());
        return result !=null && result >0;
    }
    //点赞
    private boolean like(Long userId, LikeRecordFormDTO dto) {
        /*LikedRecord record = lambdaQuery()
                .eq(LikedRecord::getUserId, userId)
                .eq(LikedRecord::getBizId, dto.getBizId())
                .one();
        if (record != null){
            return false;//说明之前点过赞
        }

        LikedRecord like = new LikedRecord();
        like.setBizId(dto.getBizId());
        like.setBizType(dto.getBizType());
        like.setUserId(userId);
        boolean result = save(like);

        return result;*/
        //获取key
        String key  = RedisConstants.LIKE_BIZ_KEY_PREFIX+dto.getBizId();
        //执行SADD命令 往Redis的set接口添加点赞记录
        //redisTemplate.boundSetOps(key).add(userId.toString());
        Long result = redisTemplate.opsForSet().add(key, userId.toString());

        return result != null && result > 0;
    }


    @Override
    public Set<Long> getLikesStatusByBizIds(List<Long> bizIds) {
/*        // 1.获取当前用户id
        Long userId = UserContext.getUser();
        //2.查询点赞状态
        List<LikedRecord> list = lambdaQuery()
                .in(LikedRecord::getBizId, bizIds)
                .eq(LikedRecord::getUserId, userId)
                .list();
        //3.将查询的转为集合返回
        Set<Long> likeBizIds = list.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());

        return likeBizIds;*/
/*        //1.获取用户
        Long userId = UserContext.getUser();
        if (CollUtils.isEmpty(bizIds)){
            return CollUtils.emptySet();
        }

        //2.循环bizIds
        Set<Long> likedBizIds = new HashSet<>();
        for (Long bizId : bizIds) {
            Boolean member = redisTemplate.opsForSet().isMember(RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId, userId.toString());
            if (member){
                likedBizIds.add(bizId);
            }

        }
        return likedBizIds;*/
// 1.获取登录用户id
        Long userId = UserContext.getUser();
        // 2.查询点赞状态
        List<Object> objects = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            for (Long bizId : bizIds) {
                String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId;
                src.sIsMember(key, userId.toString());
            }
            return null;
        });
        // 3.返回结果
        return IntStream.range(0, objects.size()) // 创建从0到集合size的流
                .filter(i -> (boolean) objects.get(i)) // 遍历每个元素，保留结果为true的角标i
                .mapToObj(bizIds::get)// 用角标i取bizIds中的对应数据，就是点赞过的id
                .collect(Collectors.toSet());// 收集
    }



    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {
        //1.拼接key
        String bizTypeTotalLikeKey = RedisConstants.LIKE_COUNT_KEY_PREFIX + bizType;

        List<LikedTimesDTO> list = new ArrayList<>();
        //2.从redis的zset结构中取maxBizSize 的 业务点赞信息  popmin
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().popMin(bizTypeTotalLikeKey, maxBizSize);//一次取三十条
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String bizId = typedTuple.getValue();//获取value,点赞的业务id
            Double likedTimes = typedTuple.getScore();//获取分数,点赞数
            if (StringUtils.isBlank(bizId) && likedTimes == null){
                continue;
            }
            //3.封装LikeTimesDto 消息数据
            LikedTimesDTO msg = LikedTimesDTO.of(Long.valueOf(bizId), likedTimes.intValue());
            list.add(msg);
        }
        //4.发送消息到mq
        if (CollUtils.isNotEmpty(list)){
            log.debug("批量发送消息{}",list);
            String routingKey  = StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE,bizType);
            rabbitMqHelper.send(
                    MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                    routingKey,
                    list);
        }
    }
}
