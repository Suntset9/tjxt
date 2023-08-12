package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author Sunset
 * @since 2023-08-11
 */
@Service
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

    @Override
    public void addPointsRecord(SignInMessage message, PointsRecordType type) {
        //0.校验
        if (message.getUserId() == null || message.getPoints() == null){
            return;
        }
        //用于判断本次积分是否上限
        int realPoints = message.getPoints();//代表实际可以增加得分数

        //1.判断该积分类型是否有上限 type.maxPoints是否大于0
        int maxPoints = type.getMaxPoints();//积分类型上限
        if (maxPoints>0){
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime dayStartTime = DateUtils.getDayStartTime(now);
            LocalDateTime dayEndTime = DateUtils.getDayEndTime(now);
            //2.如果有上限 查询该用户 该积分类型 今日已的积分 poinits_record 条件 userid type 今天  sum（points）
            QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
            wrapper.select("sum(points) as totalPoints");
            wrapper.eq("user_id",message.getUserId());
            wrapper.eq("type",type);
            wrapper.between("create_time",dayStartTime,dayEndTime);
            Map<String, Object> map = this.getMap(wrapper);
            int currentpoints =0;  //当前用户 该类型积分 已得积分
            if (map != null){
                BigDecimal totalPoints = (BigDecimal) map.get("totalPoints");
                currentpoints = totalPoints.intValue();
            }
            //3.判断已得积分是否超过上限
            if (currentpoints == maxPoints){
                return;//说明已得积分已经超过上限
            }
            //计算本次可以加多少分
            if (currentpoints + realPoints > maxPoints ){
                realPoints = maxPoints - currentpoints;
            }
        }
        //4.保存积分
        PointsRecord record = new PointsRecord();
        record.setPoints(realPoints);
        record.setUserId(message.getUserId());
        record.setType(type);
        save(record);
    }


    @Override
    public List<PointsStatisticsVO> queryMyTodayPoints() {
        //1.获取当前用户
        Long userId = UserContext.getUser();

        //2.查询积分表 points_record 条件:user_id 今日 按type分组
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStartTime = DateUtils.getDayStartTime(now);
        LocalDateTime dayEndTime = DateUtils.getDayEndTime(now);
        //SELECT type, SUM(points) FROM points_record
        //WHERE user_id = 2 AND create_time BETWEEN '2023-08-12 00.00.01' AND '2023-08-12 23.59.59'
        //GROUP BY type;
        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
        wrapper.select("type","sum(points) as points");//没有相同得字段 使用 points 临时存储 查出来得分数总和
        wrapper.eq("user_id",userId);
        wrapper.between("create_time",dayStartTime,dayEndTime);
        wrapper.groupBy("type");
        List<PointsRecord> list = this.list(wrapper);
        if (CollUtils.isEmpty(list)){
            return CollUtils.emptyList();
        }

        //3.封装vo返回
        List<PointsStatisticsVO> voList = new ArrayList<>();
        for (PointsRecord record : list) {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            vo.setType(record.getType().getDesc());//积分类型得中文
            vo.setPoints(record.getPoints());
            vo.setMaxPoints(record.getType().getMaxPoints());//积分类型得上限
            voList.add(vo);
        }

        return voList;
    }
}


























