package com.tianji.learning.service.impl;

import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author Sunset
 * @since 2023-08-11
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {

    private final StringRedisTemplate redisTemplate;

    private final UserClient userClient;
    @Override
    public PointsBoardVO queryPointsBoardList(PointsBoardQuery query) {
        //1.获取当前登录用户id
        Long userId = UserContext.getUser();
        //2.判断是查当前赛季还是历史赛季  query.season 赛季id 为null或为0则代表查询当前赛季
        boolean isCurrent = query.getSeason() == null || query.getSeason() == 0;//该字段为true则查当前赛季 redis
        LocalDate now = LocalDate.now();
        String format = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + format;//redis查询的key
        Long season = query.getSeason();//历史赛季id\

        //3.查询我的积分和排名（我的单独数据）  根据  query.season  判断是查reids还是db

        PointsBoard board = isCurrent ? queryMycurrentBoard(key) : queryMyHistoryBoard(season);
        /*
        PointsBoard board = null;
        if (isCurrent){
            board = queryMycurrentBoard(key);
        }else {
            board =  queryMyHistoryBoard(season);
        }
        */

        //4.分页查询赛季列表（所有排名）  根据query.season  判断是查redis还是db
        List<PointsBoard> list =  isCurrent ? queryCurrentBoard(key,query.getPageNo(),query.getPageSize()) : queryHistoryBoard(query);

        //List<PointsBoard> list = new ArrayList<>();
        //if (isCurrent){
        //    list = queryCurrentBoard(key,query.getPageNo(),query.getPageSize());
        //}else {
        //    list =  queryHistoryBoard(query);
        //}

        //5.封装用户id集合 远程调用用户服务 获取用户信息  转mp
        Set<Long> uids = list.stream().map(PointsBoard::getUserId).collect(Collectors.toSet());
        List<UserDTO> userDTOS = userClient.queryUserByIds(uids);
        if (CollUtils.isEmpty(userDTOS)){
            throw new BizIllegalException("用户不存在");
        }
        //转map key为用户id  value为用户名称
        Map<Long, String> userDtoMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, c -> c.getName()));


        //6.封装vo返回
        List<PointsBoardItemVO> voList = new ArrayList<>();
        PointsBoardVO vo = new PointsBoardVO();  //
        vo.setRank(board.getRank());//当前用户的排名
        vo.setPoints(board.getPoints());//当前用户的积分
        for (PointsBoard pointsBoard : list) {
            PointsBoardItemVO itemVO = new PointsBoardItemVO();// List<PointsBoardItemVO> boardList;"前100名上榜人信息")
            itemVO.setName(userDtoMap.get(pointsBoard.getUserId()));//远程调用用户服务 根据id 获取所有用户名
            itemVO.setPoints(pointsBoard.getPoints());
            itemVO.setRank(pointsBoard.getRank());
            voList.add(itemVO);

        }
        vo.setBoardList(voList);

        return vo;
    }

    /**
     * 查询当前赛季  redis zset
     * @param key boards:202308
     * @param pageNo 页码
     * @param pageSize 条数
     * @return
     */
    public List<PointsBoard> queryCurrentBoard(String key, Integer pageNo, Integer pageSize) {
        //1.计算start和end   分页值
        int start = (pageNo -1) * pageSize;
        int end = start + (pageSize - 1);
        //2.利用zrevrange  会按分数倒序 分页查询  reverseRangeWithScores
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        if (CollUtils.isEmpty(typedTuples)){
            return CollUtils.emptyList();
        }
        //3.封装结果返回
        List<PointsBoard> lsit = new ArrayList<>();
        int rank = start + 1;//前端查十条 分页后 十开始  页码 + 1  为排名
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            Double score = typedTuple.getScore();//总积分值
            String value = typedTuple.getValue();//用户id
            if (StringUtils.isBlank(value) || score == null){
                continue;//参数非法 重新循环
            }
            PointsBoard board = new PointsBoard();
            board.setUserId(Long.valueOf(value));//用户id
            board.setPoints(score.intValue());//积分
            board.setRank(rank++);//排名

            lsit.add(board);
        }
        return lsit;
    }

    //查询历史赛季列表 从db查
    private List<PointsBoard> queryHistoryBoard(PointsBoardQuery query) {
        //TODO 查询历史赛季
        return null;
    }

    //查询历史赛季 我的积分和排名 db
    private PointsBoard queryMyHistoryBoard(Long season) {
        // TODO 查询历史赛季积分排名
        return null;
    }


    //查询当前赛季 我的积分和排名 redis
    private PointsBoard queryMycurrentBoard(String key) {
        //获取当前用户
        Long userId = UserContext.getUser();
        //获取分值
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        //获取排名
        Long rank = redisTemplate.opsForZSet().reverseRank(key, userId.toString());
        PointsBoard board = new PointsBoard();
        board.setRank(rank == null ? 0 : rank.intValue()+1);//从 0 开始 +1 为排名
        board.setPoints(score== null ? 0 : score.intValue());

        return board;
    }


}
