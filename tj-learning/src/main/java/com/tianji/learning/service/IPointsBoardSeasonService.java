package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoardSeason;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Sunset
 * @since 2023-08-11
 */
public interface IPointsBoardSeasonService extends IService<PointsBoardSeason> {

    void createPointsBoardTableBySeason(Integer id);
}
