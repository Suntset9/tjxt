package com.tianji.learning.task;

import com.tianji.common.utils.CollUtils;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.tianji.learning.constants.LearningConstants.POINTS_BOARD_TABLE_PREFIX;

@Component
@Slf4j
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {

    private final IPointsBoardSeasonService pointsBoardSeasonService;

    private final IPointsBoardService pointsBoardService;

    private final StringRedisTemplate  redisTemplate;

    //@Scheduled(cron = "0 0 3 1 * ?")//每个月一号凌晨3点运行
    //@Scheduled(cron = "0 35 00 13 8 ?")//每个月一号凌晨3点运行   单机版  开启多个微服务会同时启动这个定时任务
    @XxlJob("createTableJob")
    public void createPointsBoardTableOfLastSeason(){
        log.debug("创建上赛季榜单表任务执行了");

        //1.获取上个月当前时间点
        LocalDate time = LocalDate.now().minusMonths(1);

        //2.查询赛季表获取赛季id  条件 beign_time <= time  and end_time >= time
        PointsBoardSeason one = pointsBoardSeasonService
                .lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)
                .ge(PointsBoardSeason::getEndTime, time)
                .one();
        log.debug("上赛季信息   {} ", one);
        if (one == null){
            return;
        }

        //3.创建上赛季榜单 points_board_7
        pointsBoardSeasonService.createPointsBoardTableBySeason(one.getId());

    }

    //持久化上赛季（上个月的 ） 排行榜数据 到 db中
    @XxlJob("savePointsBoard2DB")//任务名需要和 xxljob控制太 任务的jobhandler保持一致
    public void savePointsBoard2DB(){
        //1.获取上个月 当前时间点
        LocalDate time = LocalDate.now().minusMonths(1);

        //2.查询赛季表 points_board_season 获取上赛季信息
        //select * from points_board_season where begin_time <= '2023-05-01' and end_time >= '2023-05-01'
        PointsBoardSeason one = pointsBoardSeasonService.lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)
                .ge(PointsBoardSeason::getEndTime, time)
                .one();
        log.debug("上赛季信息 {}",one);

        //3.计算动态表名  并存入thredlocal
        String tableName = POINTS_BOARD_TABLE_PREFIX + one.getId();
        log.debug("动态表名为  {}",tableName);
        TableInfoContext.setInfo(tableName);//存入thredlocal
        //4.分页获取redis上赛季积分排行榜数据
        //拼接key
        String format = time.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + format;//redis查询的key
        //设置分片轮询编号 数量
        int shardIndex = XxlJobHelper.getShardIndex(); //执行器编号 从0开始
        int shardTotal = XxlJobHelper.getShardTotal(); //执行器数量
        int pageNo = shardIndex + 1; // 起始页，就是分片序号+1
        int pageSize = 5;
        while (true){
            log.debug("处理  第{}页数据",pageNo);
            List<PointsBoard> boardList = pointsBoardService.queryCurrentBoard(key, pageNo, pageSize);
            //log.debug("redis 数据{}",boardList);
            if (CollUtils.isEmpty(boardList)){
                break;//跳出循环
            }
            //5.持久化到db相应的赛季表中
            for (PointsBoard board : boardList) {
                board.setId(board.getRank().longValue());//历史排行榜中的id 就代表了排名
                board.setRank(null);
            }
            //持久化操作
            pointsBoardService.saveBatch(boardList);
            //翻页，跳过n个页  n就是分片数量
            pageNo+=shardTotal;
        }

        //6.清空threadlocal 中数据
        TableInfoContext.remove();

    }

    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis(){
        log.debug("清理Redis的历史榜单数据开始了");
        // 1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2.计算key
        String format = time.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + format;//redis查询的key
        // 3.删除
        redisTemplate.unlink(key);
    }

}
