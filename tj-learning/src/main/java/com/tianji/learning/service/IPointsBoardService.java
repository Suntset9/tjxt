package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoard;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardVO;

import java.util.List;

/**
 * <p>
 * 学霸天梯榜 服务类
 * </p>
 *
 * @author Sunset
 * @since 2023-08-11
 */
public interface IPointsBoardService extends IService<PointsBoard> {

    PointsBoardVO queryPointsBoardList(PointsBoardQuery query);


    /**
     * 查询当前赛季  redis zset
     * @param key boards:202308
     * @param pageNo 页码
     * @param pageSize 条数
     * @return
     */
    public List<PointsBoard> queryCurrentBoard(String key, Integer pageNo, Integer pageSize);
}
